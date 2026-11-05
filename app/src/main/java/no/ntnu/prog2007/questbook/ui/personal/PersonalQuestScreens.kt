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

/** Cards are shared by Home and Quests; the persisted assignment defines its calendar window. */
@Composable
fun PersonalQuestSection(state: PersonalQuestsUiState, onQuest: (String) -> Unit, onRefresh: () -> Unit, onNewActivity: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your daily & weekly quests", style = MaterialTheme.typography.titleLarge)
        Text("Solo quests are yours even when you are not in a group.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onNewActivity) { Text("Log personal activity") }
        if (state.loading) CircularProgressIndicator()
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRefresh) { Text("Refresh personal quests") }
        }
        if (!state.loading && state.userId == null) Text("Sign in to receive personal quests.")
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val day = questWindow(now, zone, QuestCadence.DAILY).key
        val week = questWindow(now, zone, QuestCadence.WEEKLY).key
        state.quests.filter { it.assignment.cadence == "DAILY" && it.assignment.periodKey == day }.forEach {
            Text("Today", style = MaterialTheme.typography.titleSmall)
            PersonalQuestCard(it, onQuest)
        }
        if (state.quests.any { it.assignment.cadence == "WEEKLY" && it.assignment.periodKey == week })
            Text("This week's goals", style = MaterialTheme.typography.titleSmall)
        state.quests.filter { it.assignment.cadence == "WEEKLY" && it.assignment.periodKey == week }.forEach {
            PersonalQuestCard(it, onQuest)
        }
        Text("Progress updates when an activity is saved. One activity can advance several matching goals.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonalQuestCard(progress: PersonalQuestProgress, onQuest: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(progress.assignment.title, style = MaterialTheme.typography.titleMedium)
            Text(if (progress.complete) "Completed ✓" else progressText(progress), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onQuest(progress.assignment.questId) }) { Text("View personal quest") }
        }
    }
}

fun progressText(progress: PersonalQuestProgress): String {
    val metric = ActivityMetric.valueOf(progress.assignment.metric)
    val divisor = if (metric == ActivityMetric.WALK_METERS) 1000.0 else 1.0
    val unit = when (metric) {
        ActivityMetric.WALK_METERS -> "km"
        ActivityMetric.ACTIVE_MINUTES -> "min"
        ActivityMetric.PUSHUPS, ActivityMetric.SQUATS, ActivityMetric.BODYWEIGHT_REPS -> "reps"
        ActivityMetric.ACTIVE_DAYS -> "days"
        else -> "visits"
    }
    return when (metric) {
        ActivityMetric.WALK_METERS -> String.format(Locale.US, "%.2f / %.2f %s", progress.value / divisor, progress.assignment.target / divisor, unit)
        ActivityMetric.ACTIVE_MINUTES -> String.format(Locale.US, "%.1f / %.0f %s", progress.value, progress.assignment.target, unit)
        else -> "${progress.value.toInt()} / ${progress.assignment.target.toInt()} $unit"
    }
}

/** Manual visits are explicit self-confirmation; the app does not recognize photo contents or water. */
@Composable
fun PersonalQuestDetail(id: String, state: PersonalQuestsUiState, onBack: () -> Unit,
    onTrack: () -> Unit, onLog: (String, String) -> Unit, onPhoto: () -> Unit, onMemories: () -> Unit,
    onRefresh: () -> Unit, onNewActivity: () -> Unit = {}) {
    val progress = state.quests.find { it.assignment.questId == id }
    var amount by rememberSaveable(id) { mutableStateOf("") }
    var minutes by rememberSaveable(id) { mutableStateOf("") }
    var confirmed by rememberSaveable(id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹  Back") }
        if (state.loading) { CircularProgressIndicator(); return@Column }
        if (progress == null) { Text("This personal quest is not available for your account."); return@Column }
        val row = progress.assignment
        val metric = ActivityMetric.valueOf(row.metric)
        val daily = row.cadence != "WEEKLY"
        val expired = System.currentTimeMillis() >= row.periodEndMillis
        Text(if (row.cadence == "ACTIVITY") "Personal activity" else if (daily) "Daily quest" else "Weekly goal", style = MaterialTheme.typography.labelLarge)
        Text(row.title, style = MaterialTheme.typography.headlineMedium)
        Text(row.description)
        if (row.cadence != "ACTIVITY") Text("Ends " + Instant.ofEpochMilli(row.periodEndMillis).atZone(ZoneId.of(row.zoneId))
            .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")), style = MaterialTheme.typography.bodySmall)
        Text(progressText(progress), style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (progress.complete) {
            Text("Quest completed!", color = MaterialTheme.colorScheme.primary)
            Button(onClick = onPhoto, modifier = Modifier.fillMaxWidth()) { Text("Add a photo (optional)") }
            OutlinedButton(onClick = onMemories, modifier = Modifier.fillMaxWidth()) { Text("View Memories") }
        } else if (!daily) {
            Text(if (expired) "This weekly goal has ended." else "Saved daily, personal, and group activities contribute automatically. You do not log the weekly total again.")
            Button(onClick = onNewActivity) { Text("Log personal activity") }
        } else {
            if (expired) Text("This daily quest has expired. Your saved activity is kept.")
            if (metric == ActivityMetric.WALK_METERS && (!expired || progress.hasSavedSession)) {
                Button(onClick = onTrack, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (progress.hasSavedSession) "Resume saved GPS activity" else "Track remaining distance with GPS")
                }
                if (progress.hasSavedSession) Text("Finish your saved GPS activity before using manual entry.", style = MaterialTheme.typography.bodySmall)
            }
            if (!expired && !progress.hasSavedSession) {
                val visit = metric in listOf(ActivityMetric.WATER_VISITS, ActivityMetric.PARK_VISITS)
                Text("Manual activity", style = MaterialTheme.typography.titleMedium)
                Text("Self-reported. Only log activity you have not already saved with GPS.", style = MaterialTheme.typography.bodySmall)
                if (visit) {
                    Row { Checkbox(confirmed, { confirmed = it }, enabled = !state.busy); Text("I confirm that I visited this place.", Modifier.padding(top = 12.dp)) }
                    Text("No photo is required. You can add one after completing the quest.", style = MaterialTheme.typography.bodySmall)
                } else OutlinedTextField(amount, { amount = it }, enabled = !state.busy,
                    label = { Text(when (metric) { ActivityMetric.WALK_METERS -> "Distance to add (km)"; ActivityMetric.ACTIVE_MINUTES -> "Active minutes to add"; else -> "Repetitions to add" }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (metric != ActivityMetric.ACTIVE_MINUTES) OutlinedTextField(minutes, { minutes = it }, enabled = !state.busy,
                    label = { Text("Active minutes (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Button(onClick = { onLog(if (visit) "1" else amount, minutes) },
                    enabled = !state.busy && if (visit) confirmed else amount.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Saving…" else "Save manual activity")
                }
            }
            if (progress.hasResult) OutlinedButton(onClick = onPhoto) { Text("Add a photo to saved activity") }
        }
        TextButton(onClick = onRefresh) { Text("Refresh progress") }
    }
}


/** Logs a real activity rather than directly editing a weekly counter. */
@Composable
fun LogPersonalActivityScreen(state: PersonalQuestsUiState, onBack: () -> Unit,
    onSave: (ActivityMetric, String, String, Boolean) -> Unit) {
    val kinds = linkedMapOf(ActivityMetric.WALK_METERS to "Walk", ActivityMetric.PUSHUPS to "Push-ups",
        ActivityMetric.SQUATS to "Squats", ActivityMetric.ACTIVE_MINUTES to "Active time",
        ActivityMetric.WATER_VISITS to "Water visit", ActivityMetric.PARK_VISITS to "Park visit")
    var selected by rememberSaveable { mutableStateOf(ActivityMetric.WALK_METERS.name) }
    var amount by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var useGps by rememberSaveable { mutableStateOf(true) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    val metric = ActivityMetric.valueOf(selected)
    val visit = metric in listOf(ActivityMetric.WATER_VISITS, ActivityMetric.PARK_VISITS)
    val gps = metric == ActivityMetric.WALK_METERS && useGps
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹  Back") }
        Text("Log personal activity", style = MaterialTheme.typography.headlineMedium)
        Text("Activities contribute to matching daily and weekly goals.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kinds.forEach { (kind, label) -> FilterChip(selected == kind.name,
                { selected = kind.name; amount = ""; minutes = ""; confirmed = false }, enabled = !state.busy, label = { Text(label) }) }
        }
        if (metric == ActivityMetric.WALK_METERS) Row {
            Checkbox(useGps, { useGps = it }, enabled = !state.busy)
            Text("Use GPS to record this walk", Modifier.padding(top = 12.dp))
        }
        if (visit) Row {
            Checkbox(confirmed, { confirmed = it }, enabled = !state.busy)
            Text("I confirm that I visited this place.", Modifier.padding(top = 12.dp))
        } else OutlinedTextField(amount, { amount = it }, enabled = !state.busy,
            label = { Text(when (metric) { ActivityMetric.WALK_METERS -> "Distance (km)"; ActivityMetric.ACTIVE_MINUTES -> "Active minutes"; else -> "Repetitions" }) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (!gps && metric != ActivityMetric.ACTIVE_MINUTES) OutlinedTextField(minutes, { minutes = it }, enabled = !state.busy,
            label = { Text("Active minutes (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (!gps) Text("Self-reported activity. Do not log an activity already saved with GPS. Photos are optional.", style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(metric, if (visit) "1" else amount, minutes, gps) },
            enabled = !state.busy && if (visit) confirmed else amount.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Saving…" else if (gps) "Start GPS activity" else "Save activity")
        }
    }
}
