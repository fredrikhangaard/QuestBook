package no.ntnu.prog2007.questbook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.ZoneId
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

/** Exercises production navigation with a signed-in solo account and no groups or Firebase calls. */
class PersonalQuestNavigationTest {
    private lateinit var repsTitle: String
    private lateinit var walkTitle: String
    private val uid = "solo-navigation-fixture"
    @get:Rule(order = 0) val data = object : ExternalResource() {
        override fun before() = runBlocking {
            AppServices.authRepository = FakeAuthRepository(User(uid, "Solo user", "solo@example.test"), signedIn = true)
            AppServices.groupRepository = FakeGroupRepository()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = QuestBookDatabase.getDatabase(context)
            db.withTransaction {
                // Remove only this test account's previous fixtures; real users' rows are untouched.
                val sql = db.openHelper.writableDatabase
                listOf("personal_quests", "activity_entries", "quest_results", "tracking_sessions", "memory_photos").forEach {
                    sql.execSQL("DELETE FROM `$it` WHERE userId = ?", arrayOf(uid))
                }
                sql.execSQL("DELETE FROM quests WHERE groupId = ?", arrayOf(PersonalQuestIds.group(uid)))
                val now = System.currentTimeMillis()
                val window = questWindow(now, ZoneId.systemDefault(), QuestCadence.DAILY)
                val id = "personal-solo-visit"
                db.personalQuestDao().insertAssignment(PersonalQuestEntity(id, uid, "water", "DAILY", window.key,
                    "daily", "Visit a body of water", "Confirm your visit. Photos are optional.", "WATER_VISITS", 1.0,
                    window.start, window.end, window.zone))
                db.questDao().insertQuest(QuestEntity.fromQuest(Quest(id, PersonalQuestIds.group(uid), QuestType.VISIT_PLACE,
                    "Visit a body of water", "Confirm your visit", "You", QuestStatus.NOT_STARTED,
                    assignedUserId = uid, createdAtMillis = now)))
            }
            val generated = PersonalQuestStore(db).refresh(uid, System.currentTimeMillis(), ZoneId.systemDefault()).quests
            repsTitle = generated.single { it.assignment.slot == "BODYWEIGHT_REPS" }.assignment.title
            walkTitle = generated.single { it.assignment.slot == "WALK_METERS" }.assignment.title
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION", "android.permission.POST_NOTIFICATIONS").forEach { permission ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission")).use { it.readBytes() }
            }
            Unit
        }
        override fun after() { AppServices.reset() }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No emulator screenshot available" }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        // Export fixture-only screenshots outside app data so test-runner APK cleanup keeps them.
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        shell("mkdir -p /sdcard/Download/questbook-solo-previews")
        shell("cp ${file.absolutePath} /sdcard/Download/questbook-solo-previews/$name.png")
    }
    private fun awaitText(text: String) = compose.waitUntil(15000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun soloUserUsesQuestSpecificControlsAndWalkingStartsOnlyOnExplicitAction() {
        awaitText("View quest")
        screenshot("01-solo-home")
        compose.onAllNodesWithText("View quest").onFirst().performClick()
        compose.onNodeWithText("Confirm visit").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Confirm visit").performScrollTo().performClick()
        awaitText("Quest completed!")
        compose.onNodeWithText("Add a photo (optional)").assertExists()
        screenshot("02-visit-completed")
        compose.onNodeWithText("View Memories").performScrollTo().performClick()
        awaitText("Active days: 1")
        compose.onNodeWithText("Completed quests: 1").assertExists()
        compose.onNodeWithText("Outdoor visits: 1").assertExists()
        compose.onNodeWithText("Previous week").performScrollTo().performClick()
        awaitText("No completed quests this week")
        compose.onNodeWithText("Quests", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Memories", useUnmergedTree = true).performClick()
        awaitText("No completed quests this week")
        compose.onNodeWithText("Next week").performScrollTo().performClick()
        awaitText("Completed quests: 1")
        compose.onNodeWithText("Quests", useUnmergedTree = true).performClick()
        compose.onNodeWithText(repsTitle).performScrollTo().performClick()
        compose.onNodeWithText("+10").performScrollTo().performClick()
        compose.onNodeWithText("+5").performScrollTo().performClick()
        compose.onNodeWithText("+1").performScrollTo().performClick()
        compose.onNodeWithText("Save repetitions").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("16 /", substring = true).fetchSemanticsNodes().isNotEmpty() }
        screenshot("04-manual-workout")
        compose.onNodeWithText("Repetitions to add").performTextReplacement("14")
        compose.onNodeWithText("Save repetitions").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("30 /", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("‹ Back").performScrollTo().performClick()
        compose.onNodeWithText("Memories", useUnmergedTree = true).performClick()
        awaitText("Bodyweight repetitions: 30")
        screenshot("05-summary-after-workout")
        compose.onNodeWithText("Quests", useUnmergedTree = true).performClick()
        compose.onNodeWithText(walkTitle).performScrollTo().performClick()
        compose.onNodeWithText("Start quest").performScrollTo().performClick()
        awaitText("Pause tracking")
        compose.onNodeWithText("Pause tracking").performScrollTo().performClick()
        awaitText("Resume tracking")
        compose.onNodeWithText("Assign quest").assertDoesNotExist()
    }
}
