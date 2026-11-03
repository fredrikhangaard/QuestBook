package no.ntnu.prog2007.questbook.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.data.groups.GroupValidator

/**
 * Create group screen (wireframe screen 5).
 *
 * @param onBack called when the user taps Back.
 * @param onCreated called with the new group's id once the group is created.
 */
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateGroupViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let(onCreated)
    }

    CreateGroupContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onCreate = viewModel::onCreate,
        onBack = onBack
    )
}

/** Layout of the Create group screen, without a ViewModel, so it can be previewed and tested. */
@Composable
fun CreateGroupContent(
    state: CreateGroupUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Create group", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Give your group a name. Afterwards you get an invite code to share with your friends.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text("Group name") },
            singleLine = true,
            supportingText = { Text("${state.name.length}/${GroupValidator.MAX_NAME_LENGTH}") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_group_name")
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text("Description (optional)") },
            minLines = 3,
            supportingText = { Text("${state.description.length}/${GroupValidator.MAX_DESCRIPTION_LENGTH}") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_group_description")
        )

        state.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = onCreate,
            enabled = state.canSubmit,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("create_group_button")
        ) {
            Text("Create group")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateGroupContentPreview() {
    CreateGroupContent(
        state = CreateGroupUiState(name = "Mountain Crew"),
        onNameChange = {},
        onDescriptionChange = {},
        onCreate = {},
        onBack = {}
    )
}
