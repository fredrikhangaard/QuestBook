package no.ntnu.prog2007.questbook.location

/**
 * Immutable snapshot read by Compose and used when checkpointing a route.
 *
 * @property sessionId stable ID joining this session to route_points and the saved result.
 * @property elapsedMillis time spent tracking, excluding pauses; not the GPS fix timestamp.
 * @property persistenceError a failed database write, separate from the current GPS status.
 * @property distanceMeters total accepted movement; rejected samples never increase it.
 * @property pointCount received samples in this controller, including rejected samples.
 * Restoring from Room initializes this count from the accepted points stored there.
 * @property routePoints accepted positions in reception order, including resume anchors.
 * @property requiredDistanceMeters the selected quest's target, converted from kilometres.
 */
data class GpsTrackingUiState(
    val isTracking: Boolean = false,
    val sessionId: String = "",
    val elapsedMillis: Long = 0L,
    val persistenceError: String? = null,
    val distanceMeters: Double = 0.0,
    val pointCount: Int = 0,
    val status: String = "Ready to track",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val hasLocation: Boolean = false,
    val routePoints: List<RoutePoint> = emptyList(),
    val requiredDistanceMeters: Double = 5000.0
) {
    /** The progress indicator stays between 0 and 1 even if the last segment overshoots. */
    val progress: Float get() = if (requiredDistanceMeters > 0.0)
        (distanceMeters / requiredDistanceMeters).toFloat().coerceIn(0f, 1f) else 0f
    /** Reaching the target completes the requirement; the user still chooses Finish quest. */
    val distanceCompleted: Boolean get() = distanceMeters >= requiredDistanceMeters
}
