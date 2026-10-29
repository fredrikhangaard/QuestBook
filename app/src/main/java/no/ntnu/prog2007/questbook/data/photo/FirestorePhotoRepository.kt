package no.ntnu.prog2007.questbook.data.photo

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.firebase.await

/**
 * [PhotoRepository] that stores each photo as Base64 text in the Firestore collection
 * `photos`, one document per photo.
 *
 * Document fields: `questId`, `userId`, `caption`, `imageBase64`, `sizeBytes` and `createdAt`.
 * Photos have their own collection, so loading a list of quests or results does not
 * download any image data until a photo is actually shown.
 *
 * @param firestore the Firestore instance to use. Can be replaced in tests.
 * @param currentUserId returns the id of the signed-in user, who is saved as the photo's owner.
 */
class FirestorePhotoRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val currentUserId: () -> String? = { AppServices.authRepository.currentUserId }
) : PhotoRepository {

    private val photos get() = firestore.collection(COLLECTION)

    override fun savePhoto(questId: String, jpegBytes: ByteArray, caption: String): String {
        val document = photos.document()
        val data = mapOf(
            FIELD_QUEST_ID to questId,
            FIELD_USER_ID to currentUserId().orEmpty(),
            FIELD_CAPTION to caption,
            FIELD_IMAGE to PhotoCodec.encode(jpegBytes),
            FIELD_SIZE to jpegBytes.size,
            FIELD_CREATED_AT to FieldValue.serverTimestamp()
        )
        // Not awaited on purpose: Firestore saves the document locally at once and
        // uploads it when the device is online. Awaiting would hang while offline.
        document.set(data).addOnFailureListener { error ->
            Log.w(TAG, "Could not upload photo ${document.id}", error)
        }
        return document.id
    }

    override suspend fun getPhoto(photoId: String): StoredPhoto? {
        val snapshot = photos.document(photoId).get().await()
        if (!snapshot.exists()) return null
        val bytes = snapshot.getString(FIELD_IMAGE)?.let(PhotoCodec::decode) ?: return null
        return StoredPhoto(
            id = snapshot.id,
            questId = snapshot.getString(FIELD_QUEST_ID).orEmpty(),
            caption = snapshot.getString(FIELD_CAPTION).orEmpty(),
            jpegBytes = bytes
        )
    }

    private companion object {
        const val TAG = "FirestorePhotoRepo"
        const val COLLECTION = "photos"
        const val FIELD_QUEST_ID = "questId"
        const val FIELD_USER_ID = "userId"
        const val FIELD_CAPTION = "caption"
        const val FIELD_IMAGE = "imageBase64"
        const val FIELD_SIZE = "sizeBytes"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
