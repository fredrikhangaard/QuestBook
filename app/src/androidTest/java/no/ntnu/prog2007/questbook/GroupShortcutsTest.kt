package no.ntnu.prog2007.questbook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import no.ntnu.prog2007.questbook.ui.groups.GroupSummary
import no.ntnu.prog2007.questbook.ui.groups.HomeGroups
import no.ntnu.prog2007.questbook.ui.theme.QuestBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Covers the three Home layouts independently of group backend availability. */
class GroupShortcutsTest {
    @get:Rule val compose = createComposeRule()
    private val first = GroupSummary("one", "Mountain Crew", listOf("Emma", "Jonas"), "Outdoor quests")
    private val second = GroupSummary("two", "Weekend Walks", listOf("Sara"), "Weekend adventures")

    @Test fun oneGroupShowsMemberNamesAndOpensGroup() {
        var selected = ""
        compose.setContent { QuestBookTheme { HomeGroups(listOf(first), { selected = it }, {}) } }
        compose.onNodeWithText("Emma, Jonas").assertIsDisplayed()
        compose.onNodeWithTag("group_one").performClick()
        compose.runOnIdle { assertEquals("one", selected) }
    }

    @Test fun multipleGroupsShowNamesAndOpenTheChosenGroup() {
        var selected = ""
        compose.setContent { QuestBookTheme { HomeGroups(listOf(first, second), { selected = it }, {}) } }
        compose.onNodeWithText("Emma, Jonas").assertDoesNotExist()
        compose.onNodeWithText("Weekend Walks").assertIsDisplayed()
        compose.onNodeWithTag("group_two").performClick()
        compose.runOnIdle { assertEquals("two", selected) }
    }

    @Test fun emptyStateStillOffersCreateOrJoin() {
        var adding = false
        compose.setContent { QuestBookTheme { HomeGroups(emptyList(), {}, { adding = true }) } }
        compose.onNodeWithText("Join or create a group to get started.").assertIsDisplayed()
        compose.onNodeWithTag("home_add_group").performClick()
        compose.runOnIdle { assertTrue(adding) }
    }
}
