package no.ntnu.prog2007.questbook.data.groups

import kotlinx.coroutines.flow.Flow
import no.ntnu.prog2007.questbook.model.Group

/**
 * Stores and loads private QuestBook groups.
 *
 * ViewModels only depend on this interface, so they don't need to know that Firestore
 * is used behind it (Repository pattern), and tests can use [FakeGroupRepository].
 */
interface GroupRepository {

    /** Emits the groups [userId] is a member of, and emits again whenever they change. */
    fun observeGroups(userId: String): Flow<List<Group>>

    /**
     * Creates a new group with [ownerId] as its first member and a fresh invite code.
     *
     * The group is saved locally at once and uploaded when the device is online,
     * so this also works without an internet connection.
     *
     * @return the new group.
     */
    fun createGroup(ownerId: String, name: String, description: String): Group

    /**
     * Adds [userId] to the group that has the invite code [code].
     *
     * Needs an internet connection, because the code must be checked with the server.
     * Joining a group you are already in succeeds and changes nothing.
     */
    suspend fun joinGroup(userId: String, code: String): JoinOutcome

    /**
     * Looks up the usernames of [userIds].
     *
     * @return a map from uid to username. Users whose name could not be loaded get "Unknown".
     */
    suspend fun getUsernames(userIds: List<String>): Map<String, String>
}
