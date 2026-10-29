package no.ntnu.prog2007.questbook.data

import no.ntnu.prog2007.questbook.data.auth.AuthRepository
import no.ntnu.prog2007.questbook.data.auth.FirebaseAuthRepository

/**
 * Holds the repository instances shared by the whole app (simple manual dependency injection).
 *
 * ViewModels get their repositories from here by default, so every screen works on the
 * same instance. Tests can replace an instance with a fake before the UI starts.
 */
object AppServices {

    private var auth: AuthRepository? = null

    /** The app's [AuthRepository]. The Firebase implementation is created on first use. */
    var authRepository: AuthRepository
        get() = auth ?: FirebaseAuthRepository().also { auth = it }
        set(value) {
            auth = value
        }

    /** Goes back to the real implementations. Used by tests to clean up after themselves. */
    fun reset() {
        auth = null
    }
}
