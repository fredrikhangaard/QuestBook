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
    @Test fun soloUserCompletesVisitWithoutPhotoLogsWorkoutAndOpensGpsAndSummary() {
        awaitText("View personal quest")
        screenshot("01-solo-home")
        compose.onAllNodesWithText("View personal quest").onFirst().performClick()
        compose.onNodeWithText("Save manual activity").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Save manual activity").performScrollTo().performClick()
        awaitText("Quest completed!")
        compose.onNodeWithText("Add a photo (optional)").assertExists()
        screenshot("02-visit-completed")
        compose.onNodeWithText("View Memories").performScrollTo().performClick()
        awaitText("Active days: 1")
        compose.onNodeWithText("Completed quests: 1").assertExists()
        screenshot("03-weekly-summary")
        compose.onNodeWithText("Previous week").performScrollTo().performClick()
        awaitText("Weekly activity")
        compose.onNodeWithText("Active days: 0").assertExists()
        compose.onNodeWithTag("tab_quests").performClick()
        compose.onNodeWithTag("tab_quests").assertIsSelected()
        compose.onNodeWithTag("tab_questbook").performClick()
        awaitText("Weekly activity") // Browsing a past week survives tab changes.
        compose.onNodeWithText("Next week").performScrollTo().performClick()
        awaitText("This week's activity")
        compose.onNodeWithTag("tab_quests").performClick()
        compose.onNodeWithText("Log personal activity").performScrollTo().performClick()
        compose.onNodeWithText("Push-ups").performClick()
        compose.onNodeWithText("Repetitions").performTextInput("30")
        compose.onNodeWithText("Active minutes (optional)").performTextInput("5")
        compose.activityRule.scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
        screenshot("04-manual-workout")
        compose.onNodeWithText("Save activity").performScrollTo().performClick()
        awaitText("Quest completed!")
        compose.onNodeWithText("View Memories").performScrollTo().performClick()
        awaitText("Completed quests: 2")
        compose.onNodeWithText("Active time: 5 min").assertExists()
        compose.onNodeWithText("Bodyweight repetitions: 30").assertExists()
        compose.onNodeWithText("Outdoor visits: 1").assertExists()
        screenshot("05-summary-after-workout")
        compose.onNodeWithTag("tab_quests").performClick()
        compose.onNodeWithTag("tab_quests").assertIsSelected()
        val db = QuestBookDatabase.getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        val reps = runBlocking { PersonalQuestStore(db).refresh(uid, System.currentTimeMillis(), ZoneId.systemDefault()) }
            .quests.single { it.assignment.slot == "BODYWEIGHT_REPS" }
        val progressLabel = no.ntnu.prog2007.questbook.ui.personal.progressText(reps)
        awaitText(progressLabel)
        compose.onNodeWithText(progressLabel).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Log personal activity").performScrollTo().performClick()
        compose.onNodeWithText("Distance (km)").performTextInput("0,1")
        compose.onNodeWithText("Start GPS activity").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasText("Start Quest") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start Quest").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Assign quest").assertDoesNotExist()
    }
}
