package no.ntnu.prog2007.questbook.data.repository

object QuestDependencies {
    val questRepository: QuestRepository = FakeQuestRepository()
}