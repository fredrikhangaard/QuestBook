package no.ntnu.prog2007.questbook

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity
import no.ntnu.prog2007.questbook.location.database.*
import no.ntnu.prog2007.questbook.ui.quest.route.*
import org.junit.Assert.*
import org.junit.Test

/** Loads the result's actual session, in timestamp/id order, without exposing another account's route. */
class SavedRouteReaderTest {
    @Test fun readerUsesOwnedResultSessionAndHandlesMissingPoints() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, QuestBookDatabase::class.java).build()
        try {
            db.trackingDao().insertResult(QuestResultEntity("result", "quest", "group", "me", "Walk", "Me", 4000, 3000, 1000.0, "session"))
            val dao = db.routePointDao()
            dao.insertPoint(RoutePointEntity(sessionId = "session", latitude = 60.1, longitude = 10.1, timestamp = 2000, accuracy = 5f))
            dao.insertPoint(RoutePointEntity(sessionId = "session", latitude = 60.0, longitude = 10.0, timestamp = 1000, accuracy = 5f))
            dao.insertPoint(RoutePointEntity(sessionId = "other-session", latitude = 50.0, longitude = 1.0, timestamp = 0, accuracy = 5f))
            val reader = RoomSavedRouteReader(db)
            assertNull(reader.load("quest", "other"))
            assertNull(reader.load("missing", "me"))
            val route = reader.load("quest", "me")!!
            assertEquals(listOf(RoutePosition(60.0, 10.0), RoutePosition(60.1, 10.1)), route.points)
            dao.deleteSession("session")
            assertTrue(reader.load("quest", "me")!!.points.isEmpty())
            assertNotNull(db.trackingDao().getResult("quest"))
        } finally { db.close() }
    }
    @Test fun aFilteredAnchorDoesNotLoseItsSegmentBoundary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, QuestBookDatabase::class.java).build()
        try {
            db.trackingDao().insertResult(QuestResultEntity("r", "q", "g", "me", "Walk", "Me", 5000, 2000, 10.0, "s"))
            val dao = db.routePointDao()
            dao.insertPoint(RoutePointEntity(sessionId = "s", latitude = 60.0, longitude = 10.0, timestamp = 1000, accuracy = 5f))
            dao.insertPoint(RoutePointEntity(sessionId = "s", latitude = 100.0, longitude = 10.0, timestamp = 2000, accuracy = 5f, startsSegment = true))
            dao.insertPoint(RoutePointEntity(sessionId = "s", latitude = 60.1, longitude = 10.1, timestamp = 3000, accuracy = 5f))
            val route = RoomSavedRouteReader(db).load("q", "me")!!
            assertEquals(listOf(0..0, 1..1), routeSegmentRanges(route.points))
        } finally { db.close() }
    }

}
