package no.ntnu.prog2007.questbook.model

/**
 * A QuestBook user.
 *
 * @property uid the Firebase Authentication user id. Use this everywhere a user is
 * referenced, e.g. group `memberIds` and a quest's `assignedUserId` / `createdBy`.
 * @property username the name shown to other users.
 * @property email the email address the user signs in with.
 */
data class User(
    val uid: String,
    val username: String,
    val email: String
)
