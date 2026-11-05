package no.ntnu.prog2007.questbook.ui.quest.route

import kotlin.math.*
import no.ntnu.prog2007.questbook.model.QuestResult

/** Route positions use degrees; the preview coordinates below are approximate local metres. */
data class RoutePosition(val latitude: Double, val longitude: Double, val startsSegment: Boolean = false)

/** A route always starts at its first point, including legacy routes with no boundary metadata. */
fun routeSegmentRanges(points: List<RoutePosition>): List<IntRange> {
    if (points.isEmpty()) return emptyList()
    val starts = listOf(0) + points.indices.drop(1).filter { points[it].startsSegment }
    return starts.mapIndexed { index, start -> start until starts.getOrElse(index + 1) { points.size } }
}
data class SavedRoute(val result: QuestResult, val points: List<RoutePosition>)
data class PreviewPosition(val x: Double, val y: Double)

/**
 * North-up local projection for recorded walking routes, not a navigational map.
 * Unwrap longitude at the date line so a short crossing does not stretch around the world.
 * Latitude-adjusted horizontal scale preserves the shape of routes at Norwegian latitudes.
 */
fun projectRoute(points: List<RoutePosition>): List<PreviewPosition> {
    if (points.isEmpty()) return emptyList()
    val reference = points.first()
    val latitudeScale = cos(Math.toRadians(points.map { it.latitude }.average())).coerceAtLeast(0.000001)
    var previousLongitude = reference.longitude
    var longitudeOffset = 0.0
    return points.map { point ->
        val delta = ((point.longitude - previousLongitude + 540.0) % 360.0) - 180.0
        longitudeOffset += delta
        previousLongitude = point.longitude
        PreviewPosition(Math.toRadians(longitudeOffset) * 6371000 * latitudeScale,
            -Math.toRadians(point.latitude - reference.latitude) * 6371000)
    }
}

/** Fits both axes with one scale; straight, closed, and single-point routes remain finite. */
fun fitRoute(points: List<PreviewPosition>, width: Double, height: Double, padding: Double): List<PreviewPosition> {
    if (points.isEmpty() || width <= 0 || height <= 0) return emptyList()
    val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
    val availableWidth = (width - padding * 2).coerceAtLeast(1.0)
    val availableHeight = (height - padding * 2).coerceAtLeast(1.0)
    val scale = min(availableWidth / (maxX - minX).coerceAtLeast(0.001),
        availableHeight / (maxY - minY).coerceAtLeast(0.001))
    return points.map { PreviewPosition((it.x - (minX + maxX) / 2) * scale + width / 2,
        (it.y - (minY + maxY) / 2) * scale + height / 2) }
}
