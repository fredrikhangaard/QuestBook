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
import no.ntnu.prog2007.questbook.data.repository.FakeQuestRepository
import no.ntnu.prog2007.questbook.model.QuestStatus

/** Quest details arranged like the wireframe; tracking remains a separate integration task. */
@Composable
fun QuestScreen(questId: String, onAddPhotoClick: () -> Unit = {}, onBack: () -> Unit = {}) {
    val quest = remember(questId) { FakeQuestRepository().getQuest(questId) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Keep an explicit Back action as well as Android's system Back navigation.
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹  Back") }
        if (quest == null) {
            Text("Quest not found", style = MaterialTheme.typography.titleLarge)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(quest.assignedTo)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(quest.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Assigned to ${quest.assignedTo}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(quest.status.displayName(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        // No distance or duration is fabricated for sample quests that have not been tracked.
        val target = quest.requiredDistanceKm
        if (target != null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 0f }, modifier = Modifier.size(192.dp).semantics {
                        contentDescription = "Distance progress: not tracked yet"
                    }, strokeWidth = 12.dp, color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestActivityIcon(quest)
                    Text("— / $target km", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Not tracked yet", style = MaterialTheme.typography.bodySmall,
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
            QuestMetric("Time", "—")
            QuestMetric("Target", target?.let { "$it km" } ?: "Custom")
            QuestMetric("Avg. pace", "—")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        // Preserve existing actions; photo navigation continues to the merged photo feature.
        Button(onClick = {}, enabled = quest.status != QuestStatus.COMPLETED,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(10.dp)) {
            Text("Start Quest")
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
