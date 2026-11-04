package no.ntnu.prog2007.questbook.data.groups

/** Why joining a group failed. */
enum class GroupError {
    /** The code does not have the right format. */
    INVALID_CODE,

    /** No group has this code. */
    CODE_NOT_FOUND,

    /** The user is already a member of the group with this code. */
    ALREADY_MEMBER,

    /** No internet connection. Joining needs to check the code with the server. */
    NETWORK,

    /** Anything else. */
    UNKNOWN
}

/** The result of trying to join a group. */
sealed interface JoinOutcome {

    /** The user is now a member of the group [groupId]. */
    data class Success(val groupId: String) : JoinOutcome

    /** Joining failed because of [error]. */
    data class Failure(val error: GroupError) : JoinOutcome
}
