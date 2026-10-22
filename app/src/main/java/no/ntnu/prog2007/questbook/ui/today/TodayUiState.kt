package no.ntnu.prog2007.questbook.ui.today


import no.ntnu.prog2007.questbook.model.Quest

data class TodayUiState(
    val quests: List<Quest> = emptyList(),
    val isLoading: Boolean = false
)
