package no.ntnu.prog2007.questbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.ui.navigation.QuestBookNavHost
import no.ntnu.prog2007.questbook.ui.theme.QuestBookTheme

/** Initializes application dependencies and displays QuestBook. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        QuestDependencies.initialize(applicationContext)

        enableEdgeToEdge()

        setContent {
            QuestBookTheme {
                QuestBookNavHost()
            }
        }
    }
}