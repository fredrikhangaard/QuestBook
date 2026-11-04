package no.ntnu.prog2007.questbook.data.tracking

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import no.ntnu.prog2007.questbook.model.QuestResult

/**
 * Latest durable checkpoint for one assigned quest; restored paused after interruption.
 * questId is the primary key, so one quest has at most one active checkpoint.
 * sessionId joins route_points; target/distance are metres, elapsed time is milliseconds.
 * startedAtMillis is an epoch timestamp and is not used to count paused time.
 */
@Entity(tableName = "tracking_sessions")
data class TrackingSessionEntity(
    @PrimaryKey val questId: String,
    val sessionId: String,
    val userId: String,
    val targetMeters: Double,
    val startedAtMillis: Long,
    val distanceMeters: Double = 0.0,
    val elapsedMillis: Long = 0L
)

/**
 * Completion snapshot with its own readable title/participant name for later history.
 * The ID is the GPS session ID, and the unique questId index prevents duplicate completions.
 * Deleting the active checkpoint does not delete route_points: gpsSessionId keeps that link.
 */
@Entity(tableName = "quest_results", indices = [Index(value = ["questId"], unique = true)])
data class QuestResultEntity(
    @PrimaryKey val id: String,
    val questId: String,
    val groupId: String,
    val userId: String,
    val questTitle: String,
    val participantName: String,
    val completedAtMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Double?,
    val gpsSessionId: String?
) {
    /** Keeps Room annotations out of the domain result used by screens and future Memories code. */
    fun toResult() = QuestResult(id, questId, groupId, userId, questTitle, participantName,
        completedAtMillis, durationMillis, distanceMeters, gpsSessionId)
}
