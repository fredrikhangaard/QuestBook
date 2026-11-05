package no.ntnu.prog2007.questbook.location

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import no.ntnu.prog2007.questbook.location.database.RoutePointEntity

/**
 * Boundary between Android location services and the GPS logic.
 * Methods and callbacks run on the main thread; tests provide a source without Android.
 */
interface GpsSource {
    fun start(onPoint: (RoutePoint, Boolean) -> Unit)
    // The default overload lets simple test sources implement only position delivery.
    fun start(onPoint: (RoutePoint, Boolean) -> Unit, onError: (Throwable) -> Unit) = start(onPoint)
    fun stop()
}

/**
 * Owns route filtering, active time, and one ordered queue of database writes.
 * It does not select quests, request permissions, or mark quests completed.
 *
 * @param insertPoint point-only persistence used by the standalone GPS test screen.
 * @param persistCheckpoint quest persistence: writes the optional point and session summary
 * together in a Room transaction. When present, this replaces insertPoint.
 * @param clock monotonic milliseconds for active duration. GPS fix timestamps are used
 * separately for sample ordering and speed checks. Tests can supply a controllable clock.
 */
class GpsRepository(
    private val source: GpsSource,
    private val insertPoint: suspend (RoutePointEntity) -> Unit,
    writeDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val persistCheckpoint: (suspend (RoutePointEntity?, GpsTrackingUiState) -> Unit)? = null,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private val mutableState = MutableStateFlow(GpsTrackingUiState())
    /** Read-only state; screens observe changes without modifying the route directly. */
    val state = mutableState.asStateFlow()
    // Incrementing this invalidates callbacks that arrive after stop or from an old request.
    private var generation = 0
    private var closed = false
    // The last accepted point in the current uninterrupted segment, not necessarily the last received fix.
    private var lastPoint: RoutePoint? = null
    private var resumedAt = 0L
    private var elapsedBeforeResume = 0L
    private var lastCheckpointElapsed = 0L
    // The writer outlives a screen so navigation does not cancel accepted database work.
    private val writerScope = CoroutineScope(SupervisorJob() + writeDispatcher)
    private val writes = Channel<Write>(Channel.UNLIMITED)

    /**
     * A snapshot optionally includes a new route point. A completion-only entry is a barrier:
     * flush waits for all earlier entries, including retries, before allowing a result save.
     */
    private data class Write(
        val snapshot: GpsTrackingUiState? = null,
        val point: RoutePointEntity? = null,
        val completion: CompletableDeferred<Unit>? = null
    )

    init {
        // A failed transaction stays at the front until retried; later writes cannot overtake it.
        writerScope.launch {
            val pending = ArrayDeque<Write>()
            try {
                for (write in writes) {
                    // Flush entries trigger another drain but are not themselves database rows.
                    if (write.snapshot != null) pending.addLast(write)
                    var failure: Exception? = null
                    while (pending.isNotEmpty()) {
                        val next = pending.first()
                        try {
                            if (persistCheckpoint != null) persistCheckpoint.invoke(next.point, next.snapshot!!)
                            else next.point?.let { insertPoint(it) }
                            // Remove only after success; a failed transaction remains first in the queue.
                            pending.removeFirst()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            failure = error
                            break
                        }
                    }
                    mutableState.update { it.copy(persistenceError = if (failure != null)
                        "Could not save GPS data. Pause and retry saving." else null) }
                    write.completion?.let { if (failure == null) it.complete(Unit) else it.completeExceptionally(failure) }
                }
            } finally {
                writerScope.cancel()
            }
        }
    }

    /** Starts a fresh session. The test screen keeps its original 5 km default. */
    fun startTracking(requiredDistanceMeters: Double = 5000.0, sessionId: String = UUID.randomUUID().toString()) {
        if (closed || state.value.isTracking) return
        require(requiredDistanceMeters.isFinite() && requiredDistanceMeters > 0)
        mutableState.value = GpsTrackingUiState(requiredDistanceMeters = requiredDistanceMeters, sessionId = sessionId)
        elapsedBeforeResume = 0
        lastCheckpointElapsed = 0
        resumeTracking()
    }

    /** Restores a persisted session in a paused state; permission is checked before resuming. */
    fun restore(snapshot: GpsTrackingUiState) {
        check(!state.value.isTracking)
        mutableState.value = snapshot.copy(isTracking = false, status = if (snapshot.distanceCompleted)
            "Distance requirement completed!" else "Tracking paused")
        elapsedBeforeResume = snapshot.elapsedMillis
        lastCheckpointElapsed = snapshot.elapsedMillis
        lastPoint = null
    }

    /** Resumes the same route. The first new point starts a new segment, excluding movement while paused. */
    fun resumeTracking() {
        if (closed || state.value.isTracking || state.value.sessionId.isBlank() || state.value.distanceCompleted) return
        // Only callbacks carrying this request token can update the current route.
        val token = ++generation
        resumedAt = clock()
        elapsedBeforeResume = state.value.elapsedMillis
        // Establish a fresh anchor after resuming; movement while paused must not be connected.
        lastPoint = null
        mutableState.update { it.copy(isTracking = true, status = "Starting GPS...") }
        checkpoint()
        try {
            source.start({ point, accuracy ->
                if (token == generation && state.value.isTracking) receive(point, accuracy)
            }, { if (token == generation) locationFailed() })
        } catch (_: SecurityException) {
            locationFailed()
        }
    }

    /**
     * Called by the screen timer while tracking. Every five active seconds a checkpoint is
     * queued even if GPS accuracy is too poor to accept a position. All writes use the same worker.
     */
    fun tick() {
        if (state.value.isTracking) {
            mutableState.update { it.copy(elapsedMillis = elapsedBeforeResume + (clock() - resumedAt).coerceAtLeast(0)) }
            if (state.value.elapsedMillis - lastCheckpointElapsed >= 5000) checkpoint()
        }
    }

    /** Pauses the same session and checkpoints its final active time; it does not reset the route. */
    fun stopTracking() {
        if (!state.value.isTracking) return
        tick()
        // Provider removal is asynchronous, so reject any late fixes immediately as well.
        ++generation
        source.stop()
        mutableState.update { it.copy(isTracking = false, status = if (it.distanceCompleted)
            "Distance requirement completed!" else "Tracking stopped") }
        if (state.value.sessionId.isNotBlank() && !closed) checkpoint()
    }

    /** A failed location request leaves the saved session available for a later retry. */
    private fun locationFailed() {
        stopTracking()
        mutableState.update { it.copy(status = "Could not start GPS. Check precise location permission and device location settings.") }
    }

    /** Permission denial changes feedback only; it never creates a session or starts location. */
    fun permissionDenied() {
        mutableState.update { it.copy(status = "Precise location permission required") }
    }

    /** Captures the current immutable state before a later callback can change it. */
    private fun checkpoint(point: RoutePointEntity? = null) {
        lastCheckpointElapsed = state.value.elapsedMillis
        writes.trySend(Write(snapshot = state.value, point = point)).getOrThrow()
    }

    /** Waits for all preceding transactions, retrying any failed write before completion is allowed. */
    suspend fun flush() {
        val completion = CompletableDeferred<Unit>()
        writes.send(Write(completion = completion))
        completion.await()
    }

    /** Accepts only ordered, accurate samples representing plausible walking movement. */
    private fun receive(point: RoutePoint, hasAccuracy: Boolean) {
        tick()
        // Show the latest fix and count it even when the route filter rejects that sample.
        val current = state.value.copy(latitude = point.latitude, longitude = point.longitude,
            accuracy = point.accuracy, hasLocation = true, pointCount = state.value.pointCount + 1)
        val previous = lastPoint
        var segment = 0.0
        // Accuracy is metres; 25 m rejects noisy fixes before measuring a segment.
        val rejection = when {
            !hasAccuracy || !point.accuracy.isFinite() || point.accuracy < 0 || point.accuracy > 25f ||
                !point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
                !point.longitude.isFinite() || point.longitude !in -180.0..180.0 -> "Waiting for accurate GPS"
            // Keep global timestamp order even when resume has cleared the segment anchor.
            current.routePoints.lastOrNull()?.let { point.timestamp <= it.timestamp } == true -> "Old GPS point ignored"
            // The first valid position anchors a segment and adds no distance.
            previous == null -> null
            point.timestamp <= previous.timestamp -> "Old GPS point ignored"
            else -> {
                segment = DistanceCalculator.calculateDistance(previous, point)
                val seconds = (point.timestamp - previous.timestamp) / 1000.0
                // Reject jumps above 8 m/s and drift below 3 m, retaining the previous accepted anchor.
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
        // Only accepted segments update distance, the route, and durable checkpoint data.
        lastPoint = point
        val accepted = current.copy(distanceMeters = current.distanceMeters + segment, routePoints = current.routePoints + point)
        mutableState.value = accepted.copy(status = if (accepted.distanceCompleted)
            "Distance requirement completed!" else "Receiving GPS updates")
        checkpoint(RoutePointEntity(sessionId = accepted.sessionId, latitude = point.latitude,
            longitude = point.longitude, timestamp = point.timestamp, accuracy = point.accuracy, startsSegment = previous == null))
        // The distance requirement is automatic, but saving completion remains an explicit action.
        if (accepted.distanceCompleted) stopTracking()
    }

    /** Stops new callbacks and closes the input queue; already queued commands are processed. */
    fun close() {
        if (closed) return
        stopTracking()
        closed = true
        // Accepted writes drain independently of the screen's coroutine scope.
        writes.close()
    }
}
