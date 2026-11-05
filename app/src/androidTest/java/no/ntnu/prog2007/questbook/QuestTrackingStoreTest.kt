package no.ntnu.prog2007.questbook

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import no.ntnu.prog2007.questbook.data.repository.RoomQuestRepository
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.tracking.RoomQuestTrackingStore
import no.ntnu.prog2007.questbook.location.RoutePoint
import no.ntnu.prog2007.questbook.location.database.*
import no.ntnu.prog2007.questbook.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Exercises actual Room transactions and upgrades, without Firebase or live location. */
class QuestTrackingStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun quest() = Quest("quest", "group", QuestType.DISTANCE, "Short walk", "Walk outside", "Me",
        QuestStatus.NOT_STARTED, requiredDistanceKm = 0.005, assignedUserId = "me")

    @Test fun sessionRestoresAndCompletionIsAtomicAndIdempotent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, QuestBookDatabase::class.java).build()
        try {
            db.questDao().insertQuest(QuestEntity.fromQuest(quest()))
            val store = RoomQuestTrackingStore(db)
            val state = store.begin("quest", "me", 1000)
            assertEquals("IN_PROGRESS", db.questDao().getQuest("quest")!!.status)
            val point = RoutePointEntity(sessionId = state.sessionId, latitude = 1.0, longitude = 2.0,
                timestamp = 4000, accuracy = 5f)
            store.checkpoint(point, state.copy(distanceMeters = 6.0, elapsedMillis = 3000))
            val restored = RoomQuestTrackingStore(db).load("quest", "me")!!
            assertEquals(state.sessionId, restored.sessionId)
            assertEquals(6.0, restored.distanceMeters, 0.0)
            assertEquals(3000L, restored.elapsedMillis)
            assertEquals(listOf(RoutePoint(1.0, 2.0, 4000, 5f)), restored.routePoints)
            val result = store.finish("quest", "me", state.sessionId, 5000)
            assertEquals(result, store.finish("quest", "me", state.sessionId, 6000))
            assertEquals("COMPLETED", db.questDao().getQuest("quest")!!.status)
            assertNull(db.trackingDao().getSession("quest"))
            assertEquals(1, db.trackingDao().observeResults("me").first().size)
            assertEquals(1, db.routePointDao().getPointsForSession(state.sessionId).first().size)
            assertEquals(3000L, result.durationMillis)
        } finally { db.close() }
    }

    @Test fun incompleteOrWrongUserCannotCompleteAndExistingDataIsKept() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, QuestBookDatabase::class.java).build()
        try {
            db.questDao().insertQuest(QuestEntity.fromQuest(quest()))
            val store = RoomQuestTrackingStore(db)
            try { store.begin("quest", "other", 0); fail("Another user cannot start") } catch (_: IllegalArgumentException) {}
            val state = store.begin("quest", "me", 1000)
            try { store.finish("quest", "me", state.sessionId, 2000); fail("Target is incomplete") } catch (_: IllegalStateException) {}
            assertNotNull(db.trackingDao().getSession("quest"))
            assertNull(db.trackingDao().getResult("quest"))
            assertEquals("IN_PROGRESS", db.questDao().getQuest("quest")!!.status)
            // A point must roll back if the matching checkpoint cannot be updated.
            try {
                store.checkpoint(RoutePointEntity(sessionId = "missing", latitude = 1.0, longitude = 2.0,
                    timestamp = 1000, accuracy = 5f), state.copy(sessionId = "missing"))
                fail("The checkpoint is missing")
            } catch (_: IllegalStateException) {}
            assertTrue(db.routePointDao().getPointsForSession("missing").first().isEmpty())
        } finally { db.close() }
    }

    @Test fun upgradesFromVersionsOneThroughFivePreserveExistingRoutes() = runBlocking {
        for (version in listOf(1, 2, 3, 4, 5)) {
            val name = "gps-upgrade-test-$version.db"
            context.deleteDatabase(name)
            // Build a real old-version database from its exported schema, then let Room migrate it.
            val raw = context.openOrCreateDatabase(name, 0, null)
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            val schema = JSONObject(assets.open("no.ntnu.prog2007.questbook.location.database.QuestBookDatabase/$version.json")
                .bufferedReader().use { it.readText() }).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                // Exported schemas describe indexes separately; reproduce the full old database.
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(i).getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            raw.execSQL("INSERT INTO route_points (id, sessionId, latitude, longitude, timestamp, accuracy) VALUES (1, 'legacy', 1, 2, 1000, 5)")
            if (version >= 2) raw.execSQL("""
                INSERT INTO quests (id, groupId, type, title, description, assignedTo, status, requiredDistanceKm,
                    createdByUserId, assignedUserId, createdAtMillis, startedAtMillis)
                VALUES ('legacy-quest', 'group', 'DISTANCE', 'Old quest', '', 'Me', 'NOT_STARTED', 5, 'me', 'me', 0, NULL)
            """.trimIndent())
            if (version >= 3) {
                raw.execSQL("INSERT INTO tracking_sessions VALUES ('legacy-quest', 'legacy', 'me', 1000, 0, 100, 2000)")
                raw.execSQL("INSERT INTO quest_results VALUES ('legacy-result', 'done', 'group', 'me', 'Old result', 'Me', 5000, 2000, 100, 'old-route')")
            }
            if (version >= 4) raw.execSQL("INSERT INTO memory_photos VALUES ('photo', 'legacy-result', 'done', 'group', 'me', 'Old photo', X'010203', 5000)")
            raw.version = version
            raw.close()
            val db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name)
                .addMigrations(QuestBookDatabase.MIGRATION_1_2, QuestBookDatabase.MIGRATION_2_3, QuestBookDatabase.MIGRATION_3_4, QuestBookDatabase.MIGRATION_4_5, QuestBookDatabase.MIGRATION_5_6).build()
            try {
                val legacy = db.routePointDao().getPointsForSession("legacy").first()
                assertEquals(1, legacy.size)
                assertFalse(legacy.single().startsSegment)
                if (version >= 2) assertEquals("Old quest", db.questDao().getQuest("legacy-quest")!!.title)
                if (version >= 3) {
                    assertEquals(100.0, db.trackingDao().getSession("legacy-quest")!!.distanceMeters, 0.0)
                    assertEquals("Old result", db.trackingDao().getResult("done")!!.questTitle)
                } else assertTrue(db.trackingDao().observeResults("me").first().isEmpty())
                if (version >= 4) {
                    assertArrayEquals(byteArrayOf(1, 2, 3), db.memoryPhotoDao().observePhotos("me").first().single().jpegBytes)
                }
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }
    @Test fun assignmentUnlocksGpsAndCannotOverwriteAnActiveOrCompletedQuest() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, QuestBookDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            db.questDao().insertQuest(QuestEntity.fromQuest(quest().copy(assignedUserId = null, assignedTo = "")))
            val repo = RoomQuestRepository(db.questDao(), scope)
            val store = RoomQuestTrackingStore(db)
            try { store.begin("quest", "me", 1000); fail("Assignment is required") } catch (_: IllegalArgumentException) {}
            try { repo.assignQuest("quest", "other-group", "me", "Me"); fail("Wrong group") } catch (_: IllegalStateException) {}
            repo.assignQuest("quest", "group", "me", "My actual name")
            val row = db.questDao().getQuest("quest")!!
            assertEquals("me", row.assignedUserId)
            assertEquals("My actual name", row.assignedTo)
            assertEquals(0.005, row.requiredDistanceKm!!, 0.0)
            // A successful GPS start must make every later assignment update fail atomically.
            val state = store.begin("quest", "me", 1000)
            try { repo.assignQuest("quest", "group", "other", "Other"); fail("Tracking has started") } catch (_: IllegalStateException) {}
            store.checkpoint(null, state.copy(distanceMeters = 6.0))
            store.finish("quest", "me", state.sessionId, 2000)
            try { repo.assignQuest("quest", "group", "other", "Other"); fail("Already completed") } catch (_: IllegalStateException) {}
            assertEquals("me", db.questDao().getQuest("quest")!!.assignedUserId)
        } finally {
            // Wait for the repository's observer to finish before closing its SQLite connection.
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
        }
    }

}
