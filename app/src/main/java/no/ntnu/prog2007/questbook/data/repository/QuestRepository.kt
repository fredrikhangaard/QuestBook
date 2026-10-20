package no.ntnu.prog2007.questbook.data.repository


import no.ntnu.prog2007.questbook.model.Quest

interface QuestRepository {
    fun getQuests(): List<Quest>
    fun getQuest(id: String): Quest?
}
