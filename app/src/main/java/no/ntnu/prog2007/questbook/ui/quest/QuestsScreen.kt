package no.ntnu.prog2007.questbook.ui.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.model.Quest

@Composable
fun QuestsScreen(
    onQuestClick: (String) -> Unit,
    questsViewModel: QuestsViewModel = viewModel()
) {
    QuestList(quests = questsViewModel.quests, onQuestClick = onQuestClick)
}

@Composable
internal fun QuestList(quests: List<Quest>, onQuestClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Quests", style = MaterialTheme.typography.headlineMedium)
            Text("Sample quests", style = MaterialTheme.typography.bodyMedium)
        }
        if (quests.isEmpty()) {
            item { Text("No quests yet.") }
        }
        items(quests, key = { it.id }) { quest ->
            Card(
                onClick = { onQuestClick(quest.id) },
                modifier = Modifier.fillMaxWidth().testTag("quest_${quest.id}")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(quest.title, style = MaterialTheme.typography.titleMedium)
                    Text(quest.description)
                    Text("Assigned to: ${quest.assignedTo}")
                    Text("Status: ${quest.status.name.lowercase().replace('_', ' ')}")
                    quest.requiredDistanceKm?.let { Text("Target distance: $it km") }
                    Text("View quest", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
