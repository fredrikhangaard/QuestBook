package no.ntnu.prog2007.questbook.model

data class Quest(
    val id: String,
    val groupId: String,
    val type: QuestType,
    val title: String,
    val description: String,
    val assignedTo: String,
    val status: QuestStatus,
    val requiredDistanceKm: Double? = null
)

enum class QuestType {
    DISTANCE,
    VISIT_PLACE,
    WORKOUT,
    CUSTOM
}

enum class QuestStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}