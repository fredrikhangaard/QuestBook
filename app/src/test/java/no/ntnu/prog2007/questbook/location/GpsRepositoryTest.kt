package no.ntnu.prog2007.questbook.location

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import no.ntnu.prog2007.questbook.location.database.RoutePointEntity
import org.junit.Assert.*
import org.junit.Test

class GpsRepositoryTest {
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
}
