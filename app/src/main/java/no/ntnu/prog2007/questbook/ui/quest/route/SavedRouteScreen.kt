package no.ntnu.prog2007.questbook.ui.quest.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.ui.questbook.ResultMetrics
import java.text.DateFormat
import java.util.Date

/** Read-only route review: no location permission, network request, or tracking session is started. */
@Composable
fun SavedRouteScreen(questId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val model: SavedRouteViewModel = viewModel(key = "saved-route-$questId",
        factory = remember(questId) { SavedRouteViewModel.Factory(context, questId) })
    val state by model.state.collectAsStateWithLifecycle()
    SavedRouteContent(state, onBack, model::retry)
}

@Composable
fun SavedRouteContent(state: SavedRouteUiState, onBack: () -> Unit, onRetry: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹  Back") }
        Text("Recorded route", style = MaterialTheme.typography.headlineMedium)
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
            state.route == null -> Text("This completed quest is not available for your account on this device.")
            else -> {
                val route = state.route
                Text(route.result.questTitle, style = MaterialTheme.typography.titleLarge)
                Text(route.result.participantName + " · " + DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.SHORT).format(Date(route.result.completedAtMillis)),
                    style = MaterialTheme.typography.bodySmall)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) { ResultMetrics(route.result) }
                }
                when {
                    route.result.gpsSessionId == null -> Text("This quest has no GPS route.")
                    route.points.isEmpty() -> Text("No recorded GPS points are available for this result on this device.")
                    else -> {
                        RouteDrawing(route.points)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Text("Start")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                Text("Finish")
                            }
                        }
                        Text("${route.points.size} recorded GPS points", style = MaterialTheme.typography.bodySmall)
                        val sections = remember(route.points) { routeSegmentRanges(route.points).size }
                        if (sections > 1) {
                            Text("$sections tracked sections", style = MaterialTheme.typography.labelLarge)
                            Text("Movement while paused is not connected.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (route.points.size == 1) Text("Only one GPS point was saved; start and finish are at the same position.")
                        Text("Offline route preview · North is up. No street map is shown.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One aspect-preserving scale fits the saved path; markers also work for a closed-loop route. */
@Composable
private fun RouteDrawing(points: List<RoutePosition>) {
    val projected = remember(points) { projectRoute(points) }
    val sections = remember(points) { routeSegmentRanges(points) }
    val line = MaterialTheme.colorScheme.primary
    val finish = MaterialTheme.colorScheme.error
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = "Recorded route with ${points.size} GPS points. Start and finish marked. North is up."
        }) {
            for (i in 1..5) {
                drawLine(grid, Offset(size.width * i / 6, 0f), Offset(size.width * i / 6, size.height))
                drawLine(grid, Offset(0f, size.height * i / 6), Offset(size.width, size.height * i / 6))
            }
            val fitted = fitRoute(projected, size.width.toDouble(), size.height.toDouble(), 32.dp.toPx().toDouble())
            val offsets = fitted.map { Offset(it.x.toFloat(), it.y.toFloat()) }
            // Fit all points together, but start a separate path for every uninterrupted section.
            // A one-point section is still visible and must not be joined to either neighbour.
            sections.forEach { section ->
                if (section.count() == 1) {
                    drawCircle(line, 3.dp.toPx(), offsets[section.first])
                } else {
                    val path = Path().apply {
                        moveTo(offsets[section.first].x, offsets[section.first].y)
                        for (index in (section.first + 1)..section.last) {
                            lineTo(offsets[index].x, offsets[index].y)
                        }
                    }
                    drawPath(path, line, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            offsets.firstOrNull()?.let {
                drawCircle(surface, 11.dp.toPx(), it)
                drawCircle(line, 8.dp.toPx(), it)
            }
            offsets.lastOrNull()?.let {
                drawCircle(surface, 7.dp.toPx(), it)
                drawCircle(finish, 5.dp.toPx(), it)
            }
        }
        Text("N ↑", Modifier.align(Alignment.TopEnd).padding(12.dp), style = MaterialTheme.typography.labelLarge)
    }
}
