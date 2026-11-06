package no.ntnu.prog2007.questbook

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*
import kotlin.random.Random
import no.ntnu.prog2007.questbook.data.challenges.*
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.data.photo.LocalMemoryPhotoStore
import no.ntnu.prog2007.questbook.data.tracking.*
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.*
import org.junit.Assert.*
import org.junit.Test

/** Two-account local integration: shared definitions, separate progress, result-linked photos and eligibility. */
class GroupChallengeStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Europe/Oslo")
    private val now = ZonedDateTime.of(2026, 11, 6, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val group = Group("adventures", "Adventurers", "", "ABCD12", "alice", listOf("alice", "bob"))
    private fun db() = Room.inMemoryDatabaseBuilder(context, QuestBookDatabase::class.java).build()
    private fun quest(id: String, type: QuestType = QuestType.WORKOUT) = Quest(id, group.id, type, "Rep rally", "Optional photo", "", QuestStatus.NOT_STARTED,
        requiredDistanceKm = if (type == QuestType.DISTANCE) 0.005 else null, createdByUserId = "alice", createdAtMillis = now)
    private suspend fun denied(block: suspend () -> Unit) {
        try { block(); fail("Should reject invalid action") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    @Test fun oneWeeklyGoalFreezesMemberCountAndNewWeekKeepsHistory() = runBlocking {
        val db = db()
        try {
            val store = GroupChallengeStore(db)
            val concurrent = coroutineScope { List(3) { async { store.refresh("alice", listOf(group), now, zone) } }.awaitAll() }
            val first = concurrent.first().challenges.single { it.kind == "WEEKLY" }
            assertTrue(concurrent.all { snapshot -> snapshot.challenges.count { it.kind == "WEEKLY" } == 1 })
            val perMember = when(ActivityMetric.valueOf(first.metric)) { ActivityMetric.WALK_METERS -> 5000.0; ActivityMetric.BODYWEIGHT_REPS -> 50.0; else -> 1.0 }
            assertEquals(perMember * 2, first.target, 0.0)
            val larger = group.copy(memberIds = group.memberIds + listOf("charlie", "dana"))
            assertEquals(first, store.refresh("alice", listOf(larger), now + 1000, zone).challenges.single { it.kind == "WEEKLY" })
            val later = store.refresh("alice", listOf(larger), now + 7 * 86400000, zone).challenges.filter { it.kind == "WEEKLY" }
            assertEquals(2, later.size)
            assertEquals(4, later.maxBy { it.startMillis }.memberCount)
            assertEquals(2, later.minBy { it.startMillis }.memberCount)
        } finally { db.close() }
    }
    @Test fun groupCalendarDoesNotCreateASecondGoalWhenPhoneTimezoneChangesNearMonday() = runBlocking {
        val db = db()
        try {
            val store = GroupChallengeStore(db)
            val monday = questWindow(now, zone, QuestCadence.WEEKLY).start + 1800000
            val initial = store.refresh("alice", listOf(group), monday, zone).challenges.single()
            val changed = store.refresh("alice", listOf(group), monday, ZoneId.of("America/Los_Angeles")).challenges
            assertEquals(listOf(initial), changed)
        } finally { db.close() }
    }

    @Test fun getQuestIsOwnedByDrawerHasWeeklyDeadlineAndRetryDoesNotDrawTwice() = runBlocking {
        val db = db()
        try {
            val store = GroupChallengeStore(db, Random(7))
            store.refresh("alice", listOf(group), now, zone)
            val id = store.draw("alice", group, "Alice", "draw", now, zone)
            assertEquals(id, store.draw("alice", group, "Alice", "draw", now, zone))
            val row = db.groupChallengeDao().challenge(id)!!
            assertEquals("alice", row.assigneeId)
            assertEquals(questWindow(now, zone, QuestCadence.WEEKLY).end, row.endMillis)
            assertNotNull(db.groupChallengeDao().attempt(id, "alice"))
            denied { store.open("bob", group, id, "Bob", now) }
            assertEquals(1, db.groupChallengeDao().challenges().count { it.kind == "DRAW" })
        } finally { db.close() }
    }
    @Test fun everyMemberCanCompleteCustomQuestWithOwnResultAndOptionalPhoto() = runBlocking {
        val db = db()
        try {
            val store = GroupChallengeStore(db)
            store.refresh("alice", listOf(group), now - 1, zone)
            store.create("alice", group, quest("custom"), ActivityMetric.BODYWEIGHT_REPS, 20.0, now)
            val a = store.open("alice", group, "custom", "Alice", now)
            val b = store.open("bob", group, "custom", "Bob", now)
            assertNotEquals(a, b)
            val personal = PersonalQuestStore(db)
            personal.logManual("alice", a, 20.0, 0.0, "a-set", now)
            assertNotNull(db.trackingDao().getResult(a))
            assertNull(db.trackingDao().getResult(b))
            assertEquals(QuestStatus.NOT_STARTED.name, db.questDao().getQuest("custom")!!.status)
            LocalMemoryPhotoStore(db) { "alice" }.save(a, byteArrayOf(1, 2), "My optional photo")
            val bobView = store.snapshot("bob", listOf(group))
            assertEquals(1, bobView.results.size)
            assertEquals(1, bobView.photos.size)
            personal.logManual("bob", b, 10.0, 0.0, "b-set", now)
            assertNull(db.trackingDao().getResult(b))
            personal.logManual("bob", b, 10.0, 0.0, "b-set2", now + 1)
            assertEquals(2, store.snapshot("alice", listOf(group)).results.size)
            assertTrue(store.snapshot("outsider", listOf(group)).results.isEmpty())
            denied { store.open("outsider", group, "custom", "Unknown", now) }
            denied { LocalMemoryPhotoStore(db) { "bob" }.save(a, byteArrayOf(1), "No") }
        } finally { db.close() }
    }
    @Test fun independentGpsSessionsFinishWithoutLockingOtherMembersOut() = runBlocking {
        val db = db()
        try {
            val store = GroupChallengeStore(db)
            store.create("alice", group, quest("walk", QuestType.DISTANCE), ActivityMetric.WALK_METERS, 5.0, now)
            val a = store.open("alice", group, "walk", "Alice", now)
            val b = store.open("bob", group, "walk", "Bob", now)
            val gps = RoomQuestTrackingStore(db)
            val first = gps.begin(a, "alice", now)
            val second = gps.begin(b, "bob", now)
            assertNotEquals(first.sessionId, second.sessionId)
            gps.checkpoint(null, first.copy(distanceMeters = 5.0))
            gps.finish(a, "alice", first.sessionId, now + 1000)
            assertNotNull(gps.load(b, "bob"))
            assertNull(gps.result(b, "bob"))
            assertEquals(QuestStatus.NOT_STARTED.name, db.questDao().getQuest("walk")!!.status)
        } finally { db.close() }
    }
    @Test fun membershipExcludesOldActivityKeepsPastContributionsAndExcludesLeaveGap() = runBlocking {
        val db = db()
        try {
            val store = GroupChallengeStore(db)
            val snapshot = store.refresh("alice", listOf(group), now, zone)
            val goal = snapshot.challenges.single()
            fun event(id: String, at: Long) = ActivityEntryEntity(id, "bob", "activity", at, distanceMeters = 1000.0,
                bodyweightReps = 10, visitKind = "OUTDOOR")
            db.personalQuestDao().insertEntry(event("before", now - 1))
            db.personalQuestDao().insertEntry(event("member", now + 1))
            val expected = when(goal.metric) { "WALK_METERS" -> 1000.0; "BODYWEIGHT_REPS" -> 10.0; else -> 1.0 }
            assertEquals(expected, store.snapshot("alice", listOf(group)).progress[goal.questId]!!, 0.0)
            val left = group.copy(memberIds = listOf("alice"))
            store.refresh("alice", listOf(left), now + 2, zone)
            db.personalQuestDao().insertEntry(event("left", now + 3))
            assertEquals(expected, store.snapshot("alice", listOf(left)).progress[goal.questId]!!, 0.0)
            store.refresh("alice", listOf(group), now + 4, zone)
            db.personalQuestDao().insertEntry(event("again", now + 5))
            assertEquals(expected * 2, store.snapshot("alice", listOf(group)).progress[goal.questId]!!, 0.0)
        } finally { db.close() }
    }
    @Test fun weeklyManualSetIsIdempotentAndCreditsPersonalGoalWithoutMakingCopies() = runBlocking {
        val db = db()
        try {
            val store = GroupChallengeStore(db)
            val week = questWindow(now, zone, QuestCadence.WEEKLY)
            db.questDao().insertQuest(QuestEntity.fromQuest(quest("weekly")))
            db.groupChallengeDao().insertChallenge(GroupChallengeEntity("weekly", group.id, "WEEKLY", "BODYWEIGHT_REPS", 100.0,
                week.key, week.start, week.end, zone.id, 2, "weekly-body"))
            store.refresh("alice", listOf(group), now - 1, zone)
            store.recordWeekly("alice", group, "weekly", 10.0, "one-set", now)
            store.recordWeekly("alice", group, "weekly", 10.0, "one-set", now)
            val personal = PersonalQuestStore(db).refresh("alice", now, zone)
            assertEquals(10.0, personal.quests.single { it.assignment.slot == "BODYWEIGHT_REPS" }.value, 0.0)
            assertEquals(1, db.personalQuestDao().getEntries("alice").size)
            denied { store.recordWeekly("alice", group, "weekly", 100.0, "too-many", now) }
        } finally { db.close() }
    }
    @Test fun boundedWeeklyWalkKeepsSamePausedSessionAndCreatesAnotherOnlyAfterSaving() = runBlocking {
        val db = db()
        try {
            val personal = PersonalQuestStore(db)
            val weekly = personal.refresh("alice", now, zone).quests.single { it.assignment.slot == "WALK_METERS" }
            val first = personal.boundedWalk("alice", weekly.assignment.questId, weekly.assignment.target, now, zone)
            val gps = RoomQuestTrackingStore(db)
            val session = gps.begin(first, "alice", now)
            gps.checkpoint(null, session.copy(distanceMeters = 2000.0, elapsedMillis = 600000))
            assertEquals(first, personal.boundedWalk("alice", weekly.assignment.questId, weekly.assignment.target, now + 1000, zone))
            assertEquals(2000.0, gps.load(first, "alice")!!.distanceMeters, 0.0)
            assertTrue(personal.refresh("alice", now, zone).quests.single { it.assignment.questId == weekly.assignment.questId }.hasSavedSession)
            gps.checkpoint(null, session.copy(distanceMeters = 5000.0, elapsedMillis = 1800000))
            gps.finish(first, "alice", session.sessionId, now + 2000)
            val next = personal.boundedWalk("alice", weekly.assignment.questId, weekly.assignment.target - 5000, now + 3000, zone)
            assertNotEquals(first, next)
            assertEquals(5000.0, activityProgress(ActivityMetric.WALK_METERS,
                questActivities(db.trackingDao().getResults("alice"), db.personalQuestDao().getEntries("alice")), now, now + 10000, zone), 0.0)
        } finally { db.close() }
    }

    @Test fun legacyActiveRouteAndPhotosKeepTheirOriginalIdsWhenQuestBecomesOpenToEveryone() = runBlocking {
        val db = db()
        try {
            db.questDao().insertQuest(QuestEntity.fromQuest(quest("legacy", QuestType.DISTANCE).copy(assignedUserId = "alice", assignedTo = "Alice")))
            val gps = RoomQuestTrackingStore(db)
            val session = gps.begin("legacy", "alice", now)
            val snapshot = GroupChallengeStore(db).refresh("alice", listOf(group), now, zone)
            val mapping = snapshot.attempts.single { it.attemptId == "legacy" }
            assertEquals(session.sessionId, gps.load("legacy", "alice")!!.sessionId)
            val bob = GroupChallengeStore(db).open("bob", group, mapping.challengeId, "Bob", now)
            assertNotEquals("legacy", bob)
            assertEquals(session.sessionId, gps.begin("legacy", "alice", now).sessionId)
        } finally { db.close() }
    }
}
