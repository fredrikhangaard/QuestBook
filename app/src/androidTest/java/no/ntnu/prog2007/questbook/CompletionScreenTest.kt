package no.ntnu.prog2007.questbook

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import no.ntnu.prog2007.questbook.model.QuestResult
import no.ntnu.prog2007.questbook.ui.questbook.CompletionContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Checks the completion actions and the units shown for a committed result. */
class CompletionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedStatsAndBothNextStepsAreAvailable() {
        var photo = false
        var memories = false
        compose.setContent {
            MaterialTheme {
                CompletionContent(QuestResult("r", "q", "g", "u", "Morning walk", "Me", 100000, 60000, 1000.0, "r"),
                    onAddPhoto = { photo = true }, onDone = { memories = true })
            }
        }
        compose.onNodeWithText("Quest completed!").assertExists()
        compose.onNodeWithText("Distance: 1.00 km").assertExists()
        compose.onNodeWithText("Time: 1:00").assertExists()
        compose.onNodeWithText("Add a photo").performClick()
        compose.onNodeWithText("View Memories").performClick()
        assertTrue(photo)
        assertTrue(memories)
    }
}
