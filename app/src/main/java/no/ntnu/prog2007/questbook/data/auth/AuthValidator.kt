package no.ntnu.prog2007.questbook.data.auth

/** A problem with what the user typed, found before contacting Firebase. */
enum class AuthValidationError {
    BLANK_USERNAME,
    USERNAME_TOO_LONG,
    INVALID_EMAIL,
    BLANK_PASSWORD,
    PASSWORD_TOO_SHORT
}

/**
 * Checks sign-in and sign-up input before it is sent to Firebase, so the user gets
 * quick feedback and we avoid requests that are sure to fail.
 *
 * Pure Kotlin (no Android classes), so it can be unit tested on the JVM.
 */
object AuthValidator {

    /** Firebase Authentication requires passwords of at least 6 characters. */
    const val MIN_PASSWORD_LENGTH = 6

    /** Longest allowed username. */
    const val MAX_USERNAME_LENGTH = 30

    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Returns `true` if [email] looks like an email address (something@domain.tld). */
    fun isValidEmail(email: String): Boolean = emailPattern.matches(email.trim())

    /** Returns the first problem with the sign-in input, or `null` if it can be sent. */
    fun validateSignIn(email: String, password: String): AuthValidationError? = when {
        !isValidEmail(email) -> AuthValidationError.INVALID_EMAIL
        password.isEmpty() -> AuthValidationError.BLANK_PASSWORD
        else -> null
    }

    /** Returns the first problem with the sign-up input, or `null` if it can be sent. */
    fun validateCreateUser(username: String, email: String, password: String): AuthValidationError? =
        when {
            username.isBlank() -> AuthValidationError.BLANK_USERNAME
            username.trim().length > MAX_USERNAME_LENGTH -> AuthValidationError.USERNAME_TOO_LONG
            !isValidEmail(email) -> AuthValidationError.INVALID_EMAIL
            password.length < MIN_PASSWORD_LENGTH -> AuthValidationError.PASSWORD_TOO_SHORT
            else -> null
        }
}
