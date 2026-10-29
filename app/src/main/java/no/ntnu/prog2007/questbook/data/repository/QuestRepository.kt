package no.ntnu.prog2007.questbook.data.repository

import kotlinx.coroutines.flow.StateFlow
import no.ntnu.prog2007.questbook.model.Quest

interface QuestRepository {
    val quests: StateFlow<List<Quest>>

    fun getQuests(): List<Quest>
    fun getQuest(id: String): Quest?
    fun addQuest(quest: Quest)
}