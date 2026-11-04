package no.ntnu.prog2007.questbook.model

/**
 * A saved completion of a quest.
 *
 * The title and participant name are snapshots so history remains readable
 * if a quest or profile changes later.
 *
 * Distance is in metres, duration in milliseconds, and timestamps are
 * milliseconds since the Unix epoch.
 *
 * A GPS session ID connects this result to locally recorded route points.
 * Photo records will reference this result's ID.
 */
data class QuestResult(
    val id: String,
    val questId: String,
    val groupId: String,
    val userId: String,
    val questTitle: String,
    val participantName: String,
    val completedAtMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Double? = null,
    val gpsSessionId: String? = null
)