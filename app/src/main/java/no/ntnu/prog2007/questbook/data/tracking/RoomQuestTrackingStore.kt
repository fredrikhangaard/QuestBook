package no.ntnu.prog2007.questbook.data.tracking

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.first
import no.ntnu.prog2007.questbook.location.GpsTrackingUiState
import no.ntnu.prog2007.questbook.location.RoutePoint
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.location.database.RoutePointEntity
import no.ntnu.prog2007.questbook.model.*

/** Stores each route sample and its checkpoint atomically, and finalizes a quest once. */
class RoomQuestTrackingStore(private val database: QuestBookDatabase) : QuestTrackingStore {
    private val tracking = database.trackingDao()

    // Read the summary and route in one transaction, so they describe the same checkpoint.
    override suspend fun load(questId: String, userId: String): GpsTrackingUiState? = database.withTransaction {
        val session = tracking.getSession(questId) ?: return@withTransaction null
        check(session.userId == userId) { "This session belongs to another user." }
        // first() reads one snapshot; no permanent Flow collector is kept for a completed load.
        val points = database.routePointDao().getPointsForSession(session.sessionId).first().map {
            RoutePoint(it.latitude, it.longitude, it.timestamp, it.accuracy)
        }
        GpsTrackingUiState(sessionId = session.sessionId, requiredDistanceMeters = session.targetMeters,
            distanceMeters = session.distanceMeters, elapsedMillis = session.elapsedMillis,
            routePoints = points, pointCount = points.size, hasLocation = points.isNotEmpty(),
            latitude = points.lastOrNull()?.latitude ?: 0.0, longitude = points.lastOrNull()?.longitude ?: 0.0,
            accuracy = points.lastOrNull()?.accuracy ?: 0f)
    }

    override suspend fun result(questId: String, userId: String): QuestResult? =
        tracking.getResult(questId)?.takeIf { it.userId == userId }?.toResult()

    override suspend fun begin(questId: String, userId: String, nowMillis: Long): GpsTrackingUiState {
        database.withTransaction {
            val row = checkNotNull(database.questDao().getQuest(questId)) { "Quest not found." }
            val quest = row.toQuest()
            validateTrackingQuest(quest, userId)
            // Reuse the session ID on resume, rather than splitting one quest into multiple routes.
            val existing = tracking.getSession(questId)
            if (existing != null) {
                check(existing.userId == userId) { "This session belongs to another user." }
                return@withTransaction
            }
            check(quest.status == QuestStatus.NOT_STARTED) { "No saved GPS session exists for this active quest." }
            val session = TrackingSessionEntity(questId, UUID.randomUUID().toString(), userId,
                quest.requiredDistanceKm!! * 1000.0, nowMillis)
            // Either both writes succeed or neither does; the quest cannot start without a session.
            tracking.insertSession(session)
            check(database.questDao().updateQuest(row.copy(status = QuestStatus.IN_PROGRESS.name,
                startedAtMillis = nowMillis)) == 1)
        }
        return checkNotNull(load(questId, userId))
    }

    override suspend fun checkpoint(point: RoutePointEntity?, state: GpsTrackingUiState) {
        database.withTransaction {
            // If updating the checkpoint fails, Room rolls back the point insert too.
            if (point != null) database.routePointDao().insertPoint(point)
            check(tracking.checkpoint(state.sessionId, state.distanceMeters, state.elapsedMillis) == 1) {
                "The tracking session is no longer available."
            }
        }
    }

    override suspend fun finish(questId: String, userId: String, sessionId: String, nowMillis: Long): QuestResult =
        database.withTransaction {
            // Saving again after a lost callback returns the same result rather than inserting twice.
            tracking.getResult(questId)?.let {
                check(it.userId == userId && it.gpsSessionId == sessionId)
                return@withTransaction it.toResult()
            }
            val row = checkNotNull(database.questDao().getQuest(questId))
            val quest = row.toQuest()
            validateTrackingQuest(quest, userId)
            val session = checkNotNull(tracking.getSession(questId))
            check(session.userId == userId && session.sessionId == sessionId)
            // Completion is checked against durable distance, not just a value displayed in Compose.
            check(session.distanceMeters >= session.targetMeters) { "Reach the distance target before finishing." }
            val result = QuestResultEntity(sessionId, questId, quest.groupId, userId, quest.title, quest.assignedTo,
                nowMillis, session.elapsedMillis, session.distanceMeters, sessionId)
            // Result, quest status, and checkpoint removal form one indivisible completion.
            tracking.insertResult(result)
            check(database.questDao().updateQuest(row.copy(status = QuestStatus.COMPLETED.name)) == 1)
            tracking.deleteSession(questId)
            result.toResult()
        }
}

/** Shared start/finish validation; unassigned and non-GPS quests never start location updates. */
fun validateTrackingQuest(quest: Quest, userId: String) {
    require(userId.isNotBlank()) { "Sign in before tracking." }
    require(quest.assignedUserId == userId) { "This quest must be assigned to you before tracking." }
    require(quest.type == QuestType.DISTANCE) { "This quest does not use GPS tracking." }
    require(quest.status != QuestStatus.COMPLETED) { "This quest is already completed." }
    val target = quest.requiredDistanceKm
    require(target != null && target.isFinite() && target > 0 && (target * 1000).isFinite()) { "Invalid distance target." }
}
