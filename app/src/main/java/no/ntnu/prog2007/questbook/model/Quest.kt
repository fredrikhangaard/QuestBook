package no.ntnu.prog2007.questbook.model

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val assignedTo: String,
    val status: QuestStatus,
    val requiredDistanceKm: Double? = null
)

enum class QuestStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}