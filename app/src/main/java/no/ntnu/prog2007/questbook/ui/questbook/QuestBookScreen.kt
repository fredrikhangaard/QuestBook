package no.ntnu.prog2007.questbook.ui.questbook

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun QuestBookScreen() {
    Column {
        Text("QuestBook")
        Text("This week")
        Text("14 quests completed")
        Text("32.4 km travelled")
    }
}