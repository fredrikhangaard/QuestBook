package no.ntnu.prog2007.questbook.data

import no.ntnu.prog2007.questbook.data.auth.AuthRepository
import no.ntnu.prog2007.questbook.data.auth.FirebaseAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FirestoreGroupRepository
import no.ntnu.prog2007.questbook.data.groups.GroupRepository
import no.ntnu.prog2007.questbook.data.sync.ChallengeSync
import no.ntnu.prog2007.questbook.data.sync.FirestoreChallengeSync
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase

/**
 * Holds the repository instances shared by the whole app (simple manual dependency injection).
 *
 * ViewModels get their repositories from here by default, so every screen works on the
 * same instance. Tests can replace an instance with a fake before the UI starts.
 */
object AppServices {

    private var auth: AuthRepository? = null
    private var groups: GroupRepository? = null

    /** The app's [AuthRepository]. The Firebase implementation is created on first use. */
    var authRepository: AuthRepository
        get() = auth ?: FirebaseAuthRepository().also { auth = it }
        set(value) {
            auth = value
        }

    /** The app's [GroupRepository]. The Firestore implementation is created on first use. */
    var groupRepository: GroupRepository
        get() = groups ?: FirestoreGroupRepository().also { groups = it }
        set(value) {
            groups = value
        }

    /**
     * Creates the group quest sync for a database. Firestore is not opened until this is called.
     * Tests that must stay offline can set this to `{ ChallengeSync.None }`.
     */
    var challengeSyncFactory: (QuestBookDatabase) -> ChallengeSync = { FirestoreChallengeSync(it) }

    /** Goes back to the real implementations. Used by tests to clean up after themselves. */
    fun reset() {
        auth = null
        groups = null
        challengeSyncFactory = { FirestoreChallengeSync(it) }
    }
}
