package no.ntnu.prog2007.questbook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

/** Group flows through the real screens and navigation, with fake repositories instead of Firebase. */
class GroupFlowTest {

    private val groups = FakeGroupRepository(
        usernames = mapOf("test-user" to "Test User"),
        newInviteCode = { "K7MQ2X" }
    )

    /** A signed-in fake user and in-memory groups. Runs before the activity starts. */
    @get:Rule(order = 0)
    val fakes = object : ExternalResource() {
        override fun before() {
            AppServices.authRepository = FakeAuthRepository(signedIn = true)
            AppServices.groupRepository = groups
        }

        override fun after() {
            AppServices.reset()
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun createGroup_opensTheNewGroupAndListsItUnderGroups() {
        // The fake user is already signed in, so the app opens on Home.
        compose.onNodeWithTag("home_add_group").performClick()
        compose.onNodeWithText("Create a new group").performClick()
        compose.onNodeWithTag("create_group_name").performTextInput("Trail Runners")
        compose.onNodeWithTag("create_group_button").performClick()

        compose.onNodeWithText("Trail Runners").assertIsDisplayed()
        compose.onNodeWithTag("group_invite_code").assertIsDisplayed()

        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("tab_groups").performClick()
        compose.onNodeWithText("Trail Runners").assertIsDisplayed()
    }

    @Test
    fun joinGroup_withFriendsCode_opensTheGroup() {
        // A friend's group the test user is not a member of yet.
        groups.createGroup("sara", "Weekend Walks", "")

        compose.onNodeWithTag("home_add_group").performClick()
        compose.onNodeWithText("Join a group with code").performClick()
        compose.onNodeWithTag("join_group_code").performTextInput("k7m-q2x")
        compose.onNodeWithTag("join_group_button").performClick()

        compose.onNodeWithText("Weekend Walks").assertIsDisplayed()
    }

    @Test
    fun joinGroup_wrongCode_showsErrorAndStays() {
        compose.onNodeWithTag("home_add_group").performClick()
        compose.onNodeWithText("Join a group with code").performClick()
        compose.onNodeWithTag("join_group_code").performTextInput("ZZZZZZ")
        compose.onNodeWithTag("join_group_button").performClick()

        compose.onNodeWithText("No group has that code. Check it and try again.").assertIsDisplayed()
        compose.onNodeWithTag("join_group_code").assertIsDisplayed()
    }
}
