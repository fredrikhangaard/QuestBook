package no.ntnu.prog2007.questbook.data.repository

import kotlinx.coroutines.flow.StateFlow
import no.ntnu.prog2007.questbook.model.Quest

/** Supplies observable quest data and persists new quests. */
interface QuestRepository {
    val quests: StateFlow<List<Quest>>

    /** Returns the latest observed list; initially empty while loading. */
    fun getQuests(): List<Quest>

    /** Looks up a quest in the latest observed list. */
    fun getQuest(id: String): Quest?

    /** Returns only after the quest has been saved, or throws on failure. */
    suspend fun addQuest(quest: Quest)
    /**
     * Assigns a not-started quest to a member's real user ID and display-name snapshot.
     * The caller validates current group membership; storage rejects a concurrent start.
     * A Firestore implementation can later provide the same operation.
     */
    suspend fun assignQuest(questId: String, groupId: String, userId: String, displayName: String)
}