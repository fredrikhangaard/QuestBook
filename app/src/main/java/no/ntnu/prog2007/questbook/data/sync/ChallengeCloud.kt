package no.ntnu.prog2007.questbook.data.sync

import java.time.ZoneId
import no.ntnu.prog2007.questbook.data.challenges.GroupChallengeEntity
import no.ntnu.prog2007.questbook.data.challenges.GroupMembershipSpell
import no.ntnu.prog2007.questbook.data.challenges.QuestAttemptEntity
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.personal.ActivityEntryEntity
import no.ntnu.prog2007.questbook.data.personal.ActivityMetric
import no.ntnu.prog2007.questbook.data.personal.QuestActivity
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.model.QuestType

/**
 * Converts between Room rows and the Firestore documents under `groups/{groupId}/...`.
 *
 * Cloud layout (one subcollection per kind of record):
 * - `challenges/{questId}`: a shared quest definition (OPEN, DRAW or WEEKLY) plus its card text.
 * - `attempts/{challengeId}_{userId}`: one member's own attempt at a challenge.
 * - `completions/{resultId}`: a finished attempt, without the GPS route.
 * - `activity/{eventId}`: one activity event (amounts and a time only), used for weekly group goals.
 * - `spells/{userId}`: when the member joined, stamped with the server's clock.
 *
 * This file has no Firebase types, so it can be unit tested on the JVM. Every `...FromMap`
 * function returns null for a document it does not trust, so a broken document is skipped
 * instead of crashing the app.
 */
object ChallengeCloud {
    const val CHALLENGES = "challenges"
    const val ATTEMPTS = "attempts"
    const val COMPLETIONS = "completions"
    const val ACTIVITY = "activity"
    const val SPELLS = "spells"

    /** Spells come first, so a member's join time is known before their activity is uploaded. */
    val COLLECTIONS = listOf(SPELLS, CHALLENGES, ATTEMPTS, COMPLETIONS, ACTIVITY)

    /** Only activity this new is shared and downloaded. Older weeks keep what is already in Room. */
    const val ACTIVITY_WINDOW_MILLIS = 56L * 24 * 60 * 60 * 1000

    private val KINDS = setOf("OPEN", "DRAW", "WEEKLY")
    private val VISITS = setOf("WATER", "PARK", "OUTDOOR", "CONFIRMED")

    /** The attempt's document ID. One ID per challenge and member, so nobody can have two attempts. */
    fun attemptDocId(challengeId: String, userId: String) = "${challengeId}_$userId"

    /**
     * True when this account is allowed to share [definition] (firestore.rules checks the same).
     * Legacy quests from before groups are skipped, because they may have been made by someone else.
     */
    fun isUploadable(definition: GroupChallengeEntity, quest: QuestEntity, uid: String): Boolean =
        when (definition.kind) {
            "OPEN" -> definition.templateId == "custom" && quest.createdByUserId == uid
            "DRAW" -> definition.assigneeId == uid && quest.createdByUserId == uid
            "WEEKLY" -> true
            else -> false
        }

    // ---------- challenges ----------

    fun challengeToMap(definition: GroupChallengeEntity, quest: QuestEntity): Map<String, Any?> = mapOf(
        "kind" to definition.kind,
        "metric" to definition.metric,
        "target" to definition.target,
        "periodKey" to definition.periodKey,
        "startMillis" to definition.startMillis,
        "endMillis" to definition.endMillis,
        "zoneId" to definition.zoneId,
        "memberCount" to definition.memberCount,
        "templateId" to definition.templateId,
        "assigneeId" to definition.assigneeId,
        "title" to quest.title,
        "description" to quest.description,
        "type" to quest.type,
        "assignedTo" to quest.assignedTo,
        "requiredDistanceKm" to quest.requiredDistanceKm,
        "createdByUserId" to quest.createdByUserId,
        "createdAtMillis" to quest.createdAtMillis
    )

    /** Rebuilds the quest card and its definition. The shared quest always starts as NOT_STARTED. */
    fun challengeFromMap(id: String, groupId: String, data: Map<String, Any?>): Pair<QuestEntity, GroupChallengeEntity>? {
        val kind = data.text("kind")?.takeIf { it in KINDS } ?: return null
        val metric = data.text("metric")?.takeIf { name -> ActivityMetric.entries.any { it.name == name } } ?: return null
        val target = data.number("target")?.takeIf { it.isFinite() && it > 0 } ?: return null
        val start = data.long("startMillis") ?: return null
        val end = data.long("endMillis")?.takeIf { it > start } ?: return null
        val zone = data.text("zoneId")?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: return null
        val memberCount = data.long("memberCount")?.toInt()?.takeIf { it >= 1 } ?: return null
        val templateId = data.text("templateId") ?: return null
        val title = data.text("title")?.takeIf { it.isNotBlank() } ?: return null
        val type = data.text("type")?.takeIf { name -> QuestType.entries.any { it.name == name } } ?: return null
        val assignee = data.text("assigneeId")
        // A DRAW always has an owner, the others never have one.
        if ((kind == "DRAW") != (assignee != null)) return null

        val definition = GroupChallengeEntity(id, groupId, kind, metric, target, data.text("periodKey") ?: "",
            start, end, zone, memberCount, templateId, assignee)
        val quest = QuestEntity(
            id = id,
            groupId = groupId,
            type = type,
            title = title,
            description = data.text("description") ?: "",
            assignedTo = data.text("assignedTo") ?: "Everyone",
            status = QuestStatus.NOT_STARTED.name,
            requiredDistanceKm = data.number("requiredDistanceKm"),
            createdByUserId = data.text("createdByUserId"),
            assignedUserId = assignee,
            createdAtMillis = data.long("createdAtMillis") ?: start,
            startedAtMillis = null
        )
        return quest to definition
    }

    // ---------- attempts ----------

    fun attemptToMap(attempt: QuestAttemptEntity): Map<String, Any?> = mapOf(
        "attemptId" to attempt.attemptId,
        "challengeId" to attempt.challengeId,
        "userId" to attempt.userId
    )

    fun attemptFromMap(docId: String, data: Map<String, Any?>): QuestAttemptEntity? {
        val attemptId = data.text("attemptId")?.takeIf { it.isNotBlank() } ?: return null
        val challengeId = data.text("challengeId") ?: return null
        val userId = data.text("userId") ?: return null
        if (docId != attemptDocId(challengeId, userId)) return null
        return QuestAttemptEntity(attemptId, challengeId, userId)
    }

    // ---------- completions ----------

    /** [name] replaces the local "You", so other members see who finished. The route is never sent. */
    fun completionToMap(result: QuestResultEntity, challengeId: String, name: String): Map<String, Any?> = mapOf(
        "questId" to result.questId,
        "challengeId" to challengeId,
        "userId" to result.userId,
        "questTitle" to result.questTitle,
        "participantName" to name,
        "completedAtMillis" to result.completedAtMillis,
        "durationMillis" to result.durationMillis,
        "distanceMeters" to result.distanceMeters
    )

    /**
     * Another member's completion. `gpsSessionId` is always null: the route stays on their phone,
     * and the walk is already counted once through its `activity` document.
     */
    fun completionFromMap(docId: String, groupId: String, data: Map<String, Any?>): QuestResultEntity? {
        val questId = data.text("questId") ?: return null
        val userId = data.text("userId") ?: return null
        val completedAt = data.long("completedAtMillis") ?: return null
        return QuestResultEntity(
            id = docId,
            questId = questId,
            groupId = groupId,
            userId = userId,
            questTitle = data.text("questTitle") ?: "Quest",
            participantName = data.text("participantName") ?: "Member",
            completedAtMillis = completedAt,
            durationMillis = (data.long("durationMillis") ?: 0L).coerceAtLeast(0L),
            distanceMeters = data.number("distanceMeters")?.takeIf { it.isFinite() && it >= 0 },
            gpsSessionId = null
        )
    }

    // ---------- activity ----------

    /** The event ID ("gps:..." or "manual:...") is unique, so the same walk is never uploaded twice. */
    fun activityDocId(event: QuestActivity) = event.id

    fun activityToMap(event: QuestActivity, uid: String): Map<String, Any?> = mapOf(
        "userId" to uid,
        "at" to event.at,
        "distanceMeters" to event.distanceMeters,
        "activeMillis" to event.activeMillis,
        "pushups" to event.pushups,
        "squats" to event.squats,
        "visit" to event.visit,
        "bodyweightReps" to event.bodyweightReps
    )

    /**
     * Another member's event as a local activity row. The "cloud:" prefix keeps it apart from
     * this phone's own rows, and personal quests never see it (they only read their own userId).
     */
    fun activityFromMap(docId: String, data: Map<String, Any?>): ActivityEntryEntity? {
        val userId = data.text("userId") ?: return null
        val at = data.long("at") ?: return null
        val visit = data.text("visit")
        if (visit != null && visit !in VISITS) return null
        val id = "cloud:$docId"
        return ActivityEntryEntity(
            id = id,
            userId = userId,
            questId = id,
            occurredAtMillis = at,
            distanceMeters = data.number("distanceMeters")?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
            activeMillis = (data.long("activeMillis") ?: 0L).coerceAtLeast(0L),
            pushups = data.count("pushups"),
            squats = data.count("squats"),
            visitKind = visit,
            bodyweightReps = data.count("bodyweightReps")
        )
    }

    // ---------- membership spells ----------

    /**
     * Returns the local spell to save so this phone uses the cloud's join time, or null if nothing
     * changes. Only a member's first, still open spell is moved. Leave/rejoin gaps stay local.
     */
    fun adoptSpell(local: List<GroupMembershipSpell>, groupId: String, userId: String, joinedAt: Long): GroupMembershipSpell? {
        val own = local.filter { it.groupId == groupId && it.userId == userId }
        return when {
            own.isEmpty() -> GroupMembershipSpell("cloud:$groupId:$userId", groupId, userId, joinedAt)
            own.size == 1 && own[0].leftAtMillis == null && own[0].joinedAtMillis != joinedAt ->
                own[0].copy(joinedAtMillis = joinedAt)
            else -> null
        }
    }

    // ---------- helpers ----------
    // Firestore returns whole numbers as Long and decimals as Double, so read both as Number.

    private fun Map<String, Any?>.text(key: String) = this[key] as? String
    private fun Map<String, Any?>.number(key: String) = (this[key] as? Number)?.toDouble()
    private fun Map<String, Any?>.long(key: String) = (this[key] as? Number)?.toLong()
    private fun Map<String, Any?>.count(key: String) = (long(key) ?: 0L).coerceIn(0L, 1000L).toInt()
}
