package no.ntnu.prog2007.questbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import no.ntnu.prog2007.questbook.ui.navigation.QuestBookNavHost
import no.ntnu.prog2007.questbook.ui.theme.QuestBookTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            QuestBookTheme {
                QuestBookNavHost()
            }
        }
    }
}