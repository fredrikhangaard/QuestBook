package no.ntnu.prog2007.questbook.data.photo

import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import java.util.UUID

/** Enforces the result/owner link before writing local photos. Never uploads to Firestore. */
class LocalMemoryPhotoStore(
    private val db: QuestBookDatabase,
    private val currentUserId: () -> String?
) {
    suspend fun save(questId: String, jpeg: ByteArray, caption: String) {
        val uid = currentUserId() ?: error("Sign in first")
        val result = db.trackingDao().getResult(questId) ?: error("No completed result")
        require(result.userId == uid) { "This result belongs to another user" }
        require(jpeg.isNotEmpty() && jpeg.size <= 675000)
        require(caption.length <= 200)
        // Check again after the database suspension in case the user signed out meanwhile.
        check(currentUserId() == uid)
        db.memoryPhotoDao().insert(MemoryPhotoEntity(UUID.randomUUID().toString(), result.id,
            questId, result.groupId, uid, caption, jpeg, System.currentTimeMillis()))
    }
}
