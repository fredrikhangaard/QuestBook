package no.ntnu.prog2007.questbook.model

/**
 * A private QuestBook group of friends.
 *
 * @property id the Firestore document id.
 * @property name the group's name, shown on Home and in Groups.
 * @property description optional text about the group (may be empty).
 * @property inviteCode the code friends type to join the group.
 * @property ownerId uid of the user who created the group.
 * @property memberIds uids of everyone in the group, including the owner.
 */
data class Group(
    val id: String,
    val name: String,
    val description: String,
    val inviteCode: String,
    val ownerId: String,
    val memberIds: List<String>
)
