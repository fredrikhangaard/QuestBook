package no.ntnu.prog2007.questbook.ui.quest.create

import no.ntnu.prog2007.questbook.model.QuestType

data class CreateQuestUiState(
    val type: QuestType = QuestType.DISTANCE,
    val title: String = "",
    val description: String = "",
    val distanceKm: String = "",
    val titleError: String? = null,
    val distanceError: String? = null,
    val saveError: String? = null,
    val isSaved: Boolean = false
)