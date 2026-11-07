package no.ntnu.prog2007.questbook.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.AuthRepository
import no.ntnu.prog2007.questbook.data.groups.GroupRepository
import no.ntnu.prog2007.questbook.model.Group

/**
 * A group as shown on Home, in Groups and on the group page.
 *
 * @property members display names, with the signed-in user first as "You".
 * @property description text about the group (may be empty).
 * @property inviteCode the code friends use to join.
 * @property ownerId uid of the user who created the group.
 * @property memberIds uids in the same order as [members].
 */
data class GroupSummary(
    val id: String,
    val name: String,
    val members: List<String>,
    val description: String,
    val inviteCode: String = "",
    val ownerId: String = "",
    val memberIds: List<String> = emptyList()
)

/** Name shown for the signed-in user in member lists. */
internal const val YOU = "You"

/**
 * Turns a stored [Group] into what the screens show. The signed-in user is listed first,
 * as "You", and the other members keep their order.
 */
internal fun Group.toSummary(currentUserId: String, usernames: Map<String, String>): GroupSummary {
    val ordered = memberIds.sortedBy { if (it == currentUserId) 0 else 1 }
    return GroupSummary(
        id = id,
        name = name,
        members = ordered.map { uid -> if (uid == currentUserId) YOU else usernames[uid] ?: "Unknown" },
        description = description,
        inviteCode = inviteCode,
        ownerId = ownerId,
        memberIds = ordered
    )
}

/**
 * Supplies the signed-in user's groups to Home, Groups and the group pages, so they
 * always show the same data. Follows sign-in and sign-out: when nobody is signed in
 * the list is empty, and a new user never sees the previous user's groups.
 *
 * @param authRepository tells which user is signed in.
 * @param groupRepository where the groups are loaded from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModel(
    private val authRepository: AuthRepository = AppServices.authRepository,
    private val groupRepository: GroupRepository = AppServices.groupRepository
) : ViewModel() {

    private val mutableLeaveState = MutableStateFlow(LeaveGroupState())
    val leaveState = mutableLeaveState.asStateFlow()

    /** Completion is reported only after the server accepts the membership change. */
    fun leave(groupId: String, onLeft: () -> Unit) {
        if (mutableLeaveState.value.busy) return
        val uid = authRepository.currentUserId ?: return
        mutableLeaveState.value = LeaveGroupState(busy = true)
        viewModelScope.launch {
            try {
                groupRepository.leaveGroup(uid, groupId)
                mutableLeaveState.value = LeaveGroupState()
                if (authRepository.currentUserId == uid) onLeft()
            } catch (cancelled: CancellationException) {
                mutableLeaveState.value = LeaveGroupState()
                throw cancelled
            } catch (_: Exception) {
                mutableLeaveState.value = LeaveGroupState(error = "Could not leave the group. Check your connection and try again.")
            }
        }
    }

    fun clearLeaveError() { if (!mutableLeaveState.value.busy) mutableLeaveState.value = LeaveGroupState() }

    /** The signed-in user's groups, sorted by name. Updates live when groups change. */
    val groups: StateFlow<List<GroupSummary>> = authRepository.currentUserIdFlow
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList<GroupSummary>())
            } else {
                groupRepository.observeGroups(userId).map { groups -> toSummaries(groups, userId) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private suspend fun toSummaries(groups: List<Group>, userId: String): List<GroupSummary> {
        val usernames = groupRepository.getUsernames(groups.flatMap { it.memberIds }.distinct())
        return groups.map { it.toSummary(userId, usernames) }
    }
}

/** State for the confirmed online leave operation. */
data class LeaveGroupState(val busy: Boolean = false, val error: String? = null)
