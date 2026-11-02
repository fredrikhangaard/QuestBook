package no.ntnu.prog2007.questbook.ui.quest

import androidx.lifecycle.ViewModel
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.data.repository.QuestRepository

class QuestsViewModel(
    repository: QuestRepository = QuestDependencies.questRepository
) : ViewModel() {
    val quests = repository.quests
}