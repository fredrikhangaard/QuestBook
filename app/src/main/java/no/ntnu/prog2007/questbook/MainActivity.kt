package no.ntnu.prog2007.questbook

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import no.ntnu.prog2007.questbook.location.QuestTrackingService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.ui.navigation.QuestBookNavHost
import no.ntnu.prog2007.questbook.ui.theme.QuestBookTheme

/** Initializes application dependencies and displays QuestBook. */
class MainActivity : ComponentActivity() {
    // Consume each notification tap once. Recomposition must not repeatedly reopen the route.
    private var trackingRequest by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        QuestDependencies.initialize(applicationContext)
        readTrackingIntent(intent)

        enableEdgeToEdge()

        setContent {
            QuestBookTheme {
                QuestBookNavHost(trackingRequest = trackingRequest, onTrackingOpened = { trackingRequest = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle this one-shot request without replacing the Activity's original launch Intent.
        readTrackingIntent(intent)
    }

    private fun readTrackingIntent(intent: Intent?) {
        val quest = intent?.getStringExtra(QuestTrackingService.EXTRA_QUEST)
        val user = intent?.getStringExtra(QuestTrackingService.EXTRA_USER)
        if (!quest.isNullOrBlank() && !user.isNullOrBlank()) trackingRequest = user to quest
        intent?.removeExtra(QuestTrackingService.EXTRA_QUEST)
        intent?.removeExtra(QuestTrackingService.EXTRA_USER)
    }
}
