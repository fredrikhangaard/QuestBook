package no.ntnu.prog2007.questbook.ui.questbook

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.ntnu.prog2007.questbook.model.QuestResult
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Memories replaces the sample totals with durable, account-scoped completion history. */
@Composable
fun QuestBookScreen(onQuestClick: (String) -> Unit = {}, onRouteClick: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val model: MemoriesViewModel = viewModel(factory = remember { MemoriesViewModel.Factory(context) })
    val state by model.state.collectAsStateWithLifecycle()
    MemoriesContent(state, onRouteClick = onRouteClick, onQuestClick = onQuestClick)
}

/** Stateless rendering allows previews and flow tests to use isolated data instead of real accounts. */
@Composable
fun MemoriesContent(state: MemoriesUiState, onRouteClick: ((String) -> Unit)? = null, onQuestClick: (String) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Memories", style = MaterialTheme.typography.headlineMedium)
            Text("Your completed quests · Saved on this device", style = MaterialTheme.typography.bodySmall)
        }
        if (state.loading) item { CircularProgressIndicator() }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (!state.loading && state.error == null && state.results.isEmpty()) item {
            Text("Your next adventure starts here", style = MaterialTheme.typography.titleLarge)
            Text("Complete an assigned quest to save its result. Add a photo to remember the moment.")
        }
        items(state.results, key = { it.id }) { result ->
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(result.participantName + " completed", style = MaterialTheme.typography.labelLarge)
                    Text(result.questTitle, style = MaterialTheme.typography.titleLarge)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(result.completedAtMillis)),
                        style = MaterialTheme.typography.bodySmall)
                    ResultMetrics(result.toResult())
                    state.photos.filter { it.resultId == result.id }.forEach { photo ->
                        // JPEG decoding runs off the UI thread, and is cancelled when the card leaves composition.
                        val bitmap by produceState<android.graphics.Bitmap?>(null, photo.id) {
                            value = withContext(Dispatchers.Default) {
                                BitmapFactory.decodeByteArray(photo.jpegBytes, 0, photo.jpegBytes.size)
                            }
                        }
                        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Photo for ${result.questTitle}",
                            modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop) }
                        if (photo.caption.isNotBlank()) Text(photo.caption)
                    }
                    if (result.gpsSessionId != null && onRouteClick != null) {
                        OutlinedButton(onClick = { onRouteClick(result.questId) }) { Text("View route") }
                    }
                    TextButton(onClick = { onQuestClick(result.questId) }) { Text("View quest") }
                }
            }
        }
    }
}

/** Success is rendered from the committed result, so reaching the GPS target alone cannot show it. */
@Composable
fun CompletionContent(result: QuestResult, onAddPhoto: () -> Unit, onDone: () -> Unit, onViewRoute: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✓", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text("Quest completed!", style = MaterialTheme.typography.headlineMedium)
        Text(result.questTitle, style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(20.dp)) { ResultMetrics(result) } }
        Text("Result and route saved on this device.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth()) { Text("Add a photo") }
        if (result.gpsSessionId != null && onViewRoute != null) {
            OutlinedButton(onClick = onViewRoute, modifier = Modifier.fillMaxWidth()) { Text("View route") }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("View Memories") }
    }
}

/** Both screens use the same units and paused-time-aware duration snapshot. */
@Composable
internal fun ResultMetrics(result: QuestResult) {
    val seconds = result.durationMillis / 1000
    Text("Time: %d:%02d".format(seconds / 60, seconds % 60))
    result.distanceMeters?.let { distance ->
        Text(String.format(Locale.US, "Distance: %.2f km", distance / 1000))
        if (distance > 0) {
            val pace = (seconds / (distance / 1000)).toLong()
            Text("Average pace: %d:%02d /km".format(pace / 60, pace % 60))
        }
    }
}
