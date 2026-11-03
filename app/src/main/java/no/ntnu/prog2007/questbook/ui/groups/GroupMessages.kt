package no.ntnu.prog2007.questbook.ui.groups

import no.ntnu.prog2007.questbook.data.groups.GroupValidationError
import no.ntnu.prog2007.questbook.data.groups.GroupValidator

/** The message shown when the group details are not valid. */
internal fun GroupValidationError.toMessage(): String = when (this) {
    GroupValidationError.BLANK_NAME -> "Give the group a name."
    GroupValidationError.NAME_TOO_LONG ->
        "The name can be at most ${GroupValidator.MAX_NAME_LENGTH} characters."
    GroupValidationError.DESCRIPTION_TOO_LONG ->
        "The description can be at most ${GroupValidator.MAX_DESCRIPTION_LENGTH} characters."
}

/** Shown when someone tries to create or join a group while signed out. */
internal const val NOT_SIGNED_IN_MESSAGE = "You need to be signed in to do this."
