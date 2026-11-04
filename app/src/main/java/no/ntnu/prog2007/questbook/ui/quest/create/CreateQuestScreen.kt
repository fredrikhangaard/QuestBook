package no.ntnu.prog2007.questbook.ui.quest.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.model.QuestType

/** Quest creation form for the selected group. */
@Composable
fun CreateQuestScreen(
    groupId: String,
    groupName: String,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val factory = remember(groupId) {
        CreateQuestViewModel.Factory(groupId)
    }
    val questViewModel: CreateQuestViewModel = viewModel(
        key = "createQuest_$groupId",
        factory = factory
    )
    val state by questViewModel.uiState.collectAsStateWithLifecycle()
    val editable = !state.isSaving && !state.isSaved

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onCreated()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = onBack,
            enabled = editable,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("‹ Back")
        }

        Text("Create quest", style = MaterialTheme.typography.titleLarge)
        Text(
            groupName,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Quest type", style = MaterialTheme.typography.titleSmall)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestType.entries.chunked(2).forEach { types ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            enabled = editable,
                            onClick = { questViewModel.onTypeChange(type) },
                            label = {
                                Text(
                                    when (type) {
                                        QuestType.DISTANCE -> "Distance"
                                        QuestType.VISIT_PLACE -> "Visit place"
                                        QuestType.WORKOUT -> "Workout"
                                        QuestType.CUSTOM -> "Custom"
                                    }
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = questViewModel::onTitleChange,
            enabled = editable,
            label = { Text("Title") },
            placeholder = { Text("e.g. Walk 5 km") },
            singleLine = true,
            isError = state.titleError != null,
            supportingText = {
                state.titleError?.let { Text(it) }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = questViewModel::onDescriptionChange,
            enabled = editable,
            label = { Text("Description (optional)") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.type == QuestType.DISTANCE) {
            OutlinedTextField(
                value = state.distanceKm,
                onValueChange = questViewModel::onDistanceChange,
                enabled = editable,
                label = { Text("Target distance") },
                suffix = { Text("km") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                isError = state.distanceError != null,
                supportingText = {
                    state.distanceError?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            "This quest will be added to $groupName without an assigned member.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        state.saveError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = questViewModel::onCreateQuest,
            enabled = editable,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (state.isSaving) "Saving…" else "Create quest")
        }
    }
}