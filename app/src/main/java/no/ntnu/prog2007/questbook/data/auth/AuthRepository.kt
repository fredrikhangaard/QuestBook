package no.ntnu.prog2007.questbook.data.auth

import no.ntnu.prog2007.questbook.model.User

/**
 * Signs users in and out and creates new accounts.
 *
 * ViewModels only depend on this interface, so they don't need to know that
 * Firebase is used behind it (Repository pattern), and tests can use [FakeAuthRepository].
 */
interface AuthRepository {

    /** The signed-in user's id ([User.uid]), or `null` if nobody is signed in. */
    val currentUserId: String?

    /** Signs in with an existing account. */
    suspend fun signIn(email: String, password: String): AuthOutcome

    /** Creates a new account, saves the user's profile and signs the user in. */
    suspend fun createUser(username: String, email: String, password: String): AuthOutcome

    /** Loads the signed-in user's profile, or returns `null` if nobody is signed in. */
    suspend fun getCurrentUser(): User?

    /** Signs the current user out. */
    fun signOut()
}
