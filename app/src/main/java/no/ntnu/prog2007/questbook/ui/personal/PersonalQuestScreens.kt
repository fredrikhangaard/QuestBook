package no.ntnu.prog2007.questbook.ui.personal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import no.ntnu.prog2007.questbook.data.personal.*

/** Active solo goals only. Completed challenges have their own section on the Quests page. */
@Composable
fun PersonalQuestSection(state: PersonalQuestsUiState, onQuest: (String) -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your quests", style = MaterialTheme.typography.titleLarge)
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRefresh) { Text("Try again") } }
        if (!state.loading && state.userId == null) Text("Sign in to receive personal quests.")
        val now = System.currentTimeMillis()
        val day = questWindow(now, ZoneId.systemDefault(), QuestCadence.DAILY).key
        val week = questWindow(now, ZoneId.systemDefault(), QuestCadence.WEEKLY).key
        val active = state.quests.filter { !it.complete && it.assignment.metric != ActivityMetric.ACTIVE_MINUTES.name }
        active.filter { it.assignment.cadence == "DAILY" && it.assignment.periodKey == day }.forEach {
            Text("Today", style = MaterialTheme.typography.titleSmall); PersonalQuestCard(it, onQuest)
        }
        val weekly = active.filter { it.assignment.cadence == "WEEKLY" && it.assignment.periodKey == week }
        if (weekly.isNotEmpty()) Text("This week's goals", style = MaterialTheme.typography.titleSmall)
        weekly.forEach { PersonalQuestCard(it, onQuest) }
    }
}

@Composable
fun PersonalQuestCard(progress: PersonalQuestProgress, onQuest: (String) -> Unit) {
    Card(onClick = { onQuest(progress.assignment.questId) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(progress.assignment.title, style = MaterialTheme.typography.titleMedium)
            Text(if (progress.complete) "Completed ✓" else progressText(progress), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onQuest(progress.assignment.questId) }) { Text(if (progress.complete) "View completed quest" else "View quest") }
        }
    }
}

fun progressText(progress: PersonalQuestProgress): String = metricProgressText(ActivityMetric.valueOf(progress.assignment.metric), progress.value, progress.assignment.target)
fun metricProgressText(metric: ActivityMetric, value: Double, target: Double): String = when (metric) {
    ActivityMetric.WALK_METERS -> String.format(Locale.US, "%.2f / %.2f km", value / 1000, target / 1000)
    ActivityMetric.ACTIVE_MINUTES -> String.format(Locale.US, "%.1f / %.0f min", value, target)
    else -> "${value.toInt()} / ${target.toInt()} ${when(metric) {
        ActivityMetric.BODYWEIGHT_REPS, ActivityMetric.PUSHUPS, ActivityMetric.SQUATS -> "reps"
        ActivityMetric.ACTIVE_DAYS -> "days"; ActivityMetric.CONFIRMATIONS -> "completed"; else -> "visits" }}"
}

/** Buttons edit an unsaved set. Only Save records it, so +10 is never counted twice as two events. */
@Composable
fun RepetitionEntry(remaining: Double, busy: Boolean, onSave: (String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 5, 10).forEach { step -> OutlinedButton(enabled = !busy && remaining >= 1,
            onClick = { amount = minOf(remaining.toInt(), (amount.toIntOrNull() ?: 0) + step).toString() }) { Text("+$step") } }
    }
    OutlinedTextField(amount, { amount = it }, enabled = !busy, label = { Text("Repetitions to add") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    val value = amount.toIntOrNull()
    Button(onClick = { onSave(amount); amount = "" }, enabled = !busy && value != null && value > 0 && value <= remaining,
        modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save repetitions") }
}

/** Quest-specific controls replace the generic logger. Walking always uses a finite GPS session. */
@Composable
fun QuestActivityControls(metric: ActivityMetric, remaining: Double, busy: Boolean, savedSession: Boolean,
    onTrack: () -> Unit, onLog: (String) -> Unit) {
    when (metric) {
        ActivityMetric.WALK_METERS -> Button(onClick = onTrack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (savedSession) "Resume quest" else "Start quest")
        }
        ActivityMetric.BODYWEIGHT_REPS, ActivityMetric.PUSHUPS, ActivityMetric.SQUATS -> RepetitionEntry(remaining, busy, onLog)
        ActivityMetric.ACTIVE_DAYS -> Text("Complete a walking, exercise or visit quest to add an active day.", style = MaterialTheme.typography.bodyMedium)
        ActivityMetric.ACTIVE_MINUTES -> Text("Time quests have been retired. Your recorded activity time is kept in summaries.")
        else -> {
            var confirmed by rememberSaveable { mutableStateOf(false) }
            Row {
                Checkbox(confirmed, { confirmed = it }, enabled = !busy)
                Text(if (metric == ActivityMetric.CONFIRMATIONS) "I completed this task." else "I confirm that I visited this place.", Modifier.padding(top = 12.dp))
            }
            Button(onClick = { onLog("1"); confirmed = false }, enabled = confirmed && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Saving…" else if (metric == ActivityMetric.CONFIRMATIONS) "Complete quest" else "Confirm visit")
            }
        }
    }
}

/** Shared attempt pages append their member completion gallery through extraContent. */
@Composable
fun PersonalQuestDetail(id: String, state: PersonalQuestsUiState, onBack: () -> Unit,
    onTrack: () -> Unit, onLog: (String, String) -> Unit, onPhoto: () -> Unit, onMemories: () -> Unit,
    onRefresh: () -> Unit, extraContent: @Composable () -> Unit = {}) {
    val progress = state.quests.find { it.assignment.questId == id }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        if (state.loading) { CircularProgressIndicator(); return@Column }
        if (progress == null) { Text("This quest is not available for your account."); return@Column }
        val row = progress.assignment
        val expired = System.currentTimeMillis() >= row.periodEndMillis
        Text(when(row.cadence) { "WEEKLY" -> "Weekly goal"; "ATTEMPT" -> "Group quest"; "DAILY" -> "Daily quest"; else -> "Quest" }, style = MaterialTheme.typography.labelLarge)
        Text(row.title, style = MaterialTheme.typography.headlineMedium)
        if (row.cadence !in listOf("WEEKLY", "GROUP_AWARD") && row.description.isNotBlank()) Text(row.description)
        if (row.periodEndMillis != Long.MAX_VALUE) Text("Ends " + Instant.ofEpochMilli(row.periodEndMillis).atZone(ZoneId.of(row.zoneId))
            .format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.bodySmall)
        Text(progressText(progress), style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRefresh) { Text("Try again") } }
        if (progress.complete || progress.hasResult) {
            Text(if (!progress.complete && expired) "Activity saved after the deadline" else "Quest completed!", color = MaterialTheme.colorScheme.primary)
            if (progress.hasSavedSession && row.metric == ActivityMetric.WALK_METERS.name)
                OutlinedButton(onClick = onTrack, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Resume saved walk") }
            Button(onClick = onPhoto, modifier = Modifier.fillMaxWidth()) { Text("Add a photo (optional)") }
            OutlinedButton(onClick = onMemories, modifier = Modifier.fillMaxWidth()) { Text("View Memories") }
        } else if (!expired || progress.hasSavedSession) {
            if (expired) Text("This quest has ended. You can still finish your saved walk.")
            QuestActivityControls(ActivityMetric.valueOf(row.metric), (row.target - progress.value).coerceAtLeast(0.0),
                state.busy, progress.hasSavedSession, onTrack) { onLog(it, "") }
            Text("Photos are optional after completion.", style = MaterialTheme.typography.bodySmall)
        } else Text("This quest has ended.")
        extraContent()
    }
}

/** Old saved navigation entries lead back to quests instead of exposing an unrestricted activity logger. */
@Composable
fun LogPersonalActivityScreen(onBack: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Choose a quest to record your activity.")
        Button(onClick = onBack) { Text("Back to quests") }
    }
}
