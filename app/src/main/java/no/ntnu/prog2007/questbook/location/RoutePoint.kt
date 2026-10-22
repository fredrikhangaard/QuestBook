package no.ntnu.prog2007.questbook.location

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float
)