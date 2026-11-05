package no.ntnu.prog2007.questbook.data.personal

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Assignments snapshot their target and calendar window; reopening cannot reroll them. */
@Entity(tableName = "personal_quests", indices = [Index(value = ["userId", "cadence", "periodKey", "slot"], unique = true)])
data class PersonalQuestEntity(@PrimaryKey val questId: String, val userId: String,
    val templateId: String, val cadence: String, val periodKey: String, val slot: String,
    val title: String, val description: String, val metric: String, val target: Double,
    val periodStartMillis: Long, val periodEndMillis: Long, val zoneId: String,
    val completedAtMillis: Long? = null)

/** Self-reported activity. GPS events are derived from immutable GPS results, never duplicated here. */
@Entity(tableName = "activity_entries")
data class ActivityEntryEntity(@PrimaryKey val id: String, val userId: String, val questId: String,
    val occurredAtMillis: Long, val distanceMeters: Double = 0.0, val activeMillis: Long = 0,
    val pushups: Int = 0, val squats: Int = 0, val visitKind: String? = null)

@Dao
interface PersonalQuestDao {
    @Query("SELECT * FROM personal_quests WHERE userId = :uid ORDER BY periodStartMillis DESC, cadence, slot")
    suspend fun getAssignments(uid: String): List<PersonalQuestEntity>
    @Query("SELECT * FROM personal_quests WHERE userId = :uid ORDER BY periodStartMillis DESC, cadence, slot")
    fun observeAssignments(uid: String): Flow<List<PersonalQuestEntity>>
    @Query("SELECT * FROM personal_quests WHERE questId = :id")
    suspend fun getAssignment(id: String): PersonalQuestEntity?
    @Insert suspend fun insertAssignment(row: PersonalQuestEntity)
    @Update suspend fun updateAssignment(row: PersonalQuestEntity)
    @Query("SELECT * FROM activity_entries WHERE userId = :uid ORDER BY occurredAtMillis, id")
    suspend fun getEntries(uid: String): List<ActivityEntryEntity>
    @Query("SELECT * FROM activity_entries WHERE userId = :uid ORDER BY occurredAtMillis, id")
    fun observeEntries(uid: String): Flow<List<ActivityEntryEntity>>
    @Query("SELECT * FROM activity_entries WHERE id = :id")
    suspend fun getEntry(id: String): ActivityEntryEntity?
    @Insert suspend fun insertEntry(row: ActivityEntryEntity)
}
