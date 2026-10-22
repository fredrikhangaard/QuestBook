
package no.ntnu.prog2007.questbook.ui.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.ntnu.prog2007.questbook.data.repository.FakeQuestRepository

@Composable
fun QuestScreen(questId: String) {
    val quest = FakeQuestRepository().getQuest(questId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (quest == null) {
            Text("Quest not found")
            return@Column
        }

        Text(quest.title)
        Text(quest.description)
        Text("Assigned to: ${quest.assignedTo}")
        Text("Status: ${quest.status}")

        quest.requiredDistanceKm?.let { distance ->
            Text("Target distance: $distance km")
        }

        Button(
            onClick = {},
            enabled = quest.status.name != "COMPLETED"
        ) {
            Text("Start Quest")
        }
    }
}
