package no.ntnu.prog2007.questbook.ui.photo

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.ntnu.prog2007.questbook.data.photo.FirestorePhotoRepository
import no.ntnu.prog2007.questbook.data.photo.ImageCompressor
import no.ntnu.prog2007.questbook.data.photo.PhotoRepository
import no.ntnu.prog2007.questbook.data.photo.PhotoTooLargeException

/**
 * ViewModel for the Add photo screen.
 *
 * The screen sends events ([onPhotoPicked], [onCaptionChange], [onSave]) up to this
 * ViewModel, which updates [uiState]. The screen only draws what [uiState] says
 * (unidirectional data flow).
 *
 * @param questId the quest the photo belongs to.
 * @param compressPhoto turns a picked image into small JPEG bytes (see [ImageCompressor]).
 * @param repository where the finished photo is saved.
 */
class AddPhotoViewModel(
    private val questId: String,
    private val compressPhoto: suspend (Uri) -> ByteArray,
    private val repository: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPhotoUiState())

    /** Current state of the screen. */
    val uiState: StateFlow<AddPhotoUiState> = _uiState.asStateFlow()

    /**
     * Called with the image the user picked in the photo picker,
     * or `null` if the user closed the picker without choosing one.
     */
    fun onPhotoPicked(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val jpeg = compressPhoto(uri)
                _uiState.update { it.copy(photo = PreparedPhoto(jpeg), isProcessing = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: PhotoTooLargeException) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = "The photo is too large. Try another one.")
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = "Could not read the photo.")
                }
            }
        }
    }

    /** Called when the user edits the caption. Long captions are cut at [MAX_CAPTION_LENGTH]. */
    fun onCaptionChange(caption: String) {
        _uiState.update { it.copy(caption = caption.take(MAX_CAPTION_LENGTH)) }
    }

    /** Called when the user taps Save. Does nothing if there is no photo to save. */
    fun onSave() {
        val state = _uiState.value
        val photo = state.photo
        if (!state.canSave || photo == null) return
        try {
            repository.savePhoto(questId, photo.jpegBytes, state.caption.trim())
            _uiState.update { it.copy(isSaved = true) }
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Could not save the photo.") }
        }
    }

    /** Creates an [AddPhotoViewModel] with the real image compressor and Firestore repository. */
    class Factory(context: Context, private val questId: String) : ViewModelProvider.Factory {
        private val compressor = ImageCompressor(context.applicationContext.contentResolver)

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AddPhotoViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AddPhotoViewModel(
                questId = questId,
                compressPhoto = { uri -> compressor.compress(uri) },
                repository = FirestorePhotoRepository()
            ) as T
        }
    }

    private companion object {
        const val MAX_CAPTION_LENGTH = 200
    }
}
