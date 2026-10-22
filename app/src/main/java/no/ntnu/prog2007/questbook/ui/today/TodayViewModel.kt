package no.ntnu.prog2007.questbook.ui.today

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.ntnu.prog2007.questbook.data.repository.FakeQuestRepository
import no.ntnu.prog2007.questbook.data.repository.QuestRepository

class TodayViewModel(
    private val repository: QuestRepository = FakeQuestRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TodayUiState(
            quests = repository.getQuests()
        )
    )

    val uiState = _uiState.asStateFlow()
}
