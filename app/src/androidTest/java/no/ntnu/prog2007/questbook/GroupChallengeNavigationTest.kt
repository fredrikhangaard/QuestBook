package no.ntnu.prog2007.questbook

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId
import java.util.UUID
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import no.ntnu.prog2007.questbook.data.challenges.GroupChallengeStore
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.data.photo.LocalMemoryPhotoStore
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

/** Actual app navigation with two local fixture members. No Firebase calls or real user records. */
class GroupChallengeNavigationTest {
    private val uid = "adventure-alice"
    private val bob = "adventure-bob"
    private val group = Group("adventure-flow-${UUID.randomUUID()}", "Adventure crew", "Find something fun together", "ABC123", uid, listOf(uid, bob))
    private val id = "shared-${UUID.randomUUID()}"
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() = runBlocking {
            AppServices.authRepository = FakeAuthRepository(User(uid, "Alice", "alice@example.test"), signedIn = true)
            AppServices.groupRepository = FakeGroupRepository(listOf(group), mapOf(uid to "Alice", bob to "Bob"))
            val db = QuestBookDatabase.getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
            val store = GroupChallengeStore(db)
            val now = System.currentTimeMillis()
            store.refresh(uid, listOf(group), now - 1000, ZoneId.systemDefault())
            store.create(uid, group, Quest(id, group.id, QuestType.WORKOUT, "Rock-solid reps", "Do 20 reps. An optional victory photo is welcome.",
                "", QuestStatus.NOT_STARTED, createdByUserId = uid, createdAtMillis = now), ActivityMetric.BODYWEIGHT_REPS, 20.0, now)
            val attempt = store.open(bob, group, id, "Bob", now)
            PersonalQuestStore(db).logManual(bob, attempt, 20.0, 0.0, "bob-set-$id", now)
            val bytes = ByteArrayOutputStream().use { output ->
                Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(64, 112, 83)) }
                    .let { bitmap -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output); bitmap.recycle() }
                output.toByteArray()
            }
            LocalMemoryPhotoStore(db) { bob }.save(attempt, bytes, "Bob's victory photo")
            Unit
        }
        override fun after() { AppServices.reset() }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun await(text: String) = compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        shell("mkdir -p /sdcard/Download/questbook-adventure-previews")
        shell("cp ${file.absolutePath} /sdcard/Download/questbook-adventure-previews/$name.png")
    }
    @Test fun memberCanFinishAfterFriendAndBothPhotosAndCompletionsAppearInQuestAndMemories() {
        await("Adventure crew")
        compose.onNodeWithText("Adventure crew").performClick()
        await("Get quest")
        screenshot("01-group-overview")
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("quest_$id"))
        compose.onNodeWithTag("quest_$id").performClick()
        await("Save repetitions")
        compose.onNodeWithText("Bob completed ✓").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bob's victory photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("+10").performScrollTo().performClick()
        compose.onNodeWithText("+5").performScrollTo().performClick()
        compose.onNodeWithText("+1").performScrollTo().performClick()
        compose.onNodeWithText("Save repetitions").performScrollTo().performClick()
        await("16 / 20 reps")
        screenshot("02-quest-repetitions")
        compose.onNodeWithText("Repetitions to add").performTextReplacement("4")
        compose.onNodeWithText("Save repetitions").performScrollTo().performClick()
        await("Quest completed!")
        compose.onNodeWithText("Add a photo (optional)").performScrollTo().assertExists()
        await("You completed ✓")
        compose.onNodeWithText("You completed ✓").performScrollTo().assertIsDisplayed()
        screenshot("03-group-completions")
        compose.onNodeWithText("View Memories").performScrollTo().performClick()
        await("This week's activity")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bob completed"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bob's victory photo"))
        compose.onNodeWithText("Bob's victory photo").assertIsDisplayed()
        screenshot("04-shared-memories")
        compose.onNodeWithTag("tab_groups").performClick()
        compose.onNodeWithText("Adventure crew").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("View group goal"))
        compose.onNodeWithText("View group goal").performClick()
        await("Weekly group goal")
        screenshot("05-weekly-group-goal")
        compose.onNodeWithText("‹ Back").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Get quest"))
        compose.onNodeWithText("Get quest").performClick()
        // The drawn quest is already owned; no member picker or assignment action is needed.
        compose.waitUntil(15000) { compose.onAllNodesWithText("Group quest").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Assign quest").assertDoesNotExist()
        screenshot("06-drawn-quest")
        compose.onNodeWithText("‹ Back").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("+ Create quest"))
        compose.onNodeWithText("+ Create quest").performClick()
        compose.onNodeWithText("Workout").performClick()
        compose.onNodeWithText("Title").performTextInput("My custom workout")
        compose.onNodeWithText("Target repetitions").performScrollTo().performTextInput("15")
        compose.onNode(hasText("Create quest") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Create quest").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(15000) { no.ntnu.prog2007.questbook.data.repository.QuestDependencies.questRepository.quests.value.any { it.title == "My custom workout" } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("My custom workout"))
        compose.onNodeWithText("My custom workout").performClick()
        await("Save repetitions")
        compose.onNodeWithText("+5").performScrollTo().performClick()
        compose.onNodeWithText("Save repetitions").performScrollTo().performClick()
        await("5 / 15 reps")
        screenshot("07-created-quest")
    }
}
