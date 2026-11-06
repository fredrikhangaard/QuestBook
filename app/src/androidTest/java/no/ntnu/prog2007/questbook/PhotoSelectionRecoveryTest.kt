package no.ntnu.prog2007.questbook

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import no.ntnu.prog2007.questbook.ui.photo.AddPhotoViewModel
import org.junit.Assert.*
import org.junit.Test

/** Uses real Android Uri values and controlled reads/saves to reproduce photo-picker races. */
class PhotoSelectionRecoveryTest {
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private suspend fun until(condition: () -> Boolean) = withTimeout(5000) { while (!condition()) delay(20) }

    @Test fun newerPhotoWinsEvenWhenAnOldReadIgnoresCancellation() = runBlocking {
        val old = CompletableDeferred<ByteArray>()
        val latest = CompletableDeferred<ByteArray>()
        val vm = AddPhotoViewModel("quest", { uri ->
            // A provider may finish a blocking read after cancellation. The selection token still protects the UI.
            withContext(NonCancellable) { if (uri.lastPathSegment == "old") old.await() else latest.await() }
        }, { _, _ -> })
        main { vm.onPhotoPicked(Uri.parse("content://test/old")) }
        main { vm.onPhotoPicked(Uri.parse("content://test/latest")) }
        latest.complete(byteArrayOf(2))
        until { !vm.uiState.value.isProcessing }
        old.complete(byteArrayOf(1))
        main {} // Drain the old continuation before inspecting the retained selection.
        assertArrayEquals(byteArrayOf(2), vm.uiState.value.photo!!.jpegBytes)
    }

    @Test fun readAndSaveFailuresStayRetryableAndPendingSaveKeepsItsSnapshot() = runBlocking {
        var failRead = true
        var failSave = true
        var reads = 0
        val saving = CompletableDeferred<Unit>()
        val vm = AddPhotoViewModel("quest", {
            reads++
            check(!failRead) { "Image no longer available" }
            byteArrayOf(7)
        }, { bytes, caption ->
            check(!failSave) { "Disk unavailable" }
            saving.await()
            assertArrayEquals(byteArrayOf(7), bytes)
            assertEquals("My walk", caption)
        })
        main { vm.onPhotoPicked(Uri.parse("content://test/photo")) }
        until { !vm.uiState.value.isProcessing }
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.canSave)
        failRead = false
        main { vm.onPhotoPicked(Uri.parse("content://test/photo")); vm.onCaptionChange("My walk") }
        until { vm.uiState.value.canSave }
        main { vm.onSave() }
        until { !vm.uiState.value.isSaving }
        assertFalse(vm.uiState.value.isSaved)
        assertTrue(vm.uiState.value.canSave)
        failSave = false
        main { vm.onSave(); vm.onPhotoPicked(Uri.parse("content://test/replacement")); vm.onCaptionChange("Changed") }
        assertEquals(2, reads)
        assertEquals("My walk", vm.uiState.value.caption)
        saving.complete(Unit)
        until { vm.uiState.value.isSaved }
    }
}
