package no.ntnu.prog2007.questbook.data.repository


import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus

class FakeQuestRepository : QuestRepository {

    private val quests = listOf(
        Quest(
            id = "1",
            title = "Walk 5 km",
            description = "Complete a 5 km walk outdoors",
            assignedTo = "Emma",
            status = QuestStatus.NOT_STARTED,
            requiredDistanceKm = 5.0
        ),
        Quest(
            id = "2",
            title = "Visit a new place",
            description = "Explore somewhere new",
            assignedTo = "Jonas",
            status = QuestStatus.NOT_STARTED
        ),
        Quest(
            id = "3",
            title = "Outdoor workout",
            description = "Complete a workout outside",
            assignedTo = "Sara",
            status = QuestStatus.COMPLETED
        )
    )

    override fun getQuests(): List<Quest> = quests

    override fun getQuest(id: String): Quest? =
        quests.find { it.id == id }
}

