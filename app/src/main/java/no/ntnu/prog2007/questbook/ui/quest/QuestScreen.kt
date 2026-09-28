package no.ntnu.prog2007.questbook.ui.quest

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun QuestScreen() {
    Column {
        Text("Waterfall Run")
        Text("Run at least 5 km")
        Text("Distance: 0.0 / 5.0 km")

        Button(
            onClick = {}
        ) {
            Text("Start Quest")
        }
    }
}