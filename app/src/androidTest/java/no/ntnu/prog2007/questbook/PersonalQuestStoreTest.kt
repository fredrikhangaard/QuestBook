package no.ntnu.prog2007.questbook

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import java.time.*
import kotlin.random.Random
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.tracking.*
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.*
import org.junit.Assert.*
import org.junit.Test

/** Real local transactions: stable draws, daily→weekly credit, manual logging, GPS, and privacy. */
class PersonalQuestStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Europe/Oslo")
    private val now = ZonedDateTime.of(2026, 11, 6, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun database() = Room.inMemoryDatabaseBuilder(context, QuestBookDatabase::class.java).build()
    private suspend fun daily(db: QuestBookDatabase, metric: ActivityMetric, target: Double): PersonalQuestEntity {
        val window = questWindow(now, zone, QuestCadence.DAILY)
        val row = PersonalQuestEntity("personal-daily", "me", "fixture", "DAILY", window.key, "daily", "Daily activity", "", metric.name,
            target, window.start, window.end, zone.id)
        db.personalQuestDao().insertAssignment(row)
        db.questDao().insertQuest(QuestEntity.fromQuest(Quest(row.questId, PersonalQuestIds.group("me"),
            if (metric == ActivityMetric.WALK_METERS) QuestType.DISTANCE else QuestType.CUSTOM,
            row.title, "", "You", QuestStatus.NOT_STARTED, if (metric == ActivityMetric.WALK_METERS) target / 1000 else null,
            assignedUserId = "me", createdAtMillis = now)))
        return row
    }
    @Test fun drawsAreStableAcrossReopenAndRollOverWithoutLosingHistory() = runBlocking {
        val name = "personal-assignment-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name).build()
        try {
            val initial = PersonalQuestStore(db, Random(1)).refresh("me", now, zone).quests
            assertEquals(1, initial.count { it.assignment.cadence == "DAILY" })
            assertEquals(5, initial.count { it.assignment.cadence == "WEEKLY" })
            db.close()
            db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name).build()
            val store = PersonalQuestStore(db, Random(99))
            assertEquals(initial, store.refresh("me", now, zone).quests)
            coroutineScope { List(3) { async { store.refresh("me", now, zone) } }.awaitAll() }
            assertEquals(6, db.personalQuestDao().getAssignments("me").size)
            val tomorrow = store.refresh("me", now + 86400000L, zone).quests
            assertEquals(7, tomorrow.size)
            val dailyRows = tomorrow.filter { it.assignment.cadence == "DAILY" }
            assertNotEquals(dailyRows[0].assignment.templateId, dailyRows[1].assignment.templateId)
            val nextWeek = store.refresh("me", now + 7 * 86400000L, zone).quests
            assertEquals(10, nextWeek.count { it.assignment.cadence == "WEEKLY" })
            assertTrue(store.refresh("other", now, zone).quests.all { it.assignment.userId == "other" })
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun aSavedFiveKmWalkAdvancesDailyAndWeeklyWithoutDoubleCounting() = runBlocking {
        val db = database()
        try {
            daily(db, ActivityMetric.WALK_METERS, 5000.0)
            db.trackingDao().insertResult(QuestResultEntity("gps", "group-walk", "group", "me", "Group walk", "Me", now, 3600000, 5000.0, "session"))
            db.trackingDao().insertResult(QuestResultEntity("other-gps", "other-walk", "group", "other", "Other walk", "Other", now, 3600000, 90000.0, "other-session"))
            val store = PersonalQuestStore(db, Random(1))
            val state = store.refresh("me", now, zone)
            assertTrue(state.quests.single { it.assignment.cadence == "DAILY" }.complete)
            assertEquals(5000.0, state.quests.single { it.assignment.slot == "WALK_METERS" }.value, 0.0)
            store.refresh("me", now, zone)
            val rows = db.trackingDao().getResults("me")
            assertEquals(2, rows.size)
            val summary = weeklyActivitySummary(rows, db.personalQuestDao().getAssignments("me"),
                questActivities(rows, emptyList()), questWindow(now, zone, QuestCadence.WEEKLY))
            assertEquals(5000.0, summary.distanceMeters, 0.0)
            assertEquals(3600000L, summary.activeMillis)
            assertEquals(1, summary.activeDays)
        } finally { db.close() }
    }
    @Test fun manualSetsAreIdempotentAndVisitsNeedNoPhoto() = runBlocking {
        val db = database()
        try {
            val row = daily(db, ActivityMetric.PUSHUPS, 30.0)
            val store = PersonalQuestStore(db)
            store.refresh("me", now, zone)
            store.logManual("me", row.questId, 10.0, 2.0, "set1", now)
            store.logManual("me", row.questId, 10.0, 2.0, "set1", now)
            store.logManual("me", row.questId, 20.0, 3.0, "set2", now)
            store.logManual("me", row.questId, 20.0, 3.0, "set2", now)
            val state = store.refresh("me", now, zone)
            assertTrue(state.quests.single { it.assignment.cadence == "DAILY" }.complete)
            assertEquals(30.0, state.quests.single { it.assignment.slot == "BODYWEIGHT_REPS" }.value, 0.0)
            assertEquals(2, db.personalQuestDao().getEntries("me").size)
            val visit = store.createActivity("me", ActivityMetric.WATER_VISITS, 1.0, 0.0, "visit", false, now, zone)
            assertNotNull(db.trackingDao().getResult(visit))
            assertNull(db.trackingDao().getResult(visit)!!.gpsSessionId)
            assertEquals("WATER", db.personalQuestDao().getEntries("me").single { it.id == "visit" }.visitKind)
        } finally { db.close() }
    }
    @Test fun partialManualDistanceAndGpsTogetherCompleteDailyAndWeekly() = runBlocking {
        val db = database()
        try {
            val row = daily(db, ActivityMetric.WALK_METERS, 5000.0)
            val store = PersonalQuestStore(db)
            store.refresh("me", now, zone)
            store.logManual("me", row.questId, 2.0, 20.0, "manual-walk", now)
            store.prepareGps("me", row.questId, now)
            assertEquals(3.0, db.questDao().getQuest(row.questId)!!.requiredDistanceKm!!, 0.0)
            val gps = RoomQuestTrackingStore(db)
            val session = gps.begin(row.questId, "me", now)
            gps.checkpoint(null, session.copy(distanceMeters = 3000.0, elapsedMillis = 1800000))
            gps.finish(row.questId, "me", session.sessionId, now + 1800000)
            val state = store.refresh("me", now + 1800000, zone)
            assertTrue(state.quests.single { it.assignment.cadence == "DAILY" }.complete)
            assertEquals(5000.0, state.quests.single { it.assignment.slot == "WALK_METERS" }.value, 0.0)
        } finally { db.close() }
    }
    @Test fun manualLoggingRejectsOtherAccountsExpiryInvalidAmountsAndWeeklyCounterEdits() = runBlocking {
        val db = database()
        try {
            val row = daily(db, ActivityMetric.PUSHUPS, 30.0)
            val store = PersonalQuestStore(db)
            val state = store.refresh("me", now, zone)
            suspend fun denied(block: suspend () -> Unit) {
                try { block(); fail("Expected invalid activity to be rejected") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
            }
            denied { store.logManual("other", row.questId, 10.0, 0.0, "a", now) }
            denied { store.logManual("me", row.questId, 31.0, 0.0, "b", now) }
            denied { store.logManual("me", row.questId, Double.NaN, 0.0, "c", now) }
            denied { store.logManual("me", row.questId, 1.5, 0.0, "d", now) }
            denied { store.logManual("me", row.questId, 10.0, 0.0, "e", row.periodEndMillis) }
            denied { store.logManual("me", state.quests.first { it.assignment.cadence == "WEEKLY" }.assignment.questId, 1.0, 0.0, "f", now) }
            assertTrue(db.personalQuestDao().getEntries("me").isEmpty())
        } finally { db.close() }
    }
    @Test fun extraActivitiesCanCompleteWeeklyGoalsAfterTheDailyIsDone() = runBlocking {
        val db = database()
        try {
            val row = daily(db, ActivityMetric.PUSHUPS, 10.0)
            val store = PersonalQuestStore(db)
            store.refresh("me", now, zone)
            store.logManual("me", row.questId, 10.0, 1.0, "daily", now)
            val snapshot = store.refresh("me", now, zone)
            val reps = snapshot.quests.single { it.assignment.slot == "BODYWEIGHT_REPS" }.assignment.target - 10
            store.createActivity("me", ActivityMetric.SQUATS, reps, 5.0, "extra-reps", false, now, zone)
            store.createActivity("me", ActivityMetric.SQUATS, reps, 5.0, "extra-reps", false, now, zone)
            val current = store.refresh("me", now, zone)
            assertTrue(current.quests.single { it.assignment.slot == "BODYWEIGHT_REPS" }.complete)
            assertEquals(1, current.quests.count { it.assignment.cadence == "ACTIVITY" })
            assertEquals(5, current.quests.count { it.assignment.cadence == "WEEKLY" })
            assertEquals(1, current.quests.count { it.assignment.cadence == "DAILY" })
        } finally { db.close() }
    }
    @Test fun expiredDailyDoesNotGainLateCreditButStartedGpsCanStillBeSaved() = runBlocking {
        val db = database()
        try {
            val row = daily(db, ActivityMetric.WALK_METERS, 1000.0)
            val store = PersonalQuestStore(db)
            store.refresh("me", now, zone)
            val gps = RoomQuestTrackingStore(db)
            val session = gps.begin(row.questId, "me", now)
            gps.checkpoint(null, session.copy(distanceMeters = 1000.0, elapsedMillis = 600000))
            // A saved session remains recoverable after midnight; the physical result is not lost.
            gps.begin(row.questId, "me", row.periodEndMillis + 1000)
            gps.finish(row.questId, "me", session.sessionId, row.periodEndMillis + 1000)
            val state = store.refresh("me", row.periodEndMillis + 1000, zone)
            assertFalse(state.quests.single { it.assignment.questId == row.questId }.complete)
            assertNotNull(db.trackingDao().getResult(row.questId))
            assertEquals(1000.0, state.quests.single { it.assignment.slot == "WALK_METERS" }.value, 0.0)
        } finally { db.close() }
    }

}
