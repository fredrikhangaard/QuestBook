package no.ntnu.prog2007.questbook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class BottomNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun signIn() {
        compose.onNodeWithTag("tab_today").assertDoesNotExist()
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Email").performTextInput("test@example.com")
        compose.onNodeWithText("Password").performTextInput("test-password")
        compose.onAllNodesWithText("Sign in").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithTag("tab_today").assertIsSelected()
    }

    @Test fun tabsNavigateAndRepeatedTapsDoNotStack() {
        signIn()
        listOf("groups", "quests", "questbook", "profile").forEach { route ->
            compose.onNodeWithTag("tab_$route").performClick().assertIsSelected()
        }
        compose.onNodeWithText("Your profile and settings will appear here.").assertIsDisplayed()
        repeat(3) { compose.onNodeWithTag("tab_profile").performClick() }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_today").assertIsSelected()
    }

    @Test fun homeLinksAndQuestBackStillWork() {
        signIn()
        compose.onNodeWithText("Open QuestBook").performClick()
        compose.onNodeWithTag("tab_questbook").assertIsSelected()
        compose.onNodeWithText("This week").assertIsDisplayed()
        compose.onNodeWithTag("tab_today").performClick()
        compose.onAllNodesWithText("View Quest").onFirst().performClick()
        compose.onNodeWithTag("tab_today").assertDoesNotExist()
        compose.onNodeWithText("Start Quest").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_today").assertIsSelected()
    }

    @Test fun tabSelectionSurvivesRecreation() {
        signIn()
        compose.onNodeWithTag("tab_groups").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab_groups").assertIsSelected()
        compose.onNodeWithText("Your groups will appear here.").assertIsDisplayed()
    }
}
