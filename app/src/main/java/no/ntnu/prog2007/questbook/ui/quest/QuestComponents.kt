package no.ntnu.prog2007.questbook.ui.quest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.ntnu.prog2007.questbook.R
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.model.QuestType

/** Human-readable status shared by quest rows and details. */
internal fun QuestStatus.displayName() = when (this) {
    QuestStatus.NOT_STARTED -> "Not started"
    QuestStatus.IN_PROGRESS -> "In progress"
    QuestStatus.COMPLETED -> "Completed"
}

/** Placeholder avatar until member profile photos are available. */
@Composable
internal fun MemberAvatar(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center) {
        Text(name.take(1), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Uses the quest type to choose its activity icon. */
@Composable
internal fun QuestActivityIcon(quest: Quest, modifier: Modifier = Modifier) {
    val icon = when (quest.type) {
        QuestType.DISTANCE -> R.drawable.ic_quest_walk
        QuestType.VISIT_PLACE -> R.drawable.ic_quest_place
        QuestType.WORKOUT -> R.drawable.ic_quest_workout
        QuestType.CUSTOM -> R.drawable.ic_nav_quests
    }
    val color = when (icon) {
        R.drawable.ic_quest_place -> Color(0xFFF8E3DF)
        R.drawable.ic_quest_workout -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Box(modifier.size(44.dp).background(color, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp),
            tint = if (icon == R.drawable.ic_quest_place) Color(0xFFAC554A) else MaterialTheme.colorScheme.primary)
    }
}

/** Compact, full-width quest row matching the group-home wireframe. */
@Composable
internal fun QuestCard(quest: Quest, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("quest_${quest.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuestActivityIcon(quest)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(quest.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(quest.requiredDistanceKm?.let { "Target: $it km" } ?: quest.status.displayName(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Sample quests have no tracked distance. Never show invented progress.
                LinearProgressIndicator(
                    progress = { if (quest.status == QuestStatus.COMPLETED) 1f else 0f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
