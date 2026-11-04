package no.ntnu.prog2007.questbook.ui.quest.assignment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.AuthRepository
import no.ntnu.prog2007.questbook.data.groups.GroupRepository
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.data.repository.QuestRepository
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.ui.groups.GroupSummary

/** A real group member. "You" is only a label in the picker, never the persisted name. */
data class AssignmentMember(val userId: String, val displayName: String, val isCurrentUser: Boolean)

/** Keeps selection/error state visible until assignment persistence actually succeeds. */
data class AssignQuestUiState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val members: List<AssignmentMember> = emptyList(),
    val selectedUserId: String? = null,
    val error: String? = null
)

/** Validates against the latest group snapshot without modifying Firestore group data. */
class AssignQuestViewModel(
    private val questId: String,
    private val quests: QuestRepository,
    private val auth: AuthRepository,
    private val groups: GroupRepository,
    private val currentGroup: () -> GroupSummary?
) : ViewModel() {
    // Screens observe this state. They never write assignment fields directly into a Quest.
    private val mutableState = MutableStateFlow(AssignQuestUiState())
    val uiState = mutableState.asStateFlow()
    private var loadJob: Job? = null
    // Prevent an old member lookup from updating a picker that was dismissed or reopened.
    private var loadGeneration = 0

    /** Uses the latest signed-in user, quest status, and group membership on every operation. */
    private fun eligibleQuestAndGroup(): Pair<Quest, GroupSummary> {
        val user = checkNotNull(auth.currentUserId) { "Sign in before assigning a quest." }
        val quest = checkNotNull(quests.getQuest(questId)) { "Quest not found." }
        val group = checkNotNull(currentGroup()) { "Wait for the group to load." }
        check(group.id == quest.groupId && user in group.memberIds) { "Only members of this group can assign its quests." }
        check(quest.status == QuestStatus.NOT_STARTED) { "Assignments cannot change after a quest has started." }
        return quest to group
    }

    /** Loads current usernames from the existing group repository; it performs no remote writes. */
    fun open() {
        if (uiState.value.isSaving) return
        val generation = ++loadGeneration
        loadJob?.cancel()
        mutableState.value = AssignQuestUiState(isOpen = true, isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                val (quest, group) = eligibleQuestAndGroup()
                val user = auth.currentUserId
                val names = groups.getUsernames(group.memberIds)
                // Membership can change while usernames are loading; recheck after the suspension.
                val current = eligibleQuestAndGroup().second
                val members = current.memberIds.distinct().map { id ->
                    AssignmentMember(id, names[id]?.takeIf { it.isNotBlank() } ?: "Unknown", id == user)
                }
                if (generation != loadGeneration) return@launch
                mutableState.update { it.copy(members = members,
                    selectedUserId = quest.assignedUserId?.takeIf { id -> members.any { it.userId == id } }) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == loadGeneration) mutableState.update { it.copy(error = error.message ?: "Could not load group members.") }
            }
            finally { if (generation == loadGeneration) mutableState.update { it.copy(isLoading = false) } }
        }
    }

    /** Select by stable UID; two group members may have identical display names. */
    fun select(userId: String) {
        if (uiState.value.isLoading || uiState.value.isSaving || uiState.value.members.none { it.userId == userId }) return
        mutableState.update { it.copy(selectedUserId = userId, error = null) }
    }

    /** Cancels a slow lookup, but does not interrupt a database save already in progress. */
    fun dismiss() {
        if (uiState.value.isSaving) return
        ++loadGeneration
        loadJob?.cancel()
        mutableState.update { it.copy(isOpen = false, isLoading = false) }
    }

    /** Rechecks membership and quest state after selection; Room also rejects a racing GPS start. */
    fun assign() {
        val state = uiState.value
        if (!state.isOpen || state.isLoading || state.isSaving) return
        val member = state.members.find { it.userId == state.selectedUserId }
        if (member == null) {
            mutableState.update { it.copy(error = "Choose a group member.") }
            return
        }
        // Set saving before launching so rapid taps cannot queue duplicate assignment writes.
        mutableState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val (quest, group) = eligibleQuestAndGroup()
                check(member.userId in group.memberIds) { "This person is no longer a group member. Reload the list." }
                // Persist the actual username, not the picker's added "(You)" label.
                quests.assignQuest(quest.id, group.id, member.userId, member.displayName)
                mutableState.update { it.copy(isOpen = false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableState.update { it.copy(error = error.message ?: "Could not save assignment. Try again.") } }
            finally { mutableState.update { it.copy(isSaving = false) } }
        }
    }

    /** Uses current group data from the existing navigation-level GroupsViewModel. */
    class Factory(private val questId: String, private val currentGroup: () -> GroupSummary?) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AssignQuestViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AssignQuestViewModel(questId, QuestDependencies.questRepository,
                AppServices.authRepository, AppServices.groupRepository, currentGroup) as T
        }
    }
}
