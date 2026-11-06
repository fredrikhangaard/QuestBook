package no.ntnu.prog2007.questbook.location

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import no.ntnu.prog2007.questbook.location.database.RoutePointEntity
import org.junit.Assert.*
import org.junit.Test

class GpsRepositoryTest {
    @Test fun providerStartFailureKeepsSessionPausedAndRetryable() = runBlocking {
        var failing = true
        val source = object : GpsSource {
            override fun start(onPoint: (RoutePoint, Boolean) -> Unit) {
                check(!failing) { "Provider unavailable" }
            }
            override fun stop() {}
        }
        val repository = GpsRepository(source, {}, Dispatchers.Unconfined)
        try {
            repository.startTracking(sessionId = "retry-me")
            assertFalse(repository.state.value.isTracking)
            assertEquals("retry-me", repository.state.value.sessionId)
            assertTrue(repository.state.value.status.contains("Check precise location"))
            failing = false
            repository.resumeTracking()
            assertTrue(repository.state.value.isTracking)
            assertEquals("retry-me", repository.state.value.sessionId)
        } finally { repository.close() }
    }

    private class Source : GpsSource {
        lateinit var callback: (RoutePoint, Boolean) -> Unit
        var starts = 0
        var stops = 0
        override fun start(onPoint: (RoutePoint, Boolean) -> Unit) { callback = onPoint; starts++ }
        override fun stop() { stops++ }
        fun emit(latitude: Double, time: Long, accuracy: Float = 5f, hasAccuracy: Boolean = true) =
            callback(RoutePoint(latitude, 0.0, time, accuracy), hasAccuracy)
    }

    @Test fun filtersPreserveLastAcceptedPointAndCounters() = runBlocking {
        val source = Source()
        val repository = GpsRepository(source, {}, Dispatchers.Unconfined)
        try {
            repository.startTracking()
            repository.startTracking()
            assertEquals(1, source.starts)
            source.emit(0.0, 1000, hasAccuracy = false)
            source.emit(0.0, 2000, accuracy = 26f)
            source.emit(0.0, 3000)
            source.emit(0.00005, 3000)
            assertEquals("Old GPS point ignored", repository.state.value.status)
            source.emit(1.0, 4000)
            assertEquals("GPS jump ignored", repository.state.value.status)
            source.emit(0.000001, 5000)
            assertEquals("GPS active - waiting for movement", repository.state.value.status)
            source.emit(0.00005, 6000)
            val state = repository.state.value
            assertEquals(7, state.pointCount)
            assertEquals(2, state.routePoints.size)
            assertEquals(5.56, state.distanceMeters, 0.02)
            repository.stopTracking()
            source.emit(0.0001, 9000)
            assertEquals(state.routePoints, repository.state.value.routePoints)
            repository.permissionDenied()
            assertEquals("Precise location permission required", repository.state.value.status)
        } finally { repository.close() }
    }

    @Test fun writesDrainInOrderAcrossRestartAndClose() = runBlocking {
        val source = Source()
        val saved = Channel<RoutePointEntity>(Channel.UNLIMITED)
        val repository = GpsRepository(source, { delay(10); saved.send(it) }, Dispatchers.Default)
        repository.startTracking()
        val oldCallback = source.callback
        source.emit(0.0, 1000)
        source.emit(0.00005, 4000)
        repository.stopTracking()
        repository.startTracking()
        oldCallback(RoutePoint(2.0, 0.0, 5000, 5f), true)
        source.emit(0.0, 6000)
        assertEquals(1, repository.state.value.pointCount)
        assertEquals(0.0, repository.state.value.distanceMeters, 0.0)
        repository.close()
        withTimeout(5000) {
            val first = saved.receive()
            val second = saved.receive()
            val third = saved.receive()
            assertEquals(listOf(1000L, 4000L, 6000L), listOf(first.timestamp, second.timestamp, third.timestamp))
            assertEquals(first.sessionId, second.sessionId)
            assertNotEquals(first.sessionId, third.sessionId)
        }
    }

    @Test fun progressIsCappedAtTarget() {
        val state = GpsTrackingUiState(distanceMeters = 5100.0)
        assertTrue(state.distanceCompleted)
        assertEquals(1f, state.progress)
    }
    @Test fun selectedTargetCompletesAndStopsWithoutAcceptingLatePoints() = runBlocking {
        val source = Source()
        val repository = GpsRepository(source, {}, Dispatchers.Unconfined)
        try {
            repository.startTracking(requiredDistanceMeters = 5.0)
            source.emit(0.0, 1000)
            source.emit(0.00005, 4000)
            assertTrue(repository.state.value.distanceCompleted)
            assertFalse(repository.state.value.isTracking)
            assertEquals(1f, repository.state.value.progress)
            val count = repository.state.value.pointCount
            source.emit(0.0001, 7000)
            assertEquals(count, repository.state.value.pointCount)
            repository.flush()
        } finally { repository.close() }
    }

    // A large position/time change while paused must not become part of the walking segment.
    @Test fun resumeKeepsSessionAndExcludesPausedMovementAndTime() = runBlocking {
        val source = Source()
        var now = 0L
        val saved = mutableListOf<RoutePointEntity>()
        val repository = GpsRepository(source, { saved += it }, Dispatchers.Unconfined, clock = { now })
        try {
            repository.startTracking(requiredDistanceMeters = 100.0)
            val id = repository.state.value.sessionId
            source.emit(0.0, 1000)
            now = 3000
            source.emit(0.00005, 4000)
            repository.stopTracking()
            now = 30000
            repository.resumeTracking()
            source.emit(0.5, 31000)
            assertEquals(5.56, repository.state.value.distanceMeters, 0.02)
            now = 33000
            source.emit(0.50005, 34000)
            repository.stopTracking()
            assertEquals(id, repository.state.value.sessionId)
            assertEquals(11.12, repository.state.value.distanceMeters, 0.02)
            assertEquals(6000L, repository.state.value.elapsedMillis)
            repository.flush()
            assertEquals(listOf(true, false, true, false), saved.map { it.startsSegment })
        } finally { repository.close() }
    }

    // Simulate a disk failure, then recover it; neither accepted point may be skipped or reordered.
    @Test fun failedCheckpointRetriesBeforeFlushAndKeepsPointOrder() = runBlocking {
        val source = Source()
        var failing = true
        val timestamps = mutableListOf<Long>()
        val boundaries = mutableListOf<Boolean>()
        val repository = GpsRepository(source, {}, Dispatchers.Unconfined,
            persistCheckpoint = { point, _ ->
                check(!failing) { "disk unavailable" }
                point?.let { timestamps.add(it.timestamp); boundaries.add(it.startsSegment) }
            })
        try {
            repository.startTracking()
            source.emit(0.0, 1000)
            source.emit(0.00005, 4000)
            assertNotNull(repository.state.value.persistenceError)
            try { repository.flush(); fail("Flush must report the failed write") } catch (_: IllegalStateException) {}
            failing = false
            repository.flush()
            assertNull(repository.state.value.persistenceError)
            assertEquals(listOf(1000L, 4000L), timestamps)
            assertEquals(listOf(true, false), boundaries)
        } finally { repository.close() }
    }

    @Test fun restoredSessionResumesWithoutConnectingToOldPosition() = runBlocking {
        val source = Source()
        val saved = mutableListOf<RoutePointEntity>()
        val repository = GpsRepository(source, { saved += it }, Dispatchers.Unconfined)
        try {
            repository.restore(GpsTrackingUiState(sessionId = "saved", requiredDistanceMeters = 100.0,
                distanceMeters = 12.0, elapsedMillis = 9000,
                routePoints = listOf(RoutePoint(0.0, 0.0, 1000, 5f))))
            assertFalse(repository.state.value.isTracking)
            repository.resumeTracking()
            source.emit(0.5, 2000)
            assertEquals(12.0, repository.state.value.distanceMeters, 0.0)
            assertEquals("saved", repository.state.value.sessionId)
            repository.stopTracking()
            repository.flush()
            assertTrue(saved.single().startsSegment)
        } finally { repository.close() }
    }

    @Test fun rejectedResumeFixesDoNotConsumeTheNewSegmentBoundary() = runBlocking {
        val source = Source()
        val saved = mutableListOf<RoutePointEntity>()
        val repository = GpsRepository(source, { saved += it }, Dispatchers.Unconfined)
        try {
            repository.startTracking()
            source.emit(0.0, 1000)
            repository.stopTracking()
            repository.resumeTracking()
            source.emit(0.5, 2000, accuracy = 50f) // Rejected: still waiting for an anchor.
            source.emit(0.5, 900) // Rejected: globally out of order after resume.
            source.emit(0.5, 3000)
            source.emit(0.50005, 6000)
            repository.stopTracking()
            repository.flush()
            assertEquals(listOf(1000L, 3000L, 6000L), saved.map { it.timestamp })
            assertEquals(listOf(true, true, false), saved.map { it.startsSegment })
            assertEquals(5.56, repository.state.value.distanceMeters, 0.02)
        } finally { repository.close() }
    }

}
