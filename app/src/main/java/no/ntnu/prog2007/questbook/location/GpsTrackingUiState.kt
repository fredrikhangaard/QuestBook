package no.ntnu.prog2007.questbook.location

data class GpsTrackingUiState(
    val isTracking: Boolean = false,
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
    val progress: Float get() = (distanceMeters / requiredDistanceMeters).toFloat().coerceIn(0f, 1f)
    val distanceCompleted: Boolean get() = distanceMeters >= requiredDistanceMeters
}
