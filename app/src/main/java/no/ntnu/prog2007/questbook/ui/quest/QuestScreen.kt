package no.ntnu.prog2007.questbook.ui.quest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.util.Locale
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.model.QuestType
import no.ntnu.prog2007.questbook.ui.groups.GroupSummary
import no.ntnu.prog2007.questbook.ui.quest.assignment.AssignQuestViewModel
import no.ntnu.prog2007.questbook.ui.quest.assignment.AssignQuestDialog
import no.ntnu.prog2007.questbook.ui.quest.tracking.QuestTrackingViewModel
import no.ntnu.prog2007.questbook.model.QuestStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies

/** Quest details with foreground GPS tracking for the selected assigned distance quest. */
@Composable
fun QuestScreen(questId: String, onAddPhotoClick: () -> Unit = {}, onBack: () -> Unit = {},
    canAccessGroup: (String) -> Boolean = { false }, group: GroupSummary? = null) {
    val context = LocalContext.current
    // A remembered factory must still read new group membership when Firestore delivers updates.
    val latestGroup by rememberUpdatedState(group)
    val assignment: AssignQuestViewModel = viewModel(key = "assignment-$questId",
        factory = remember(questId) { AssignQuestViewModel.Factory(questId) { latestGroup } })
    val assignmentUi by assignment.uiState.collectAsStateWithLifecycle()
    AssignQuestDialog(assignmentUi, assignment::select, assignment::assign, assignment::dismiss, assignment::open)
    val tracking: QuestTrackingViewModel = viewModel(key = "tracking-$questId",
        factory = remember(questId) { QuestTrackingViewModel.Factory(context, questId, canAccessGroup) })
    val trackingUi by tracking.uiState.collectAsStateWithLifecycle()
    val gps by tracking.gpsState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    // Approximate/coarse permission is insufficient for distance quests; require fine location.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) tracking.start()
        else tracking.permissionDenied()
    }
    // Tracking is deliberately foreground-only; leaving the page pauses and checkpoints it.
    DisposableEffect(owner, tracking) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) tracking.screenStarted()
            if (event == Lifecycle.Event.ON_STOP) tracking.screenStopped()
        }
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) tracking.screenStarted()
        else tracking.screenStopped()
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); tracking.screenStopped() }
    }
    // This timer only refreshes active duration. Location callbacks measure actual movement.
    LaunchedEffect(gps.isTracking) {
        while (gps.isTracking) { delay(1000); tracking.tick() }
    }
    val quests by QuestDependencies.questRepository.quests
        .collectAsStateWithLifecycle()

    val quest = quests.find { it.id == questId }
    val hasGroupAccess = quest?.let { canAccessGroup(it.groupId) } == true
    // Quest/Firestore observations can finish after the ViewModel's first load.
    LaunchedEffect(quest?.id, hasGroupAccess) {
        if (quest != null && hasGroupAccess) tracking.load()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Keep an explicit Back action as well as Android's system Back navigation.
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹  Back") }
        if (quest == null) {
            Text("Quest not found", style = MaterialTheme.typography.titleLarge)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(quest.assignedTo.ifBlank { "?" })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(quest.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (quest.assignedTo.isBlank()) "Unassigned"
                    else "Assigned to ${quest.assignedTo}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(quest.status.displayName(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        // Assignment is available to group members only before tracking has started.
        if (quest.status == QuestStatus.NOT_STARTED && hasGroupAccess) {
            OutlinedButton(onClick = assignment::open,
                enabled = !trackingUi.isBusy && !assignmentUi.isSaving,
                modifier = Modifier.fillMaxWidth()) {
                Text(if (quest.assignedUserId == null) "Assign quest" else "Change assignee")
            }
        }
        // Saved results and live samples use the same units and the selected quest's target.
        val distance = trackingUi.result?.distanceMeters ?: gps.distanceMeters
        val elapsed = trackingUi.result?.durationMillis ?: gps.elapsedMillis
        val progress = if (trackingUi.result != null) 1f else gps.progress
        val target = quest.requiredDistanceKm
        if (target != null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress }, modifier = Modifier.size(192.dp).semantics {
                        contentDescription = "Distance progress: ${(progress * 100).toInt()} percent"
                    }, strokeWidth = 12.dp, color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestActivityIcon(quest)
                    Text(String.format(Locale.US, "%.2f / %.2f km", distance / 1000.0, target), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                QuestActivityIcon(quest, Modifier.size(80.dp))
            }
        }
        Text(quest.description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Stacked metrics remain readable at larger Android font sizes.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QuestMetric("Time", "%d:%02d".format(elapsed / 60000, (elapsed / 1000) % 60))
            QuestMetric("Target", target?.let { "$it km" } ?: "Custom")
            QuestMetric("Avg. pace", if (distance > 0) {
                val secondsPerKm = (elapsed / 1000.0 / (distance / 1000.0)).toLong()
                "%d:%02d /km".format(secondsPerKm / 60, secondsPerKm % 60)
            } else "—")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        // Permission is requested at the user's start action, never when opening details.
        val isDistance = quest.type == QuestType.DISTANCE
        // Compare Firebase user IDs, never display names, before enabling Start/Resume.
        val assignedToMe = quest.assignedUserId != null && quest.assignedUserId == AppServices.authRepository.currentUserId
        trackingUi.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        gps.persistenceError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = tracking::retrySaving) { Text("Retry saving GPS data") }
        }
        if (trackingUi.isLoading) CircularProgressIndicator(Modifier.size(24.dp))
        // Show success only after the result transaction completes, not merely when 100% is reached.
        if (trackingUi.result != null) {
            Text("Quest completed!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("Result and route saved on this device.")
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        } else if (quest.status != QuestStatus.COMPLETED && isDistance) {
            Text(gps.status, style = MaterialTheme.typography.bodyMedium)
            Text("Tracking pauses when you leave this screen.", style = MaterialTheme.typography.bodySmall)
            if (!assignedToMe) Text("This quest must be assigned to you before tracking.")
            if (gps.isTracking) {
                OutlinedButton(onClick = tracking::pause, modifier = Modifier.fillMaxWidth()) { Text("Pause tracking") }
            } else if (!gps.distanceCompleted) {
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        tracking.start()
                    else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, enabled = assignedToMe && hasGroupAccess && !trackingUi.isLoading && !trackingUi.isBusy && !assignmentUi.isSaving && !assignmentUi.isOpen,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(10.dp)) {
                    Text(if (gps.sessionId.isBlank()) "Start Quest" else "Resume tracking")
                }
            }
            if (gps.distanceCompleted) {
                Text("Distance target reached!", color = MaterialTheme.colorScheme.primary)
                Button(onClick = tracking::finish, enabled = !trackingUi.isBusy,
                    modifier = Modifier.fillMaxWidth()) { Text(if (trackingUi.isBusy) "Saving…" else "Finish quest") }
            }
        } else if (!isDistance) {
            Text("This quest does not require GPS tracking.", style = MaterialTheme.typography.bodyMedium)
        }
        if (trackingUi.error != null && !trackingUi.isBusy && gps.sessionId.isBlank()) {
            TextButton(onClick = tracking::load) { Text("Retry loading tracking") }
        }
        OutlinedButton(onClick = onAddPhotoClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(10.dp)) { Text("Add photo") }
        Spacer(Modifier.height(8.dp))
    }
}

/** Small summary metric used beneath the quest progress circle. */
@Composable
private fun QuestMetric(label: String, value: String) {
    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
