package no.ntnu.prog2007.questbook.ui.challenges

import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZoneId
import java.util.UUID
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.challenges.*
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.*

/** One account-scoped observer feeds group pages, completion galleries and the shared Memories cards. */
data class GroupChallengesUiState(val userId: String? = null, val groups: List<Group> = emptyList(),
    val names: Map<String, String> = emptyMap(), val snapshot: GroupChallengeSnapshot = GroupChallengeSnapshot(),
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null)

class GroupChallengesViewModel(private val db: QuestBookDatabase) : ViewModel() {
    private val store = GroupChallengeStore(db)
    private val sync = AppServices.challengeSyncFactory(db)
    private val personal = PersonalQuestStore(db)
    private val mutable = MutableStateFlow(GroupChallengesUiState())
    val state = mutable.asStateFlow()
    private var pendingDraw: Pair<String, String>? = null
    private val refresh = MutableStateFlow(0)

    init { viewModelScope.launch {
        AppServices.authRepository.currentUserIdFlow.collectLatest { uid ->
            mutable.value = GroupChallengesUiState(userId = uid, loading = uid != null)
            if (uid == null) return@collectLatest
            AppServices.groupRepository.observeGroups(uid).collectLatest { groups ->
                val names = try { AppServices.groupRepository.getUsernames(groups.flatMap { it.memberIds }.distinct()) }
                    catch (error: CancellationException) { throw error } catch (_: Exception) { emptyMap() }
                coroutineScope {
                    // Cloud -> Room for these groups. Stops by itself when the groups or the account change.
                    launch { sync.listen(uid, groups) }
                    combine(combine(db.questDao().observeQuests(), db.groupChallengeDao().observeChallenges()) { quests, _ -> quests }, db.groupChallengeDao().observeResults(),
                        db.groupChallengeDao().observeEntries(), db.groupChallengeDao().observePhotos(),
                        combine(refresh, flow { while (true) { emit(System.currentTimeMillis()); delay(30000) } }) { _, now -> now }
                    ) { _, _, _, _, now -> now }.collectLatest { now ->
                        try {
                            val snapshot = store.refresh(uid, groups, now, ZoneId.systemDefault())
                            mutable.update { it.copy(groups = groups, names = names, snapshot = snapshot, loading = false) }
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { mutable.update { it.copy(loading = false, error = "Could not load group quests. Try again.") } }
                        // Room -> cloud, after refresh so this week's goal and new awards are included.
                        // A failed upload must not hide the quests, so it is only logged.
                        try { sync.push(uid, names[uid] ?: "Member", groups) }
                        catch (error: CancellationException) { throw error }
                        catch (error: Exception) { android.util.Log.w("GroupChallenges", "Could not upload group quests", error) }
                    }
                }
            }
        }
    } }

    private fun group(uid: String, groupId: String): Group {
        check(AppServices.authRepository.currentUserId == uid)
        return checkNotNull(state.value.groups.find { it.id == groupId && uid in it.memberIds }) { "This group is not available to you." }
    }
    private fun action(block: suspend (String) -> Unit) {
        if (state.value.busy) return
        val uid = AppServices.authRepository.currentUserId ?: return
        if (uid != state.value.userId) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block(uid); refresh.value++ }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = error.message ?: "Could not save quest. Try again.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    /** Navigation reads durable metadata when a newly created card arrives before its UI snapshot. */
    fun resolveParent(id: String, ready: (String?) -> Unit) {
        val uid = AppServices.authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val parent = db.groupChallengeDao().attemptById(id)?.challengeId
                    ?: db.groupChallengeDao().challenge(id)?.questId
                if (AppServices.authRepository.currentUserId == uid) ready(parent)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(error = "Could not open this quest. Try again.") } }
        }
    }
    fun retry() { mutable.update { it.copy(error = null) }; refresh.value++ }
    fun draw(groupId: String, ready: (String) -> Unit) = action { uid ->
        val key = "$uid/$groupId"
        val operation = pendingDraw?.takeIf { it.first == key }?.second ?: UUID.randomUUID().toString().also { pendingDraw = key to it }
        val id = store.draw(uid, group(uid, groupId), state.value.names[uid] ?: "Member", operation,
            System.currentTimeMillis(), ZoneId.systemDefault())
        pendingDraw = null
        if (AppServices.authRepository.currentUserId == uid) ready(id)
    }
    fun open(id: String) = action { uid ->
        val row = checkNotNull(state.value.snapshot.challenges.find { it.questId == id })
        store.open(uid, group(uid, row.groupId), id, state.value.names[uid] ?: "Member", System.currentTimeMillis())
    }
    /** Recheck current membership before recording an owned attempt, not only when drawing its page. */
    fun logAttempt(child: String, amount: String) = action { uid ->
        val attempt = checkNotNull(state.value.snapshot.attempts.find { it.attemptId == child && it.userId == uid })
        val definition = checkNotNull(state.value.snapshot.challenges.find { it.questId == attempt.challengeId })
        group(uid, definition.groupId)
        val value = amount.toDoubleOrNull() ?: error("Enter a valid amount.")
        val key = "$uid/$child/$amount"
        val operation = pendingDraw?.takeIf { it.first == key }?.second ?: UUID.randomUUID().toString().also { pendingDraw = key to it }
        personal.logManual(uid, child, value, 0.0, operation, System.currentTimeMillis())
        pendingDraw = null
    }
    /** Create's form awaits this transaction, so a parent cannot exist without its participation rules. */
    suspend fun saveCreated(quest: Quest, target: Double) {
        val uid = checkNotNull(AppServices.authRepository.currentUserId)
        val metric = when (quest.type) { QuestType.DISTANCE -> ActivityMetric.WALK_METERS
            QuestType.WORKOUT -> ActivityMetric.BODYWEIGHT_REPS; QuestType.VISIT_PLACE -> ActivityMetric.OUTDOOR_VISITS
            else -> ActivityMetric.CONFIRMATIONS }
        store.create(uid, group(uid, quest.groupId), quest, metric, target, System.currentTimeMillis())
    }
    /** Weekly group walks are bounded, daily-sized sessions. They never start an unlimited GPS logger. */
    fun startWeekly(id: String, ready: (String) -> Unit) = action { uid ->
        val row = checkNotNull(state.value.snapshot.challenges.find { it.questId == id && it.kind == "WEEKLY" })
        group(uid, row.groupId)
        check(state.value.snapshot.pendingWalks[id] != null || System.currentTimeMillis() < row.endMillis)
        val remaining = row.target - (state.value.snapshot.progress[id] ?: 0.0)
        val child = personal.boundedWalk(uid, id, remaining, System.currentTimeMillis(), ZoneId.systemDefault())
        if (AppServices.authRepository.currentUserId == uid) ready(child)
    }
    /** A weekly set is a physical event, not an editable total or a second copy of a saved daily set. */
    fun logWeekly(id: String, amount: String) = action { uid ->
        val row = checkNotNull(state.value.snapshot.challenges.find { it.questId == id && it.kind == "WEEKLY" })
        group(uid, row.groupId)
        val metric = ActivityMetric.valueOf(row.metric)
        require(metric in listOf(ActivityMetric.BODYWEIGHT_REPS, ActivityMetric.OUTDOOR_VISITS))
        check(System.currentTimeMillis() in row.startMillis until row.endMillis)
        val value = amount.toDoubleOrNull() ?: error("Enter a whole number.")
        val remaining = row.target - (state.value.snapshot.progress[id] ?: 0.0)
        require(value.isFinite() && value > 0 && value % 1.0 == 0.0 && value <= remaining)
        val key = "$uid/$id/$amount"
        val operation = pendingDraw?.takeIf { it.first == key }?.second ?: UUID.randomUUID().toString().also { pendingDraw = key to it }
        // Store validation and the activity insert share a transaction; retries reuse the event ID.
        store.recordWeekly(uid, group(uid, row.groupId), id, value, operation, System.currentTimeMillis())
        pendingDraw = null
    }
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GroupChallengesViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return GroupChallengesViewModel(QuestBookDatabase.getDatabase(context.applicationContext)) as T
        }
    }
}
