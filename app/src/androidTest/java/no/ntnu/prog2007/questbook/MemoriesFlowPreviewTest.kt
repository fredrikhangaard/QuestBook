package no.ntnu.prog2007.questbook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.photo.ImageCompressor
import no.ntnu.prog2007.questbook.data.photo.LocalMemoryPhotoStore
import no.ntnu.prog2007.questbook.data.tracking.RoomQuestTrackingStore
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.location.database.RoutePointEntity
import no.ntnu.prog2007.questbook.model.*
import no.ntnu.prog2007.questbook.ui.navigation.QuestBookBottomBar
import no.ntnu.prog2007.questbook.ui.photo.*
import no.ntnu.prog2007.questbook.ui.questbook.*
import no.ntnu.prog2007.questbook.ui.theme.QuestBookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Compose screenshots and local persistence checks with a separate disposable database.
 * The platform gallery and live GPS are replaced with deterministic input; app data is untouched.
 */
class MemoriesFlowPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "memories-previews").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** A labelled sample image, not a user's private photo. Uses the real compressor after picking. */
    private fun sampleImage(): File {
        val bitmap = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(205, 224, 230))
        paint.color = Color.rgb(111, 148, 124)
        canvas.drawOval(-150f, 160f, 1000f, 790f, paint)
        paint.color = Color.rgb(63, 104, 83)
        canvas.drawOval(280f, 260f, 1300f, 1000f, paint)
        paint.color = Color.rgb(151, 192, 209)
        canvas.drawOval(-200f, 360f, 950f, 950f, paint)
        paint.color = Color.WHITE
        paint.textSize = 38f
        canvas.drawText("SAMPLE WALK PHOTO", 42f, 76f, paint)
        return File(context.cacheDir, "memory-preview.jpg").also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
    }

    @Test fun completeAddPhotoReopenAndPreviewMemories() = runBlocking {
        val name = "memories-flow-preview.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name).build()
        val fixture = sampleImage()
        try {
            suspend fun complete(id: String, title: String, targetKm: Double, metres: Double, duration: Long): QuestResult {
                db.questDao().insertQuest(QuestEntity.fromQuest(Quest(id, "preview-group", QuestType.DISTANCE,
                    title, "Enjoy some time outdoors", "Espen", QuestStatus.NOT_STARTED,
                    requiredDistanceKm = targetKm, assignedUserId = "preview-user")))
                val store = RoomQuestTrackingStore(db)
                val session = store.begin(id, "preview-user", 1793876400000L)
                store.checkpoint(RoutePointEntity(sessionId = session.sessionId, latitude = 60.79816,
                    longitude = 10.6828, timestamp = 1793876401000L, accuracy = 5f),
                    session.copy(distanceMeters = metres, elapsedMillis = duration))
                return store.finish(id, "preview-user", session.sessionId, 1793876400000L + duration)
            }
            val result = complete("preview-walk", "Walk 5 km", 5.0, 5100.0, 3504000)
            complete("preview-short", "Lunchtime walk", 1.0, 1200.0, 840000)
            assertEquals("COMPLETED", db.questDao().getQuest(result.questId)!!.status)
            val page = mutableStateOf("complete")
            var memories = MemoriesUiState(loading = false)
            val compressor = ImageCompressor(context.contentResolver)
            lateinit var photoModel: AddPhotoViewModel
            compose.runOnIdle {
                photoModel = AddPhotoViewModel(result.questId, { compressor.compress(it) }, { jpeg, caption ->
                    LocalMemoryPhotoStore(db) { "preview-user" }.save(result.questId, jpeg, caption)
                })
            }
            var dismissKeyboard: () -> Unit = {}
            compose.setContent {
                val focus = androidx.compose.ui.platform.LocalFocusManager.current
                SideEffect { dismissKeyboard = { focus.clearFocus(force = true) } }
                QuestBookTheme(darkTheme = false) {
                    Scaffold(bottomBar = {
                        if (page.value == "memories" || page.value == "empty")
                            QuestBookBottomBar("questbook") {}
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            when (page.value) {
                                "complete" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                                    TextButton(onClick = {}) { Text("‹  Back") }
                                    CompletionContent(result, { page.value = "photo" }, { page.value = "memories" })
                                }
                                "photo" -> {
                                    val state by photoModel.uiState.collectAsState()
                                    AddPhotoContent(state,
                                        onChooseFromGallery = { photoModel.onPhotoPicked(Uri.fromFile(fixture)) },
                                        onCaptionChange = photoModel::onCaptionChange, onSave = photoModel::onSave)
                                }
                                "memories" -> MemoriesContent(memories) { assertEquals(result.questId, it) }
                                "empty" -> MemoriesContent(MemoriesUiState(loading = false))
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText("Distance: 5.10 km").assertExists()
            compose.onNodeWithText("Time: 58:24").assertExists()
            capture("01-completion")
            compose.onNodeWithText("Add a photo").performClick()
            // Cancellation leaves Save disabled and stores nothing.
            compose.runOnIdle { photoModel.onPhotoPicked(null) }
            compose.onNodeWithText("Save to Memories").assertIsNotEnabled()
            compose.onNodeWithText("Choose from gallery").performClick()
            compose.waitUntil(15000) { photoModel.uiState.value.canSave }
            compose.onNodeWithText("Add a caption (optional)").performTextInput("A great walk by the lake!")
            compose.runOnIdle {
                dismissKeyboard()
            }
            capture("02-add-photo")
            compose.onNodeWithText("Save to Memories").performScrollTo().performClick()
            compose.waitUntil(15000) { photoModel.uiState.value.isSaved }
            assertEquals(result.id, db.memoryPhotoDao().observePhotos("preview-user").first().single().resultId)
            // Closing and reopening the real database approximates the durable state after app restart.
            db.close()
            db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name).build()
            val results = db.trackingDao().observeResults("preview-user").first()
            val photos = db.memoryPhotoDao().observePhotos("preview-user").first()
            assertEquals(2, results.size)
            assertEquals(1, photos.size)
            assertNotNull(db.trackingDao().getResult("preview-short")) // A photo is optional.
            assertTrue(db.trackingDao().observeResults("other-account").first().isEmpty())
            assertTrue(db.memoryPhotoDao().observePhotos("other-account").first().isEmpty())
            memories = MemoriesUiState(results, photos, loading = false)
            compose.runOnIdle { page.value = "memories" }
            compose.onNodeWithText("Walk 5 km").assertExists()
            compose.onNodeWithText("A great walk by the lake!").assertExists()
            compose.waitUntil(15000) {
                compose.onAllNodesWithContentDescription("Photo for Walk 5 km").fetchSemanticsNodes().isNotEmpty()
            }
            capture("03-memories")
            compose.onAllNodesWithText("View quest").onFirst().performClick()
            compose.runOnIdle { page.value = "empty" }
            compose.onNodeWithText("Your next adventure starts here").assertExists()
            capture("04-empty-memories")
        } finally {
            db.close()
            context.deleteDatabase(name)
            fixture.delete()
        }
    }
}
