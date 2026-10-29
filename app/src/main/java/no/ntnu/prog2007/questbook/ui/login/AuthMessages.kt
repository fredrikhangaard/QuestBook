package no.ntnu.prog2007.questbook.ui.login

import no.ntnu.prog2007.questbook.data.auth.AuthError
import no.ntnu.prog2007.questbook.data.auth.AuthValidationError
import no.ntnu.prog2007.questbook.data.auth.AuthValidator

/** The message shown to the user when Firebase rejects a sign-in or sign-up. */
internal fun AuthError.toMessage(): String = when (this) {
    AuthError.INVALID_CREDENTIALS -> "Wrong email or password."
    AuthError.INVALID_EMAIL -> "That email address doesn't look right."
    AuthError.EMAIL_IN_USE -> "An account with this email already exists. Try signing in instead."
    AuthError.WEAK_PASSWORD -> "The password is too weak. Try a longer one."
    AuthError.NETWORK -> "No internet connection. Try again when you're online."
    AuthError.TOO_MANY_ATTEMPTS -> "Too many attempts. Wait a moment and try again."
    AuthError.UNKNOWN -> "Something went wrong. Please try again."
}

/** The message shown to the user when the input is not valid. */
internal fun AuthValidationError.toMessage(): String = when (this) {
    AuthValidationError.BLANK_USERNAME -> "Enter a username."
    AuthValidationError.USERNAME_TOO_LONG ->
        "The username can be at most ${AuthValidator.MAX_USERNAME_LENGTH} characters."
    AuthValidationError.INVALID_EMAIL -> "Enter a valid email address."
    AuthValidationError.BLANK_PASSWORD -> "Enter your password."
    AuthValidationError.PASSWORD_TOO_SHORT ->
        "The password must be at least ${AuthValidator.MIN_PASSWORD_LENGTH} characters."
}
