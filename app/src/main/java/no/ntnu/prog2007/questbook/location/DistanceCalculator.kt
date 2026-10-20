package no.ntnu.prog2007.questbook.location

import kotlin.math.*

object DistanceCalculator {

    fun calculateDistance(
        start: RoutePoint,
        end: RoutePoint
    ): Double {

        val earthRadius = 6371000.0

        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)

        val deltaLat = Math.toRadians(
            end.latitude - start.latitude
        )

        val deltaLon = Math.toRadians(
            end.longitude - start.longitude
        )

        val a =
            sin(deltaLat / 2).pow(2) +
                    cos(lat1) * cos(lat2) *
                    sin(deltaLon / 2).pow(2)

        val c = 2 * atan2(
            sqrt(a),
            sqrt(1 - a)
        )

        return earthRadius * c
    }
}