package no.ntnu.prog2007.questbook.data.auth

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

    private var signedInUid: String? = if (signedIn) user.uid else null

    override val currentUserId: String?
        get() = signedInUid

    override suspend fun signIn(email: String, password: String): AuthOutcome = attempt()

    override suspend fun createUser(username: String, email: String, password: String): AuthOutcome =
        attempt()

    override suspend fun getCurrentUser(): User? = if (signedInUid != null) user else null

    override fun signOut() {
        signedInUid = null
    }

    private fun attempt(): AuthOutcome {
        attempts++
        if (nextOutcome == AuthOutcome.Success) signedInUid = user.uid
        return nextOutcome
    }
}
