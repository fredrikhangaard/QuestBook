package no.ntnu.prog2007.questbook.ui.quest.assignment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Scrollable, accessible member picker. Save remains disabled while loading or persisting. */
@Composable
fun AssignQuestDialog(state: AssignQuestUiState, onSelect: (String) -> Unit, onAssign: () -> Unit,
    onDismiss: () -> Unit, onReload: () -> Unit) {
    if (!state.isOpen) return
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Assign quest") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose who will complete this quest.")
                if (state.isLoading) CircularProgressIndicator(Modifier.size(24.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    // Key and select rows by UID so duplicate names never select the wrong person.
                    items(state.members, key = { it.userId }) { member ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(selected = state.selectedUserId == member.userId,
                                enabled = !state.isLoading && !state.isSaving, role = Role.RadioButton,
                                onClick = { onSelect(member.userId) })
                            .testTag("assign_member_${member.userId}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            // The whole row handles selection; the radio button is visual only.
                            RadioButton(selected = state.selectedUserId == member.userId, onClick = null)
                            Text(if (member.isCurrentUser) "${member.displayName} (You)" else member.displayName,
                                modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onReload, enabled = !state.isLoading && !state.isSaving) { Text("Reload members") }
                }
                Text("Saved on this device. Group sync is not connected yet.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onAssign, enabled = state.selectedUserId != null && !state.isLoading && !state.isSaving) {
                Text(if (state.isSaving) "Saving…" else "Assign")
            }
        }, dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") }
        })
}
