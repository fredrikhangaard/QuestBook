package no.ntnu.prog2007.questbook.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.data.groups.InviteCode

/**
 * Join group screen: the user types the invite code a friend shared.
 *
 * @param onBack called when the user taps Back.
 * @param onJoined called with the group's id once the user is a member.
 */
@Composable
fun JoinGroupScreen(
    onBack: () -> Unit,
    onJoined: (String) -> Unit,
    viewModel: JoinGroupViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.joinedGroupId) {
        state.joinedGroupId?.let(onJoined)
    }

    JoinGroupContent(
        state = state,
        onCodeChange = viewModel::onCodeChange,
        onJoin = viewModel::onJoin,
        onBack = onBack
    )
}

/** Layout of the Join group screen, without a ViewModel, so it can be previewed and tested. */
@Composable
fun JoinGroupContent(
    state: JoinGroupUiState,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
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
        Text("Join group", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Ask a friend in the group for the invite code and type it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            label = { Text("Invite code") },
            singleLine = true,
            enabled = !state.isJoining,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, letterSpacing = 4.sp),
            supportingText = { Text("${state.code.length}/${InviteCode.LENGTH}") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onJoin() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("join_group_code")
        )

        state.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = onJoin,
            enabled = state.canSubmit,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("join_group_button")
        ) {
            if (state.isJoining) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Join group")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinGroupContentPreview() {
    JoinGroupContent(
        state = JoinGroupUiState(code = "K7MQ2X"),
        onCodeChange = {},
        onJoin = {},
        onBack = {}
    )
}
