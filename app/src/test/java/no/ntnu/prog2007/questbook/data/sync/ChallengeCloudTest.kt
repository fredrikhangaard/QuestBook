package no.ntnu.prog2007.questbook.data.sync

import no.ntnu.prog2007.questbook.data.challenges.GroupChallengeEntity
import no.ntnu.prog2007.questbook.data.challenges.GroupMembershipSpell
import no.ntnu.prog2007.questbook.data.challenges.QuestAttemptEntity
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.personal.ActivityMetric
import no.ntnu.prog2007.questbook.data.personal.QuestActivity
import no.ntnu.prog2007.questbook.data.personal.questActivities
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cloud mapping is pure Kotlin, so it is tested without Firebase or Android. */
class ChallengeCloudTest {

    private val week = 7L * 24 * 60 * 60 * 1000

    private fun quest(id: String, createdBy: String? = "me", assignee: String? = null) = QuestEntity(
        id, "g", "DISTANCE", "Walk 10 km this week", "Together", "Everyone", "IN_PROGRESS",
        10.0, createdBy, assignee, 1_000L, 2_000L)

    private fun weekly(id: String = "group-weekly-1", count: Int = 2) = GroupChallengeEntity(
        id, "g", "WEEKLY", ActivityMetric.WALK_METERS.name, 5000.0 * count, "2026-11-02",
        0L, week, "Europe/Oslo", count, "weekly-WALK_METERS-10000")

    @Test
    fun challengeRoundTripKeepsTheDefinitionAndResetsTheSharedStatus() {
        // Arrange
        val definition = weekly()
        val data = ChallengeCloud.challengeToMap(definition, quest(definition.questId))

        // Act
        val (quest, restored) = ChallengeCloud.challengeFromMap(definition.questId, "g", data)!!

        // Assert
        assertEquals(definition, restored)
        assertEquals("Walk 10 km this week", quest.title)
        assertEquals("NOT_STARTED", quest.status)
        assertNull(quest.startedAtMillis)
    }

    @Test
    fun firestoreWholeNumbersAsLongAreReadCorrectly() {
        // Arrange: Firestore gives back 10000.0 as a Long when it was stored as a whole number.
        val data = ChallengeCloud.challengeToMap(weekly(), quest("group-weekly-1")).toMutableMap()
        data["target"] = 10000L
        data["memberCount"] = 2L

        // Act
        val restored = ChallengeCloud.challengeFromMap("group-weekly-1", "g", data)?.second

        // Assert
        assertEquals(10000.0, restored!!.target, 0.0)
        assertEquals(2, restored.memberCount)
    }

    @Test
    fun brokenOrUnexpectedChallengesAreSkipped() {
        // Arrange
        val good = ChallengeCloud.challengeToMap(weekly(), quest("group-weekly-1"))
        val broken = listOf(
            good + ("kind" to "HACKED"),
            good + ("metric" to "TELEPORT"),
            good + ("target" to -5.0),
            good + ("endMillis" to -1L),
            good + ("zoneId" to "Not/AZone"),
            good + ("assigneeId" to "someone"),   // only a DRAW may have an owner
            good - "title"
        )

        // Act
        val restored = broken.map { ChallengeCloud.challengeFromMap("group-weekly-1", "g", it) }

        // Assert
        assertTrue(restored.all { it == null })
    }

    @Test
    fun drawWithoutOwnerIsSkipped() {
        // Arrange
        val draw = weekly().copy(questId = "group-draw-1", kind = "DRAW", assigneeId = null)
        val data = ChallengeCloud.challengeToMap(draw, quest(draw.questId))

        // Act
        val restored = ChallengeCloud.challengeFromMap(draw.questId, "g", data)

        // Assert
        assertNull(restored)
    }

    @Test
    fun onlyOwnDefinitionsAreUploadedAndLegacyQuestsStayLocal() {
        // Arrange
        val open = weekly().copy(questId = "o", kind = "OPEN", templateId = "custom")
        val legacy = open.copy(templateId = "legacy")
        val draw = open.copy(kind = "DRAW", templateId = "walk-1", assigneeId = "me")

        // Act + Assert
        assertTrue(ChallengeCloud.isUploadable(open, quest("o", createdBy = "me"), "me"))
        assertFalse(ChallengeCloud.isUploadable(open, quest("o", createdBy = "other"), "me"))
        assertFalse(ChallengeCloud.isUploadable(legacy, quest("o", createdBy = "me"), "me"))
        assertTrue(ChallengeCloud.isUploadable(draw, quest("o", createdBy = "me", assignee = "me"), "me"))
        assertFalse(ChallengeCloud.isUploadable(draw.copy(assigneeId = "other"), quest("o"), "me"))
        assertTrue(ChallengeCloud.isUploadable(weekly(), quest("group-weekly-1", createdBy = null), "me"))
    }

    @Test
    fun attemptMustUseItsOneDocumentIdPerChallengeAndMember() {
        // Arrange
        val attempt = QuestAttemptEntity("attempt-x", "c", "me")
        val data = ChallengeCloud.attemptToMap(attempt)

        // Act
        val restored = ChallengeCloud.attemptFromMap(ChallengeCloud.attemptDocId("c", "me"), data)
        val wrongId = ChallengeCloud.attemptFromMap("c_someoneElse", data)

        // Assert
        assertEquals(attempt, restored)
        assertNull(wrongId)
    }

    @Test
    fun completionFromAnotherMemberHasNoRouteAndShowsTheirName() {
        // Arrange
        val result = QuestResultEntity("session-1", "attempt-x", "g", "other", "Walk", "You",
            5_000L, 60_000L, 2_000.0, "session-1")
        val data = ChallengeCloud.completionToMap(result, "c", "Fredrik")

        // Act
        val restored = ChallengeCloud.completionFromMap("session-1", "g", data)!!

        // Assert
        assertNull(restored.gpsSessionId)
        assertEquals("Fredrik", restored.participantName)
        assertEquals(2_000.0, restored.distanceMeters!!, 0.0)
        assertFalse("route and coordinates are never sent", data.keys.any { "route" in it || "lat" in it })
    }

    @Test
    fun remoteWalkCountsOnceEvenWhenItsCompletionIsAlsoDownloaded() {
        // Arrange: the same walk arrives as an activity document and as a completion.
        val walk = QuestActivity("gps:session-1", "attempt-x", 5_000L, 2_000.0, 60_000L)
        val entry = ChallengeCloud.activityFromMap(ChallengeCloud.activityDocId(walk),
            ChallengeCloud.activityToMap(walk, "other"))!!
        val completion = ChallengeCloud.completionFromMap("session-1", "g",
            ChallengeCloud.completionToMap(QuestResultEntity("session-1", "attempt-x", "g", "other", "Walk",
                "You", 5_000L, 60_000L, 2_000.0, "session-1"), "c", "Fredrik"))!!

        // Act
        val events = questActivities(listOf(completion), listOf(entry))

        // Assert
        assertEquals(1, events.size)
        assertEquals(2_000.0, events.single().distanceMeters, 0.0)
        assertEquals("other", entry.userId)
        assertTrue(entry.id.startsWith("cloud:"))
    }

    @Test
    fun activityWithUnknownVisitKindIsSkipped() {
        // Arrange
        val data = ChallengeCloud.activityToMap(QuestActivity("manual:1", "q", 1L, visit = "OUTDOOR"), "other") +
            ("visit" to "MOON")

        // Act
        val restored = ChallengeCloud.activityFromMap("manual:1", data)

        // Assert
        assertNull(restored)
    }

    @Test
    fun cloudJoinTimeReplacesTheFirstLocalSpellOnly() {
        // Arrange
        val first = GroupMembershipSpell("s1", "g", "other", 500L)
        val closed = GroupMembershipSpell("s0", "g", "other", 100L, 200L)

        // Act
        val moved = ChallengeCloud.adoptSpell(listOf(first), "g", "other", 300L)
        val created = ChallengeCloud.adoptSpell(emptyList(), "g", "other", 300L)
        val sameTime = ChallengeCloud.adoptSpell(listOf(first.copy(joinedAtMillis = 300L)), "g", "other", 300L)
        val afterRejoin = ChallengeCloud.adoptSpell(listOf(closed, first), "g", "other", 300L)

        // Assert
        assertEquals(first.copy(joinedAtMillis = 300L), moved)
        assertEquals(300L, created!!.joinedAtMillis)
        assertNull(sameTime)
        assertNull(afterRejoin)
    }
}
