package no.ntnu.prog2007.questbook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.challenges.GroupChallengeStore
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import no.ntnu.prog2007.questbook.data.personal.ActivityMetric
import no.ntnu.prog2007.questbook.data.sync.ChallengeSync
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.Group
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.model.QuestType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class BottomNavigationTest {
    private val group = Group(
        id = "mountain-crew", name = "Mountain Crew",
        description = "Outdoor adventures and quests together.",
        inviteCode = "K7MQ2X", ownerId = "emma",
        memberIds = listOf("emma", "jonas", "test-user")
    )

    /** A unique group quest per run, because the Room database on the device is kept between runs. */
    private val questId = "nav-${UUID.randomUUID()}"
    private val questTitle = "Navigation reps ${questId.takeLast(4)}"

    /**
     * Uses a fake login and a local group quest so the tests never talk to Firebase.
     * Runs before the activity starts.
     */
    @get:Rule(order = 0) val fakeAuth = object : ExternalResource() {
        override fun before() = runBlocking {
            AppServices.authRepository = FakeAuthRepository()
            AppServices.groupRepository = FakeGroupRepository(
                initialGroups = listOf(group),
                usernames = mapOf("emma" to "Emma", "jonas" to "Jonas", "test-user" to "Test User")
            )
            AppServices.challengeSyncFactory = { ChallengeSync.None }
            val db = QuestBookDatabase.getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
            val store = GroupChallengeStore(db)
            val now = System.currentTimeMillis()
            store.refresh("test-user", listOf(group), now - 1000, ZoneId.systemDefault())
            store.create("test-user", group, Quest(questId, group.id, QuestType.WORKOUT, questTitle, "Do 20 reps.",
                "", QuestStatus.NOT_STARTED, createdByUserId = "test-user", createdAtMillis = now),
                ActivityMetric.BODYWEIGHT_REPS, 20.0, now)
            Unit
        }

        override fun after() {
            AppServices.reset()
        }
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    /** Lists only compose the rows on screen, so scroll the page until [matcher] is found. */
    private fun scrollTo(matcher: SemanticsMatcher) = compose.waitUntil(15000) {
        try {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
            true
        } catch (_: AssertionError) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    /** Opens the seeded group quest from the current list and checks that its page shows. */
    private fun openSeededQuest() {
        scrollTo(hasTestTag("quest_$questId"))
        compose.onNodeWithTag("quest_$questId").performClick()
        // The quest page has a back button; the lists do not.
        compose.waitUntil(15000) { compose.onAllNodesWithText("‹ Back").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(questTitle).onFirst().assertIsDisplayed()
    }

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
        compose.onNodeWithText("Log out").assertIsDisplayed()
        repeat(3) { compose.onNodeWithTag("tab_profile").performClick() }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_today").assertIsSelected()
    }

    @Test fun homeLinksAndQuestBackStillWork() {
        signIn()
        scrollTo(hasText("Open QuestBook"))
        compose.onNodeWithText("Open QuestBook").performClick()
        compose.onNodeWithTag("tab_questbook").assertIsSelected()
        compose.onNodeWithText("This week's activity").assertIsDisplayed()
        compose.onNodeWithTag("tab_today").performClick()
        openSeededQuest()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_today").assertIsSelected()
    }

    @Test fun questCardsOpenCorrectDetailsAndBackReturnsToQuests() {
        signIn()
        compose.onNodeWithTag("tab_quests").performClick()
        openSeededQuest()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_quests").assertIsSelected()
    }

    @Test fun homeAndOverviewOpenGroupAndCreateJoinPages() {
        signIn()
        compose.onNodeWithTag("group_mountain-crew").performClick()
        compose.onNodeWithText("Mountain Crew").assertIsDisplayed()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("home_add_group").performClick()
        compose.onNodeWithText("Create a new group").performClick()
        compose.onNodeWithTag("create_group_name").assertIsDisplayed()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("tab_groups").performClick()
        compose.onNodeWithText("3 members").assertIsDisplayed()
        compose.onNodeWithText("+ Add new").performClick()
        compose.onNodeWithText("Join a group with code").performClick()
        compose.onNodeWithTag("join_group_code").assertIsDisplayed()
    }

    @Test fun tabSelectionSurvivesRecreation() {
        signIn()
        compose.onNodeWithTag("tab_groups").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab_groups").assertIsSelected()
        compose.onNodeWithTag("group_mountain-crew").assertIsDisplayed()
    }
}
