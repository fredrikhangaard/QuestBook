package no.ntnu.prog2007.questbook.data.tracking

import no.ntnu.prog2007.questbook.location.GpsTrackingUiState
import no.ntnu.prog2007.questbook.location.database.RoutePointEntity
import no.ntnu.prog2007.questbook.model.QuestResult

/** Operations used by the GPS coordinator, independently of Room and Android UI. */
interface QuestTrackingStore {
    /** Returns the latest checkpoint plus its stored route, or null if no active session exists. */
    suspend fun load(questId: String, userId: String): GpsTrackingUiState?
    /** Returns only this user's saved completion for the selected quest. */
    suspend fun result(questId: String, userId: String): QuestResult?
    /** Creates a session and marks the assigned quest IN_PROGRESS, or resumes its existing session. */
    suspend fun begin(questId: String, userId: String, nowMillis: Long): GpsTrackingUiState
    /** Saves the optional point and its summary atomically so route and progress cannot diverge. */
    suspend fun checkpoint(point: RoutePointEntity?, state: GpsTrackingUiState)
    /** Saves one result and marks the quest COMPLETED after all GPS writes have drained. */
    suspend fun finish(questId: String, userId: String, sessionId: String, nowMillis: Long): QuestResult
}
