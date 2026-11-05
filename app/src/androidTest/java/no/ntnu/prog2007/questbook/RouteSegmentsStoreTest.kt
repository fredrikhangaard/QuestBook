package no.ntnu.prog2007.questbook

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.tracking.RoomQuestTrackingStore
import no.ntnu.prog2007.questbook.location.*
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.*
import no.ntnu.prog2007.questbook.ui.quest.route.*
import org.junit.Assert.*
import org.junit.Test

/** End-to-end local storage: pause, restore, complete and reopen without inventing a paused edge. */
class RouteSegmentsStoreTest {
    private class Source : GpsSource {
        lateinit var emit: (RoutePoint, Boolean) -> Unit
        override fun start(onPoint: (RoutePoint, Boolean) -> Unit) { emit = onPoint }
        override fun stop() {}
    }
    @Test fun boundariesSurviveRestoreCompletionAndDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "gps-segments-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name).build()
        val controllers = mutableListOf<GpsRepository>()
        try {
            db.questDao().insertQuest(QuestEntity.fromQuest(Quest("q", "g", QuestType.DISTANCE, "Walk", "", "Me",
                QuestStatus.NOT_STARTED, requiredDistanceKm = 0.01, assignedUserId = "me")))
            val store = RoomQuestTrackingStore(db)
            var now = 0L
            fun controller(source: Source) = GpsRepository(source, {}, Dispatchers.IO,
                persistCheckpoint = store::checkpoint, clock = { now }).also { controllers += it }
            val firstSource = Source()
            val first = controller(firstSource)
            first.restore(store.begin("q", "me", 0))
            first.resumeTracking()
            firstSource.emit(RoutePoint(0.0, 0.0, 1000, 5f), true)
            now = 3000
            firstSource.emit(RoutePoint(0.00005, 0.0, 4000, 5f), true)
            first.stopTracking()
            first.flush()
            first.close()
            // A new controller represents leaving the screen or restarting the app.
            now = 30000
            val secondSource = Source()
            val second = controller(secondSource)
            second.restore(store.load("q", "me")!!)
            second.resumeTracking()
            secondSource.emit(RoutePoint(0.5, 0.0, 31000, 5f), true)
            now = 33000
            secondSource.emit(RoutePoint(0.50005, 0.0, 34000, 5f), true)
            second.stopTracking()
            second.flush()
            val result = store.finish("q", "me", second.state.value.sessionId, 35000)
            second.close()
            assertEquals(6000L, result.durationMillis)
            assertEquals(11.12, result.distanceMeters!!, 0.02)
            db.close()
            db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name).build()
            val route = RoomSavedRouteReader(db).load("q", "me")!!
            assertEquals(listOf(true, false, true, false), route.points.map { it.startsSegment })
            assertEquals(listOf(0..1, 2..3), routeSegmentRanges(route.points))
            assertEquals(result, route.result)
        } finally {
            controllers.forEach { it.close() }
            db.close()
            context.deleteDatabase(name)
        }
    }
}
