package no.ntnu.prog2007.questbook.ui.quest.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.data.repository.QuestRepository
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.model.QuestType

/** Validates quest input and waits for persistence before reporting success. */
class CreateQuestViewModel(
    private val groupId: String,
    private val repository: QuestRepository =
        QuestDependencies.questRepository,
    private val currentUserId: () -> String? = {
        AppServices.authRepository.currentUserId
    },
    private val saveShared: (suspend (Quest, Double) -> Unit)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateQuestUiState())
    val uiState = _uiState.asStateFlow()

    fun onTypeChange(type: QuestType) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        _uiState.update {
            it.copy(type = type, distanceError = null, saveError = null)
        }
    }

    fun onTitleChange(title: String) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        _uiState.update {
            it.copy(title = title, titleError = null, saveError = null)
        }
    }

    fun onDescriptionChange(description: String) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        _uiState.update {
            it.copy(description = description, saveError = null)
        }
    }

    fun onDistanceChange(distanceKm: String) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        _uiState.update {
            it.copy(
                distanceKm = distanceKm,
                distanceError = null,
                saveError = null
            )
        }
    }

    /** Saves once, leaving the form open with an error if saving fails. */
    fun onCreateQuest() {
        val state = _uiState.value
        if (state.isSaving || state.isSaved) return

        val title = state.title.trim()
        val distance = state.distanceKm
            .trim()
            .replace(',', '.')
            .toDoubleOrNull()

        val titleError =
            if (title.isBlank()) "Enter a quest title." else null

        val distanceError =
            if (
                state.type in listOf(QuestType.DISTANCE, QuestType.WORKOUT) &&
                (distance == null || !distance.isFinite() || distance <= 0.0 ||
                    (state.type == QuestType.WORKOUT && distance % 1.0 != 0.0))
            ) {
                "Enter a positive target (whole repetitions for workouts)."
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

        val userId = currentUserId()
        if (userId.isNullOrBlank()) {
            _uiState.update {
                it.copy(saveError = "Sign in before creating a quest.")
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
                if (state.type == QuestType.DISTANCE) distance else null,
            createdByUserId = userId,
            assignedUserId = null,
            createdAtMillis = System.currentTimeMillis(),
            startedAtMillis = null
        )

        _uiState.update {
            it.copy(isSaving = true, saveError = null)
        }

        viewModelScope.launch {
            try {
                val target = when(state.type) { QuestType.DISTANCE -> distance!! * 1000
                    QuestType.WORKOUT -> distance!!; else -> 1.0 }
                if (saveShared != null) saveShared.invoke(quest, target) else repository.addQuest(quest)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSaved = true,
                        saveError = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = "Could not save the quest. Try again."
                    )
                }
            }
        }
    }

    /** Supplies the selected group ID to the ViewModel. */
    class Factory(
        private val groupId: String,
        private val saveShared: (suspend (Quest, Double) -> Unit)? = null
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(
                modelClass.isAssignableFrom(CreateQuestViewModel::class.java)
            )

            @Suppress("UNCHECKED_CAST")
            return CreateQuestViewModel(groupId, saveShared = saveShared) as T
        }
    }
}