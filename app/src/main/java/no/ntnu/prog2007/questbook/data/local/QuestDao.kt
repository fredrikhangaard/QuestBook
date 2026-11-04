package no.ntnu.prog2007.questbook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Reads and writes locally persisted quests. */
@Dao
interface QuestDao {

    /** Emits a new list whenever a quest changes. */
    @Query(
        """
        SELECT * FROM quests
        ORDER BY createdAtMillis DESC, id ASC
        """
    )
    fun observeQuests(): Flow<List<QuestEntity>>

    /** Loads a single quest directly from the database. */
    @Query("SELECT * FROM quests WHERE id = :questId LIMIT 1")
    suspend fun getQuest(questId: String): QuestEntity?

    /** Inserts a new quest; duplicate IDs produce an error. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuest(quest: QuestEntity)

    /**
     * Updates an existing quest.
     *
     * Returns the number of updated rows, so the caller can detect
     * whether the quest was missing.
     */
    @Update
    suspend fun updateQuest(quest: QuestEntity): Int
    /**
     * Updates only assignment fields, preserving the target and any other quest information.
     * The status condition is evaluated by SQLite at write time: if GPS started after the
     * picker opened, the update affects zero rows instead of reassigning an active route.
     */
    @Query("""
        UPDATE quests SET assignedUserId = :userId, assignedTo = :displayName
        WHERE id = :questId AND groupId = :groupId AND status = 'NOT_STARTED'
    """)
    suspend fun assignQuest(questId: String, groupId: String, userId: String, displayName: String): Int
}