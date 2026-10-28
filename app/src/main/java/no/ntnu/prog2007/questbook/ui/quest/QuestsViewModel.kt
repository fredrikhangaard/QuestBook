package no.ntnu.prog2007.questbook.ui.quest

import androidx.lifecycle.ViewModel
import no.ntnu.prog2007.questbook.data.repository.FakeQuestRepository
import no.ntnu.prog2007.questbook.data.repository.QuestRepository

/** Supplies sample quest data until the shared quest repository is connected. */
class QuestsViewModel(
    repository: QuestRepository = FakeQuestRepository()
) : ViewModel() {
    val quests = repository.getQuests()
}
