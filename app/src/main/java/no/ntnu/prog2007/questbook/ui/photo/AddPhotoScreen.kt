package no.ntnu.prog2007.questbook.ui.photo

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Add photo screen (wireframe screen 11). The user picks a photo from the gallery,
 * writes an optional caption and saves it to the quest.
 *
 * @param questId the quest the photo belongs to.
 * @param onDone called after the photo has been saved, e.g. to go back.
 */
@Composable
fun AddPhotoScreen(questId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AddPhotoViewModel = viewModel(
        factory = remember(questId) {
            AddPhotoViewModel.Factory(context.applicationContext, questId)
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Android's photo picker. Needs no permissions and returns the chosen image as a Uri.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onPhotoPicked(uri)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    AddPhotoContent(
        state = state,
        onChooseFromGallery = {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onCaptionChange = viewModel::onCaptionChange,
        onSave = viewModel::onSave
    )
}

/**
 * Layout of the Add photo screen. It has no state of its own, so it can be
 * shown in previews and UI tests without a ViewModel.
 */
@Composable
fun AddPhotoContent(
    state: AddPhotoUiState,
    onChooseFromGallery: () -> Unit,
    onCaptionChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = remember(state.photo) { state.photo?.jpegBytes?.toImageBitmap() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Add photo", style = MaterialTheme.typography.headlineMedium)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isProcessing -> CircularProgressIndicator()
                preview != null -> Image(
                    bitmap = preview,
                    contentDescription = "Selected quest photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> Text(
                    text = "No photo selected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.photo?.let { photo ->
            Text(text = "Size: ${photo.sizeKb} KB", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(
            onClick = onChooseFromGallery,
            enabled = !state.isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose from gallery")
        }

        OutlinedTextField(
            value = state.caption,
            onValueChange = onCaptionChange,
            label = { Text("Add a caption (optional)") },
            placeholder = { Text("e.g. Great hike today!") },
            modifier = Modifier.fillMaxWidth()
        )

        state.errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

/**
 * Turns JPEG bytes (for example from [no.ntnu.prog2007.questbook.data.photo.PhotoCodec.decode])
 * into an image Compose can draw.
 *
 * @return the image, or `null` if the bytes are not a valid image.
 */
fun ByteArray.toImageBitmap(): ImageBitmap? =
    BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()

@Preview(showBackground = true)
@Composable
private fun AddPhotoContentPreview() {
    AddPhotoContent(
        state = AddPhotoUiState(caption = "Great hike today!"),
        onChooseFromGallery = {},
        onCaptionChange = {},
        onSave = {}
    )
}
