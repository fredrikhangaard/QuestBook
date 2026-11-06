package no.ntnu.prog2007.questbook.ui.challenges

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.ui.personal.*

/** One shared page wraps the current member's owned attempt and everyone's visible completion cards. */
@Composable
fun GroupChallengeScreen(id: String, state: GroupChallengesUiState, personal: PersonalQuestsUiState,
    onBack: () -> Unit, onOpen: () -> Unit, onTrack: (String) -> Unit, onLog: (String, String) -> Unit,
    onPhoto: (String) -> Unit, onMemories: () -> Unit, onWeeklyTrack: () -> Unit, onWeeklyLog: (String) -> Unit,
    onRetry: () -> Unit) {
    val definition = state.snapshot.challenges.find { it.questId == id }
    val parent = QuestDependencies.questRepository.quests.value.find { it.id == id }
    val attempt = state.snapshot.attempts.find { it.challengeId == id && it.userId == state.userId }
    val allowed = definition != null && state.groups.any { it.id == definition.groupId && state.userId in it.memberIds }
    LaunchedEffect(id, state.userId, allowed, definition?.assigneeId, definition?.endMillis, state.busy, state.error) {
        if (!state.busy && state.error == null && allowed && definition.kind != "WEEKLY" && (definition.assigneeId == null || definition.assigneeId == state.userId) &&
            attempt == null && System.currentTimeMillis() < definition.endMillis) onOpen()
    }
    if (allowed && definition.kind != "WEEKLY" && attempt != null && personal.quests.any { it.assignment.questId == attempt.attemptId }) {
        PersonalQuestDetail(attempt.attemptId, personal.copy(busy = personal.busy || state.busy, error = state.error ?: personal.error), onBack,
            onTrack = { onTrack(attempt.attemptId) }, onLog = { quantity, _ -> onLog(attempt.attemptId, quantity) },
            onPhoto = { onPhoto(attempt.attemptId) }, onMemories = onMemories, onRefresh = onRetry,
            extraContent = { CompletionGallery(id, state) })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        if (state.loading) { CircularProgressIndicator(); return@Column }
        if (!allowed || parent == null) { Text("This group quest is not available to you."); return@Column }
        Text(if (definition.kind == "WEEKLY") "Weekly group goal" else "Group quest", style = MaterialTheme.typography.labelLarge)
        Text(parent.title, style = MaterialTheme.typography.headlineMedium)
        if (definition.kind == "WEEKLY") {
            val value = state.snapshot.progress[id] ?: 0.0
            val complete = value >= definition.target
            val savedWalk = state.snapshot.pendingWalks[id] != null
            Text(metricProgressText(ActivityMetric.valueOf(definition.metric), value, definition.target), style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = { (value / definition.target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("Ends " + Instant.ofEpochMilli(definition.endMillis).atZone(ZoneId.of(definition.zoneId)).format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.bodySmall)
            Text("Target fixed for ${definition.memberCount} members", style = MaterialTheme.typography.bodySmall)
            if (complete) {
                Text("Group goal completed!", color = MaterialTheme.colorScheme.primary)
                if (attempt != null) Button(onClick = { onPhoto(attempt.attemptId) }) { Text("Add a photo (optional)") }
            }
            if (savedWalk || (!complete && System.currentTimeMillis() < definition.endMillis)) {
                QuestActivityControls(ActivityMetric.valueOf(definition.metric), definition.target - value,
                    state.busy, savedWalk, onWeeklyTrack, onWeeklyLog)
            } else if (!complete) Text("This weekly goal has ended.")
            val members = state.snapshot.contributions[id].orEmpty()
            Text("Contributions", style = MaterialTheme.typography.titleMedium)
            if (members.values.none { it > 0 }) Text("Complete a matching quest to contribute.")
            members.filterValues { it > 0 }.forEach { (uid, progress) ->
                val name = if (uid == state.userId) "You" else state.names[uid] ?: "Former member"
                Text("$name · " + metricProgressText(ActivityMetric.valueOf(definition.metric), progress, definition.target).substringBefore(" /") +
                    when(ActivityMetric.valueOf(definition.metric)) { ActivityMetric.WALK_METERS -> " km"; ActivityMetric.BODYWEIGHT_REPS -> " reps"; else -> " visits" })
            }
        } else {
            if (parent.description.isNotBlank()) Text(parent.description)
            if (definition.assigneeId != null && definition.assigneeId != state.userId)
                Text("Assigned to ${state.names[definition.assigneeId] ?: parent.assignedTo}.")
            else if (System.currentTimeMillis() >= definition.endMillis) Text("This quest has ended.")
            else CircularProgressIndicator()
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text("Try again") } }
        val hasCompletions = state.snapshot.attempts.filter { it.challengeId == id }
            .any { a -> state.snapshot.results.any { it.questId == a.attemptId } }
        if (definition.kind != "WEEKLY" || hasCompletions) CompletionGallery(id, state)
    }
}

/** Only completions and optional photos are shared. Other members' precise GPS routes stay private. */
@Composable
fun CompletionGallery(id: String, state: GroupChallengesUiState) {
    val attempts = state.snapshot.attempts.filter { it.challengeId == id }.map { it.attemptId }.toSet()
    val results = state.snapshot.results.filter { it.questId in attempts }.sortedByDescending { it.completedAtMillis }
    HorizontalDivider()
    Text("Member completions", style = MaterialTheme.typography.titleMedium)
    if (results.isEmpty()) Text("No one has completed this quest yet.")
    results.forEach { result ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val deadline = state.snapshot.challenges.find { it.questId == id }?.endMillis ?: Long.MAX_VALUE
                val label = if (result.completedAtMillis >= deadline) "finished after the deadline" else "completed ✓"
                Text("${if (result.userId == state.userId) "You" else state.names[result.userId] ?: result.participantName} $label")
                no.ntnu.prog2007.questbook.ui.questbook.ResultMetrics(result.toResult())
                state.snapshot.photos.filter { it.resultId == result.id }.forEach { photo ->
                    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.id) {
                        value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(photo.jpegBytes, 0, photo.jpegBytes.size) }
                    }
                    bitmap?.let { Image(it.asImageBitmap(), "Photo for ${result.questTitle}", Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop) }
                    if (photo.caption.isNotBlank()) Text(photo.caption)
                }
            }
        }
    }
}
