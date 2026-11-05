package no.ntnu.prog2007.questbook

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity
import no.ntnu.prog2007.questbook.ui.quest.route.*
import no.ntnu.prog2007.questbook.ui.questbook.MemoriesContent
import no.ntnu.prog2007.questbook.ui.questbook.MemoriesUiState
import no.ntnu.prog2007.questbook.ui.theme.QuestBookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises actual screen content with sample positions; never requests live location. */
class SavedRouteScreenTest {
    @get:Rule val compose = createComposeRule()
    private val result = QuestResultEntity("r", "q", "g", "me", "Walk by the lake", "Espen",
        1793879904000L, 3504000, 5100.0, "session")
    private val route = SavedRoute(result.toResult(), listOf(
        RoutePosition(60.79816, 10.68280), RoutePosition(60.79797, 10.68351),
        RoutePosition(60.79784, 10.68416), RoutePosition(60.79775, 10.68452),
        RoutePosition(60.79765, 10.68498), RoutePosition(60.79758, 10.68516),
        RoutePosition(60.79749, 10.68537), RoutePosition(60.79742, 10.68583),
        RoutePosition(60.79730, 10.68620), RoutePosition(60.79714, 10.68677),
        RoutePosition(60.79692, 10.68746), RoutePosition(60.79680, 10.68778)))

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "saved-route-previews").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun memoryOpensRouteAndBackReturnsToHistory() {
        val showingRoute = mutableStateOf(false)
        compose.setContent {
            QuestBookTheme(darkTheme = false) {
                Scaffold { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                        if (showingRoute.value) SavedRouteContent(SavedRouteUiState(false, route),
                            onBack = { showingRoute.value = false })
                        else MemoriesContent(MemoriesUiState(results = listOf(result), loading = false),
                            onRouteClick = { assertEquals("q", it); showingRoute.value = true })
                    }
                }
            }
        }
        compose.onNodeWithText("View route").performClick()
        compose.onNodeWithText("Recorded route").assertExists()
        compose.onNodeWithText("Distance: 5.10 km").assertExists()
        compose.onNodeWithText("Time: 58:24").assertExists()
        compose.onNodeWithContentDescription("Recorded route with 12 GPS points. Start and finish marked. North is up.").assertExists()
        capture("01-saved-route")
        compose.onNodeWithText("‹  Back").performClick()
        compose.onNodeWithText("Memories").assertExists()
    }

    @Test fun singlePointMissingRouteAndErrorAreUsable() {
        val state = mutableStateOf(SavedRouteUiState(false, route.copy(points = route.points.take(1))))
        var retries = 0
        compose.setContent {
            QuestBookTheme(darkTheme = false) {
                Scaffold { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                        SavedRouteContent(state.value, {}, { retries++ })
                    }
                }
            }
        }
        compose.onNodeWithText("Only one GPS point was saved; start and finish are at the same position.").assertExists()
        compose.runOnIdle { state.value = SavedRouteUiState(false, route.copy(points = emptyList())) }
        compose.onNodeWithText("No recorded GPS points are available for this result on this device.").assertExists()
        capture("02-route-unavailable")
        compose.runOnIdle { state.value = SavedRouteUiState(false, error = "Could not load this saved route.") }
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        compose.runOnIdle { state.value = SavedRouteUiState(loading = false) }
        compose.onNodeWithText("This completed quest is not available for your account on this device.").assertExists()
        compose.onNodeWithText("Walk by the lake").assertDoesNotExist()
    }
}
