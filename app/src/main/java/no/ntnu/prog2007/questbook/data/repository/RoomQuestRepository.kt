package no.ntnu.prog2007.questbook.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import no.ntnu.prog2007.questbook.data.local.QuestDao
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.model.Quest

/**
 * Persists quests in Room and observes database changes.
 *
 * The supplied scope belongs to the application, so observation continues
 * while users move between screens.
 */
class RoomQuestRepository(
    private val dao: QuestDao,
    scope: CoroutineScope
) : QuestRepository {

    override val quests = dao.observeQuests()
        .map { rows -> rows.map { it.toQuest() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    override fun getQuests(): List<Quest> = quests.value

    override fun getQuest(id: String): Quest? =
        quests.value.find { it.id == id }

    override suspend fun addQuest(quest: Quest) {
        dao.insertQuest(QuestEntity.fromQuest(quest))
    }
}