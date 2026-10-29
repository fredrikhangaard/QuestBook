package no.ntnu.prog2007.questbook.data.auth

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import no.ntnu.prog2007.questbook.data.firebase.await
import no.ntnu.prog2007.questbook.model.User

/**
 * [AuthRepository] backed by Firebase Authentication (email and password).
 *
 * The user's profile (username and email) is stored in the Firestore collection
 * `users`, in a document whose id is the user's uid. Firebase remembers the signed-in
 * user between app starts, so [currentUserId] is already set when the app opens.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    private val users get() = firestore.collection(USERS)

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override suspend fun signIn(email: String, password: String): AuthOutcome = attempt {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun createUser(username: String, email: String, password: String): AuthOutcome =
        attempt {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val uid = checkNotNull(result.user?.uid) { "Firebase returned no user" }
            val profile = mapOf(
                FIELD_USERNAME to username.trim(),
                FIELD_EMAIL to email.trim(),
                FIELD_CREATED_AT to FieldValue.serverTimestamp()
            )
            // Not awaited: Firestore saves the profile locally at once and uploads it when online.
            users.document(uid).set(profile).addOnFailureListener { error ->
                Log.w(TAG, "Could not save the profile for $uid", error)
            }
        }

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        val email = firebaseUser.email.orEmpty()
        val username = try {
            users.document(firebaseUser.uid).get().await().getString(FIELD_USERNAME)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not load the profile for ${firebaseUser.uid}", error)
            null
        }
        return User(
            uid = firebaseUser.uid,
            username = username ?: email.substringBefore('@'),
            email = email
        )
    }

    override fun signOut() = auth.signOut()

    /** Runs [block] and turns any Firebase error into an [AuthOutcome.Failure]. */
    private suspend fun attempt(block: suspend () -> Unit): AuthOutcome =
        try {
            block()
            AuthOutcome.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Authentication failed", error)
            AuthOutcome.Failure(error.toAuthError())
        }

    private fun Exception.toAuthError(): AuthError = when (this) {
        // Weak password is a subclass of invalid credentials, so it must be checked first.
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AuthError.INVALID_EMAIL else AuthError.INVALID_CREDENTIALS
        is FirebaseAuthInvalidUserException -> AuthError.INVALID_CREDENTIALS
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_ATTEMPTS
        else -> AuthError.UNKNOWN
    }

    private companion object {
        const val TAG = "FirebaseAuthRepo"
        const val USERS = "users"
        const val FIELD_USERNAME = "username"
        const val FIELD_EMAIL = "email"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
