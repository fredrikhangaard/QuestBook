package no.ntnu.prog2007.questbook.model

/**
 * A challenge belonging to one QuestBook group.
 *
 * User IDs identify people. assignedTo is only the display name.
 * Null assignedUserId means the quest has not been assigned.
 *
 * Timestamps use milliseconds since the Unix epoch.
 */
data class Quest(
    val id: String,
    val groupId: String,
    val type: QuestType,
    val title: String,
    val description: String,
    val assignedTo: String,
    val status: QuestStatus,
    val requiredDistanceKm: Double? = null,
    val createdByUserId: String? = null,
    val assignedUserId: String? = null,
    val createdAtMillis: Long = 0L,
    val startedAtMillis: Long? = null
)

/** Determines the quest's input fields and completion behavior. */
enum class QuestType {
    DISTANCE,
    VISIT_PLACE,
    WORKOUT,
    CUSTOM
}

/** The lifecycle of a quest. */
enum class QuestStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}