package no.ntnu.prog2007.questbook.ui.quest.route

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Degenerate routes, high-latitude scaling, and date-line crossings must not distort the preview. */
class RouteProjectionTest {
    @Test fun northIsUpAndLongitudeScaleAccountsForLatitude() {
        val route = projectRoute(listOf(RoutePosition(60.0, 10.0), RoutePosition(60.001, 10.002)))
        assertTrue(route.last().x > 0)
        assertTrue(route.last().y < 0)
        assertEquals(1.0, abs(route.last().x / route.last().y), 0.001)
    }
    @Test fun dateLineCrossingStaysLocal() {
        val route = projectRoute(listOf(RoutePosition(0.0, 179.999), RoutePosition(0.0, -179.999)))
        assertTrue(route.last().x in 200.0..230.0)
    }
    @Test fun fitPreservesAspectAndKeepsExtremesInsidePadding() {
        val fitted = fitRoute(listOf(PreviewPosition(0.0, 0.0), PreviewPosition(200.0, 100.0)), 400.0, 300.0, 30.0)
        assertEquals(2.0, (fitted[1].x - fitted[0].x) / (fitted[1].y - fitted[0].y), 0.001)
        assertTrue(fitted.all { it.x in 30.0..370.0 && it.y in 30.0..270.0 })
    }
    @Test fun emptySingleRepeatedAndStraightRoutesRemainFinite() {
        assertTrue(projectRoute(emptyList()).isEmpty())
        val point = PreviewPosition(0.0, 0.0)
        for (route in listOf(listOf(point), listOf(point, point), listOf(point, PreviewPosition(0.0, 100.0)))) {
            val fitted = fitRoute(route, 400.0, 300.0, 30.0)
            assertTrue(fitted.all { it.x.isFinite() && it.y.isFinite() })
        }
        assertEquals(PreviewPosition(200.0, 150.0), fitRoute(listOf(point), 400.0, 300.0, 30.0).single())
    }
}
