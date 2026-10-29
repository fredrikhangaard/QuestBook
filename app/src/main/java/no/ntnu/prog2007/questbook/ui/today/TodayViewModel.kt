package no.ntnu.prog2007.questbook.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.data.repository.QuestRepository

class TodayViewModel(
    private val repository: QuestRepository =
        QuestDependencies.questRepository
) : ViewModel() {

    val uiState = repository.quests
        .map { quests ->
            TodayUiState(quests = quests)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(
                quests = repository.getQuests()
            )
        )
}