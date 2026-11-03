package no.ntnu.prog2007.questbook.data.groups

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import no.ntnu.prog2007.questbook.model.Group

/**
 * In-memory [GroupRepository] for tests and previews. Never talks to Firestore.
 *
 * @param initialGroups groups that exist from the start.
 * @param usernames usernames returned by [getUsernames], by uid.
 * @param newInviteCode creates invite codes for new groups.
 */
class FakeGroupRepository(
    initialGroups: List<Group> = emptyList(),
    private val usernames: Map<String, String> = emptyMap(),
    private val newInviteCode: () -> String = { InviteCode.generate() }
) : GroupRepository {

    private val groups = MutableStateFlow(initialGroups)

    /** All groups, members or not. Lets tests check what was saved. */
    val allGroups: List<Group> get() = groups.value

    /** How many times [createGroup] has been called. */
    var createCalls: Int = 0
        private set

    /** How many times [joinGroup] has been called. */
    var joinCalls: Int = 0
        private set

    /** If set, the next [joinGroup] call fails with this error, e.g. to test being offline. */
    var nextJoinError: GroupError? = null

    override fun observeGroups(userId: String): Flow<List<Group>> =
        groups.map { all -> all.filter { userId in it.memberIds }.sortedBy { it.name.lowercase() } }

    override fun createGroup(ownerId: String, name: String, description: String): Group {
        createCalls++
        val group = Group(
            id = "group-${groups.value.size + 1}",
            name = name.trim(),
            description = description.trim(),
            inviteCode = newInviteCode(),
            ownerId = ownerId,
            memberIds = listOf(ownerId)
        )
        groups.update { it + group }
        return group
    }

    override suspend fun joinGroup(userId: String, code: String): JoinOutcome {
        joinCalls++
        nextJoinError?.let { error ->
            nextJoinError = null
            return JoinOutcome.Failure(error)
        }
        val normalized = InviteCode.normalize(code)
        if (!InviteCode.isValid(normalized)) return JoinOutcome.Failure(GroupError.INVALID_CODE)
        val group = groups.value.find { it.inviteCode == normalized }
            ?: return JoinOutcome.Failure(GroupError.CODE_NOT_FOUND)
        if (userId in group.memberIds) return JoinOutcome.Failure(GroupError.ALREADY_MEMBER)
        groups.update { all ->
            all.map { if (it.id == group.id) it.copy(memberIds = it.memberIds + userId) else it }
        }
        return JoinOutcome.Success(group.id)
    }

    override suspend fun getUsernames(userIds: List<String>): Map<String, String> =
        userIds.associateWith { usernames[it] ?: "Unknown" }
}
