package no.ntnu.prog2007.questbook.data.challenges

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.personal.ActivityEntryEntity
import no.ntnu.prog2007.questbook.data.photo.MemoryPhotoEntity
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity

/** Shared definition. OPEN has one independent attempt per member; DRAW belongs only to its drawer. */
@Entity(tableName = "group_challenges")
data class GroupChallengeEntity(@PrimaryKey val questId: String, val groupId: String, val kind: String,
    val metric: String, val target: Double, val periodKey: String, val startMillis: Long,
    val endMillis: Long, val zoneId: String, val memberCount: Int, val templateId: String,
    val assigneeId: String? = null)

/** A separate owned quest ID lets the existing GPS/result uniqueness constraints remain unchanged. */
@Entity(tableName = "quest_attempts", indices = [Index(value = ["challengeId", "userId"], unique = true)])
data class QuestAttemptEntity(@PrimaryKey val attemptId: String, val challengeId: String, val userId: String)

/** Membership spells exclude activity before joining, after leaving, and during a leave/rejoin gap. */
@Entity(tableName = "group_membership_spells")
data class GroupMembershipSpell(@PrimaryKey val id: String, val groupId: String, val userId: String,
    val joinedAtMillis: Long, val leftAtMillis: Long? = null)

@Dao
interface GroupChallengeDao {
    @Query("SELECT * FROM group_challenges") fun observeChallenges(): Flow<List<GroupChallengeEntity>>
    @Query("SELECT * FROM group_challenges ORDER BY startMillis, questId") suspend fun challenges(): List<GroupChallengeEntity>
    @Query("SELECT * FROM group_challenges WHERE questId = :id") suspend fun challenge(id: String): GroupChallengeEntity?
    @Insert suspend fun insertChallenge(row: GroupChallengeEntity)
    @Query("SELECT * FROM quest_attempts") suspend fun attempts(): List<QuestAttemptEntity>
    @Query("SELECT * FROM quest_attempts WHERE challengeId = :id AND userId = :uid") suspend fun attempt(id: String, uid: String): QuestAttemptEntity?
    @Query("SELECT * FROM quest_attempts WHERE attemptId = :id") suspend fun attemptById(id: String): QuestAttemptEntity?
    @Insert suspend fun insertAttempt(row: QuestAttemptEntity)
    @Query("SELECT * FROM group_membership_spells") suspend fun spells(): List<GroupMembershipSpell>
    @Insert suspend fun insertSpell(row: GroupMembershipSpell)
    @Update suspend fun updateSpell(row: GroupMembershipSpell)
    // These broad reads are private to the store, which filters by current group membership before returning UI data.
    @Query("SELECT * FROM quest_results") fun observeResults(): Flow<List<QuestResultEntity>>
    @Query("SELECT * FROM quest_results") suspend fun results(): List<QuestResultEntity>
    @Query("SELECT * FROM activity_entries") fun observeEntries(): Flow<List<ActivityEntryEntity>>
    @Query("SELECT * FROM activity_entries") suspend fun entries(): List<ActivityEntryEntity>
    @Query("SELECT * FROM memory_photos") fun observePhotos(): Flow<List<MemoryPhotoEntity>>
    @Query("SELECT * FROM memory_photos") suspend fun photos(): List<MemoryPhotoEntity>

    // Used by the cloud sync. "Insert if absent" makes applying the same snapshot twice harmless.
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertQuestIfAbsent(row: QuestEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertChallengeIfAbsent(row: GroupChallengeEntity): Long
    @Update suspend fun updateChallenge(row: GroupChallengeEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAttemptIfAbsent(row: QuestAttemptEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertResultIfAbsent(row: QuestResultEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEntryIfAbsent(row: ActivityEntryEntity): Long
}
