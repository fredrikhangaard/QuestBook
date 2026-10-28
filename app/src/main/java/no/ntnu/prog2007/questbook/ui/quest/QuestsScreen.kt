package no.ntnu.prog2007.questbook.ui.quest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus

/** Displays sample quests with status filters and links to quest details. */
@Composable
fun QuestsScreen(onQuestClick: (String) -> Unit, questsViewModel: QuestsViewModel = viewModel()) {
    QuestList(questsViewModel.quests, onQuestClick)
}

/** Scrollable quest rows; filter selection survives tab switches and recreation. */
@Composable
internal fun QuestList(quests: List<Quest>, onQuestClick: (String) -> Unit) {
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val visible = quests.filter {
        when (selectedFilter) {
            "Active" -> it.status != QuestStatus.COMPLETED
            "Completed" -> it.status == QuestStatus.COMPLETED
            else -> true
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quests", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Sample quests", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // FlowRow keeps controls usable with large accessibility fonts.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Active", "Completed").forEach { filter ->
                        FilterChip(selected = selectedFilter == filter, onClick = { selectedFilter = filter },
                            label = { Text(filter) })
                    }
                }
            }
        }
        if (visible.isEmpty()) item {
            Text("No quests here yet.", modifier = Modifier.padding(vertical = 24.dp))
        }
        items(visible, key = { it.id }) { quest -> QuestCard(quest) { onQuestClick(quest.id) } }
    }
}
