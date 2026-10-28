package no.ntnu.prog2007.questbook.ui.photo

/**
 * A photo that has been compressed and is ready to be saved.
 *
 * A regular class (not a data class), so a newly picked photo always counts as a new value.
 */
class PreparedPhoto(val jpegBytes: ByteArray) {

    /** Size of the compressed photo in kilobytes. */
    val sizeKb: Int get() = jpegBytes.size / 1024
}

/**
 * Everything the Add photo screen shows. Produced by [AddPhotoViewModel]
 * and observed by the screen (state flows down, events flow up).
 *
 * @property photo the compressed photo, or `null` if none is picked yet.
 * @property caption optional text written by the user.
 * @property isProcessing `true` while the picked photo is being compressed.
 * @property isSaved `true` once the photo has been saved.
 * @property errorMessage a message to show the user, or `null` if nothing went wrong.
 */
data class AddPhotoUiState(
    val photo: PreparedPhoto? = null,
    val caption: String = "",
    val isProcessing: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
) {
    /** The Save button is enabled only when there is a photo and nothing else is happening. */
    val canSave: Boolean get() = photo != null && !isProcessing && !isSaved
}
