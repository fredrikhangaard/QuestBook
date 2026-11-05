package no.ntnu.prog2007.questbook.ui.quest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.data.repository.QuestRepository

class QuestsViewModel(
    repository: QuestRepository = QuestDependencies.questRepository
) : ViewModel() {
    // Personal assignments have their own account-scoped section, never the shared group list.
    val quests = repository.quests.map { list -> list.filterNot {
        no.ntnu.prog2007.questbook.data.personal.PersonalQuestIds.isPersonalGroup(it.groupId)
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}