
package no.ntnu.prog2007.questbook.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TodayScreen(
    onQuestClick: (String) -> Unit,
    onQuestBookClick: () -> Unit,
    todayViewModel: TodayViewModel = viewModel()
) {
    val uiState by todayViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("QuestBook")
        Text("Today's Quests")

        uiState.quests.forEach { quest ->
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(quest.title)
                    Text(quest.description)
                    Text("Assigned to: ${quest.assignedTo}")
                    Text("Status: ${quest.status}")

                    Button(
                        onClick = { onQuestClick(quest.id) }
                    ) {
                        Text("View Quest")
                    }
                }
            }
        }

        Button(onClick = onQuestBookClick) {
            Text("Open QuestBook")
        }
    }
}
