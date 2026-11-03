package no.ntnu.prog2007.questbook.data.groups

/** A problem with the group details the user typed. */
enum class GroupValidationError {
    BLANK_NAME,
    NAME_TOO_LONG,
    DESCRIPTION_TOO_LONG
}

/**
 * Checks group details before they are saved. The limits match the Firestore security
 * rules, so a group that passes here is also accepted by the server.
 *
 * Pure Kotlin, so it can be unit tested on the JVM.
 */
object GroupValidator {

    /** Longest allowed group name. */
    const val MAX_NAME_LENGTH = 40

    /** Longest allowed description. */
    const val MAX_DESCRIPTION_LENGTH = 200

    /** Returns the first problem with the input, or `null` if the group can be saved. */
    fun validate(name: String, description: String): GroupValidationError? = when {
        name.isBlank() -> GroupValidationError.BLANK_NAME
        name.trim().length > MAX_NAME_LENGTH -> GroupValidationError.NAME_TOO_LONG
        description.trim().length > MAX_DESCRIPTION_LENGTH -> GroupValidationError.DESCRIPTION_TOO_LONG
        else -> null
    }
}
