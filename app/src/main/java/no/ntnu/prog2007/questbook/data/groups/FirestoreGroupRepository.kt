package no.ntnu.prog2007.questbook.data.groups

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.ntnu.prog2007.questbook.data.firebase.await
import no.ntnu.prog2007.questbook.model.Group

/**
 * [GroupRepository] backed by Firestore.
 *
 * - `groups/{groupId}`: name, description, inviteCode, ownerId, memberIds, joinedWith, createdAt.
 *   Only members can read a group (see `firestore.rules`).
 * - `inviteCodes/{code}`: groupId, createdBy. Lets a friend find the group from its code
 *   without being able to read or list any group they are not in.
 *
 * @param firestore the Firestore instance to use.
 * @param newInviteCode creates invite codes. Can be replaced in tests.
 */
class FirestoreGroupRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val newInviteCode: () -> String = { InviteCode.generate() }
) : GroupRepository {

    private val groups get() = firestore.collection(GROUPS)
    private val inviteCodes get() = firestore.collection(INVITE_CODES)
    private val users get() = firestore.collection(USERS)

    /** Usernames already loaded, so the same names are not fetched over and over. */
    private val usernameCache = ConcurrentHashMap<String, String>()

    override fun observeGroups(userId: String): Flow<List<Group>> = callbackFlow {
        // The query only asks for groups the user is a member of, which the rules allow.
        val registration = groups
            .whereArrayContains(FIELD_MEMBER_IDS, userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Could not load groups for $userId", error)
                    return@addSnapshotListener
                }
                val loaded = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toGroup() }
                    .sortedBy { it.name.lowercase() }
                trySend(loaded)
            }
        awaitClose { registration.remove() }
    }

    override fun createGroup(ownerId: String, name: String, description: String): Group {
        val document = groups.document()
        val group = Group(
            id = document.id,
            name = name.trim(),
            description = description.trim(),
            inviteCode = newInviteCode(),
            ownerId = ownerId,
            memberIds = listOf(ownerId)
        )
        val batch = firestore.batch()
        batch.set(
            document,
            mapOf(
                FIELD_NAME to group.name,
                FIELD_DESCRIPTION to group.description,
                FIELD_INVITE_CODE to group.inviteCode,
                FIELD_OWNER_ID to ownerId,
                FIELD_MEMBER_IDS to group.memberIds,
                FIELD_CREATED_AT to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            inviteCodes.document(group.inviteCode),
            mapOf(FIELD_GROUP_ID to group.id, FIELD_CREATED_BY to ownerId)
        )
        // Not awaited: Firestore saves the group locally at once and uploads it when online.
        batch.commit().addOnFailureListener { error ->
            Log.w(TAG, "Could not save group ${group.id}", error)
        }
        return group
    }

    override suspend fun joinGroup(userId: String, code: String): JoinOutcome {
        val normalized = InviteCode.normalize(code)
        if (!InviteCode.isValid(normalized)) return JoinOutcome.Failure(GroupError.INVALID_CODE)
        return try {
            // Ask the server, not the local cache: the code must be checked against current data.
            val invite = inviteCodes.document(normalized).get(Source.SERVER).await()
            val groupId = invite.getString(FIELD_GROUP_ID)
                ?: return JoinOutcome.Failure(GroupError.CODE_NOT_FOUND)
            if (isMember(groupId, userId)) return JoinOutcome.Failure(GroupError.ALREADY_MEMBER)
            // The rules only let a non-member add themselves, and only when they also
            // write the right invite code under joinedWith.<uid>.
            groups.document(groupId).update(
                mapOf<String, Any>(
                    FIELD_MEMBER_IDS to FieldValue.arrayUnion(userId),
                    "$FIELD_JOINED_WITH.$userId" to normalized
                )
            ).await()
            JoinOutcome.Success(groupId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseFirestoreException) {
            Log.w(TAG, "Could not join a group", error)
            JoinOutcome.Failure(
                if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) GroupError.NETWORK else GroupError.UNKNOWN
            )
        } catch (error: Exception) {
            Log.w(TAG, "Could not join a group", error)
            JoinOutcome.Failure(GroupError.UNKNOWN)
        }
    }

    /**
     * Returns `true` if [userId] is already in the group. The rules only let members read a
     * group, so for someone who is not a member the read is denied, which means "not a member".
     */
    private suspend fun isMember(groupId: String, userId: String): Boolean =
        try {
            val group = groups.document(groupId).get(Source.SERVER).await()
            (group.get(FIELD_MEMBER_IDS) as? List<*>)?.contains(userId) == true
        } catch (error: FirebaseFirestoreException) {
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) false else throw error
        }

    override suspend fun getUsernames(userIds: List<String>): Map<String, String> {
        val missing = userIds.distinct().filterNot { usernameCache.containsKey(it) }
        // Firestore allows at most 30 ids in one "in" query.
        missing.chunked(MAX_IDS_PER_QUERY).forEach { chunk ->
            try {
                users.whereIn(FieldPath.documentId(), chunk).get().await().documents.forEach { user ->
                    user.getString(FIELD_USERNAME)?.let { usernameCache[user.id] = it }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not load usernames", error)
            }
        }
        return userIds.associateWith { usernameCache[it] ?: UNKNOWN_USERNAME }
    }

    private fun DocumentSnapshot.toGroup(): Group? {
        val name = getString(FIELD_NAME) ?: return null
        val memberIds = (get(FIELD_MEMBER_IDS) as? List<*>)?.filterIsInstance<String>() ?: return null
        return Group(
            id = id,
            name = name,
            description = getString(FIELD_DESCRIPTION).orEmpty(),
            inviteCode = getString(FIELD_INVITE_CODE).orEmpty(),
            ownerId = getString(FIELD_OWNER_ID).orEmpty(),
            memberIds = memberIds
        )
    }

    private companion object {
        const val TAG = "FirestoreGroupRepo"
        const val GROUPS = "groups"
        const val INVITE_CODES = "inviteCodes"
        const val USERS = "users"
        const val FIELD_NAME = "name"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_INVITE_CODE = "inviteCode"
        const val FIELD_OWNER_ID = "ownerId"
        const val FIELD_MEMBER_IDS = "memberIds"
        const val FIELD_JOINED_WITH = "joinedWith"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_GROUP_ID = "groupId"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_USERNAME = "username"
        const val MAX_IDS_PER_QUERY = 30
        const val UNKNOWN_USERNAME = "Unknown"
    }
}
