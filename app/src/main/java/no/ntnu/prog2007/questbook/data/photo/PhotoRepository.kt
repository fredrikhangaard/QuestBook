package no.ntnu.prog2007.questbook.data.photo

/**
 * A quest photo as it is stored in the database.
 *
 * Not a data class: [jpegBytes] is an array, and arrays are compared by reference.
 */
class StoredPhoto(
    val id: String,
    val questId: String,
    val caption: String,
    val jpegBytes: ByteArray
)

/**
 * Saves and loads quest photos.
 *
 * ViewModels only depend on this interface, so they don't need to know whether
 * photos are stored in Firestore or somewhere else (Repository pattern).
 */
interface PhotoRepository {

    /**
     * Saves a compressed JPEG photo for the quest [questId].
     *
     * The photo is written to the local cache right away and uploaded when the
     * device is online, so this also works without an internet connection.
     *
     * @return the id of the new photo. Store it as `photoReference` on the quest result.
     */
    fun savePhoto(questId: String, jpegBytes: ByteArray, caption: String): String

    /**
     * Loads the photo with the id [photoId].
     *
     * @return the photo, or `null` if it does not exist or its image data is invalid.
     */
    suspend fun getPhoto(photoId: String): StoredPhoto?
}
