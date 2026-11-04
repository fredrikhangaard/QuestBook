package no.ntnu.prog2007.questbook

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import no.ntnu.prog2007.questbook.ui.quest.assignment.*
import no.ntnu.prog2007.questbook.ui.theme.QuestBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Ensures the picker selects by ID even if two members share the same display name. */
class AssignQuestDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun memberSelectionEnablesSaveAndKeepsTheCorrectUserId() {
        val state = mutableStateOf(AssignQuestUiState(isOpen = true,
            members = listOf(AssignmentMember("me", "Alex", true), AssignmentMember("other", "Alex", false))))
        var assigned = ""
        compose.setContent {
            QuestBookTheme {
                AssignQuestDialog(state.value, { state.value = state.value.copy(selectedUserId = it) },
                    { assigned = state.value.selectedUserId.orEmpty() }, {}, {})
            }
        }
        compose.onNodeWithText("Assign").assertIsNotEnabled()
        compose.onNodeWithText("Alex (You)").assertIsDisplayed()
        compose.onNodeWithTag("assign_member_other").performClick().assertIsSelected()
        compose.onNodeWithTag("assign_member_me").assertIsNotSelected()
        compose.onNodeWithText("Assign").performClick()
        compose.runOnIdle { assertEquals("other", assigned) }
    }
}
