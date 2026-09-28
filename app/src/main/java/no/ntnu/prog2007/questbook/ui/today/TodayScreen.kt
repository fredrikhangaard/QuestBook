package no.ntnu.prog2007.questbook.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TodayScreen(
    onQuestClick: () -> Unit,
    onQuestBookClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text("QuestBook")
        Text("Today's Quests")

        Text("🏃 Run 5 km")
        Text("🌲 Visit somewhere new")
        Text("📷 Take a quest photo")

        Button(
            onClick = onQuestClick
        ) {
            Text("Open Quest")
        }

        Button(
            onClick = onQuestBookClick
        ) {
            Text("Open QuestBook")
        }
    }
}