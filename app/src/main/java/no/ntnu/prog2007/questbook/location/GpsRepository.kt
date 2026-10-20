package no.ntnu.prog2007.questbook.location

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.ntnu.prog2007.questbook.location.database.RoutePointEntity

/** Called on the main thread, like LocationTracker callbacks. */
interface GpsSource {
    fun start(onPoint: (RoutePoint, Boolean) -> Unit)
    fun stop()
}

class GpsRepository(
    private val source: GpsSource,
    private val insertPoint: suspend (RoutePointEntity) -> Unit,
    writeDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutableState = MutableStateFlow(GpsTrackingUiState())
    val state = mutableState.asStateFlow()
    private var sessionId = ""
    private var generation = 0
    private var closed = false
    private val writes = Channel<RoutePointEntity>(Channel.UNLIMITED)
    private val writerScope = CoroutineScope(SupervisorJob() + writeDispatcher)

    init {
        // One consumer preserves acceptance order, including across stop/start.
        writerScope.launch {
            try {
                for (point in writes) {
                    try {
                        insertPoint(point)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            mutableState.value = mutableState.value.copy(status = "Could not save GPS point")
                        }
                    }
                }
            } finally {
                writerScope.cancel()
            }
        }
    }

    fun startTracking() {
        if (closed || state.value.isTracking) return
        sessionId = UUID.randomUUID().toString()
        val token = ++generation
        mutableState.value = GpsTrackingUiState(isTracking = true, status = "Starting GPS...")
        source.start { point, hasAccuracy ->
            if (token == generation && state.value.isTracking) receive(point, hasAccuracy)
        }
    }

    fun stopTracking() {
        ++generation
        source.stop()
        mutableState.value = state.value.copy(isTracking = false, status = "Tracking stopped")
    }

    fun permissionDenied() {
        mutableState.value = state.value.copy(status = "Precise location permission required")
    }

    private fun receive(point: RoutePoint, hasAccuracy: Boolean) {
        val current = state.value.copy(
            latitude = point.latitude, longitude = point.longitude, accuracy = point.accuracy,
            hasLocation = true, pointCount = state.value.pointCount + 1
        )
        val previous = current.routePoints.lastOrNull()
        var segment = 0.0
        val rejection = when {
            !hasAccuracy || point.accuracy > 25f -> "Waiting for accurate GPS"
            previous == null -> null
            point.timestamp <= previous.timestamp -> "Old GPS point ignored"
            else -> {
                segment = DistanceCalculator.calculateDistance(previous, point)
                val seconds = (point.timestamp - previous.timestamp) / 1000.0
                when {
                    segment / seconds > 8.0 -> "GPS jump ignored"
                    segment < 3.0 -> "GPS active - waiting for movement"
                    else -> null
                }
            }
        }
        if (rejection != null) {
            mutableState.value = current.copy(status = rejection)
            return
        }
        val accepted = current.copy(
            distanceMeters = current.distanceMeters + segment,
            routePoints = current.routePoints + point
        )
        writes.trySend(RoutePointEntity(
            sessionId = sessionId, latitude = point.latitude, longitude = point.longitude,
            timestamp = point.timestamp, accuracy = point.accuracy
        )).getOrThrow()
        mutableState.value = accepted.copy(status = if (accepted.distanceCompleted)
            "Distance requirement completed!" else "Receiving GPS updates")
    }

    fun close() {
        if (closed) return
        closed = true
        stopTracking()
        // Drain accepted writes even when the ViewModel is cleared.
        writes.close()
    }
}
