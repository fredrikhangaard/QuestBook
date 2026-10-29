package no.ntnu.prog2007.questbook.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.model.QuestType

class FakeQuestRepository : QuestRepository {

    private val initialQuests = listOf(
        Quest(
            id = "1",
            groupId = "mountain-crew",
            type = QuestType.DISTANCE,
            title = "Walk 5 km",
            description = "Complete a 5 km walk outdoors",
            assignedTo = "Emma",
            status = QuestStatus.NOT_STARTED,
            requiredDistanceKm = 5.0
        ),
        Quest(
            id = "2",
            groupId = "mountain-crew",
            type = QuestType.VISIT_PLACE,
            title = "Visit a new place",
            description = "Explore somewhere new",
            assignedTo = "Jonas",
            status = QuestStatus.NOT_STARTED
        ),
        Quest(
            id = "3",
            groupId = "mountain-crew",
            type = QuestType.WORKOUT,
            title = "Outdoor workout",
            description = "Complete a workout outside",
            assignedTo = "Sara",
            status = QuestStatus.COMPLETED
        )
    )

    private val _quests = MutableStateFlow(initialQuests)
    override val quests = _quests.asStateFlow()

    override fun getQuests(): List<Quest> = quests.value

    override fun getQuest(id: String): Quest? =
        quests.value.find { it.id == id }

    override fun addQuest(quest: Quest) {
        _quests.update { current ->
            require(current.none { it.id == quest.id }) {
                "A quest with this ID already exists"
            }
            current + quest
        }
    }
}