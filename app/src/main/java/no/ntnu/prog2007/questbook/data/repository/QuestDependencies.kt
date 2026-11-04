package no.ntnu.prog2007.questbook.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase

/** Holds the quest repository shared across the application. */
object QuestDependencies {

    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repository: QuestRepository? = null

    val questRepository: QuestRepository
        get() = checkNotNull(repository) {
            "Call QuestDependencies.initialize(context) before using quests."
        }

    /** Initializes local storage once, using only the application context. */
    @Synchronized
    fun initialize(context: Context) {
        if (repository != null) return

        val database = QuestBookDatabase.getDatabase(
            context.applicationContext
        )

        repository = RoomQuestRepository(
            dao = database.questDao(),
            scope = applicationScope
        )
    }
}