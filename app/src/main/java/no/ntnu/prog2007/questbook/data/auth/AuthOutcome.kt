package no.ntnu.prog2007.questbook.data.auth

/** Why a sign-in or sign-up attempt failed. */
enum class AuthError {
    /** Wrong email or password, or no account with that email. */
    INVALID_CREDENTIALS,

    /** The email address is badly formatted. */
    INVALID_EMAIL,

    /** An account with this email already exists. */
    EMAIL_IN_USE,

    /** Firebase rejected the password as too weak. */
    WEAK_PASSWORD,

    /** No internet connection. */
    NETWORK,

    /** Firebase blocked further attempts for a while. */
    TOO_MANY_ATTEMPTS,

    /** Anything else. */
    UNKNOWN
}

/** The result of a sign-in or sign-up attempt. */
sealed interface AuthOutcome {

    /** The user is now signed in. */
    data object Success : AuthOutcome

    /** The attempt failed because of [error]. */
    data class Failure(val error: AuthError) : AuthOutcome
}
