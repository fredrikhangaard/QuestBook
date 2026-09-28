package no.ntnu.prog2007.questbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import no.ntnu.prog2007.questbook.ui.theme.QuestBookTheme
import no.ntnu.prog2007.questbook.ui.today.TodayScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            QuestBookTheme {
                TodayScreen()
            }
        }
    }
}