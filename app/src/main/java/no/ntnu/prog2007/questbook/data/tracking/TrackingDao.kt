package no.ntnu.prog2007.questbook.data.tracking

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Local active sessions and completion results; routes remain in the existing GPS table. */
@Dao
interface TrackingDao {
    /** Loads one quest's resumable summary; positions are read from the existing route DAO. */
    @Query("SELECT * FROM tracking_sessions WHERE questId = :questId")
    suspend fun getSession(questId: String): TrackingSessionEntity?

    /** Duplicate quest IDs fail rather than silently replacing another session. */
    @Insert suspend fun insertSession(session: TrackingSessionEntity)

    /** Updates only progress fields. A zero row count means the session no longer exists. */
    @Query("UPDATE tracking_sessions SET distanceMeters = :distance, elapsedMillis = :elapsed WHERE sessionId = :sessionId")
    suspend fun checkpoint(sessionId: String, distance: Double, elapsed: Long): Int

    /** Removes only the active summary once completion is saved, preserving its route. */
    @Query("DELETE FROM tracking_sessions WHERE questId = :questId")
    suspend fun deleteSession(questId: String)

    /** Used for reopening a completed quest and for idempotent completion retries. */
    @Query("SELECT * FROM quest_results WHERE questId = :questId")
    suspend fun getResult(questId: String): QuestResultEntity?

    /** Database uniqueness also guards against duplicate saves outside the ViewModel. */
    @Insert suspend fun insertResult(result: QuestResultEntity)

    /** User-scoped local history, ready for a later Memories integration. */
    @Query("SELECT * FROM quest_results WHERE userId = :userId ORDER BY completedAtMillis DESC")
    fun observeResults(userId: String): Flow<List<QuestResultEntity>>
}
