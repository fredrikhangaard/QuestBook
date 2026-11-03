package no.ntnu.prog2007.questbook.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import no.ntnu.prog2007.questbook.model.User

/**
 * In-memory [AuthRepository] for tests and previews. Never talks to Firebase.
 *
 * @param user the user who is signed in after a successful attempt.
 * @param signedIn whether [user] starts out signed in.
 */
class FakeAuthRepository(
    private val user: User = User(uid = "test-user", username = "Test User", email = "test@example.com"),
    signedIn: Boolean = false
) : AuthRepository {

    /** What the next [signIn] or [createUser] call returns. */
    var nextOutcome: AuthOutcome = AuthOutcome.Success

    /** How many times [signIn] or [createUser] has been called. */
    var attempts: Int = 0
        private set

    private val signedInUid = MutableStateFlow(if (signedIn) user.uid else null)

    override val currentUserId: String?
        get() = signedInUid.value

    override val currentUserIdFlow: Flow<String?> = signedInUid

    override suspend fun signIn(email: String, password: String): AuthOutcome = attempt()

    override suspend fun createUser(username: String, email: String, password: String): AuthOutcome =
        attempt()

    override suspend fun getCurrentUser(): User? = if (signedInUid.value != null) user else null

    override fun signOut() {
        signedInUid.value = null
    }

    private fun attempt(): AuthOutcome {
        attempts++
        if (nextOutcome == AuthOutcome.Success) signedInUid.value = user.uid
        return nextOutcome
    }
}
