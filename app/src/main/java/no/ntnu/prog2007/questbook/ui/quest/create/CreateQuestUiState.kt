package no.ntnu.prog2007.questbook.ui.quest.create

import no.ntnu.prog2007.questbook.model.QuestType

/** Form values, validation errors and save status. */
data class CreateQuestUiState(
    val type: QuestType = QuestType.DISTANCE,
    val title: String = "",
    val description: String = "",
    // Legacy field name: distance in km for walking, whole repetitions for Workout.
    val distanceKm: String = "",
    val titleError: String? = null,
    val distanceError: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
)