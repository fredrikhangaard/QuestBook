package no.ntnu.prog2007.questbook.ui.quest.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.data.repository.QuestRepository
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.model.QuestType

class CreateQuestViewModel(
    private val groupId: String,
    private val repository: QuestRepository =
        QuestDependencies.questRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateQuestUiState())
    val uiState = _uiState.asStateFlow()

    fun onTypeChange(type: QuestType) {
        _uiState.update {
            it.copy(
                type = type,
                distanceError = null,
                saveError = null
            )
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update {
            it.copy(
                title = title,
                titleError = null,
                saveError = null
            )
        }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update {
            it.copy(
                description = description,
                saveError = null
            )
        }
    }

    fun onDistanceChange(distanceKm: String) {
        _uiState.update {
            it.copy(
                distanceKm = distanceKm,
                distanceError = null,
                saveError = null
            )
        }
    }

    fun onCreateQuest() {
        val state = _uiState.value

        // Prevent another save before navigation closes the form.
        if (state.isSaved) return

        val title = state.title.trim()

        // Accept both 2.5 and 2,5.
        val distance = state.distanceKm
            .trim()
            .replace(',', '.')
            .toDoubleOrNull()

        val titleError =
            if (title.isBlank()) "Enter a quest title." else null

        val distanceError =
            if (
                state.type == QuestType.DISTANCE &&
                (distance == null || !distance.isFinite() || distance <= 0.0)
            ) {
                "Enter a distance greater than 0 km."
            } else {
                null
            }

        if (titleError != null || distanceError != null) {
            _uiState.update {
                it.copy(
                    titleError = titleError,
                    distanceError = distanceError,
                    saveError = null
                )
            }
            return
        }

        if (groupId.isBlank()) {
            _uiState.update {
                it.copy(saveError = "Open a group before creating a quest.")
            }
            return
        }

        val quest = Quest(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            type = state.type,
            title = title,
            description = state.description.trim(),
            assignedTo = "",
            status = QuestStatus.NOT_STARTED,
            requiredDistanceKm =
                if (state.type == QuestType.DISTANCE) distance else null
        )

        try {
            repository.addQuest(quest)
            _uiState.update {
                it.copy(isSaved = true, saveError = null)
            }
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    saveError = "Could not create the quest. Try again."
                )
            }
        }
    }

    class Factory(
        private val groupId: String
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateQuestViewModel::class.java))

            @Suppress("UNCHECKED_CAST")
            return CreateQuestViewModel(groupId) as T
        }
    }
}