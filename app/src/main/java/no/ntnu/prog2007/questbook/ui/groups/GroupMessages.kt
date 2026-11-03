package no.ntnu.prog2007.questbook.ui.groups

import no.ntnu.prog2007.questbook.data.groups.GroupError
import no.ntnu.prog2007.questbook.data.groups.GroupValidationError
import no.ntnu.prog2007.questbook.data.groups.GroupValidator
import no.ntnu.prog2007.questbook.data.groups.InviteCode

/** The message shown when the group details are not valid. */
internal fun GroupValidationError.toMessage(): String = when (this) {
    GroupValidationError.BLANK_NAME -> "Give the group a name."
    GroupValidationError.NAME_TOO_LONG ->
        "The name can be at most ${GroupValidator.MAX_NAME_LENGTH} characters."
    GroupValidationError.DESCRIPTION_TOO_LONG ->
        "The description can be at most ${GroupValidator.MAX_DESCRIPTION_LENGTH} characters."
}

/** The message shown when joining a group fails. */
internal fun GroupError.toMessage(): String = when (this) {
    GroupError.INVALID_CODE -> "An invite code has ${InviteCode.LENGTH} letters and numbers."
    GroupError.CODE_NOT_FOUND -> "No group has that code. Check it and try again."
    GroupError.NETWORK -> "No internet connection. You need to be online to join a group."
    GroupError.UNKNOWN -> "Something went wrong. Please try again."
}

/** Shown when someone tries to create or join a group while signed out. */
internal const val NOT_SIGNED_IN_MESSAGE = "You need to be signed in to do this."
