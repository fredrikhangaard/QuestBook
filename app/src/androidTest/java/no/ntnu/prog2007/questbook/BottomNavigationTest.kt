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
        compose.onNodeWithTag("quest_1").performScrollTo().performClick()
        compose.onNodeWithTag("tab_today").assertDoesNotExist()
        compose.onNodeWithText("Start Quest").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_today").assertIsSelected()
    }

    @Test fun questCardsOpenCorrectDetailsAndBackReturnsToQuests() {
        signIn()
        compose.onNodeWithTag("tab_quests").performClick()
        listOf("1" to "Walk 5 km", "2" to "Visit a new place", "3" to "Outdoor workout").forEach { (id, title) ->
            compose.onNodeWithTag("quest_$id").performScrollTo().performClick()
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText("Add photo").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("tab_quests").assertDoesNotExist()
            if (id == "3") compose.onNodeWithText("Start Quest").assertIsNotEnabled()
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("tab_quests").assertIsSelected()
        }
    }

    @Test fun homeAndOverviewOpenGroupAndCreateJoinPages() {
        signIn()
        compose.onNodeWithTag("group_mountain-crew").performClick()
        compose.onNodeWithText("Your group page will appear here.").assertIsDisplayed()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("home_add_group").performClick()
        compose.onNodeWithText("Create a new group").performClick()
        compose.onNodeWithText("Create group").assertIsDisplayed()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("tab_groups").performClick()
        compose.onNodeWithText("3 members").assertIsDisplayed()
        compose.onNodeWithText("+ Add new").performClick()
        compose.onNodeWithText("Join a group with code").performClick()
        compose.onNodeWithText("Join group").assertIsDisplayed()
    }

    @Test fun tabSelectionSurvivesRecreation() {
        signIn()
        compose.onNodeWithTag("tab_groups").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab_groups").assertIsSelected()
        compose.onNodeWithTag("group_mountain-crew").assertIsDisplayed()
    }
}
