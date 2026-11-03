package no.ntnu.prog2007.questbook.ui.groups

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.AuthRepository
import no.ntnu.prog2007.questbook.data.groups.GroupRepository
import no.ntnu.prog2007.questbook.data.groups.GroupValidator

/**
 * Everything the Create group screen shows.
 *
 * @property errorMessage a message to show the user, or `null`.
 * @property createdGroupId the new group's id once it is created; the screen then opens it.
 */
data class CreateGroupUiState(
    val name: String = "",
    val description: String = "",
    val errorMessage: String? = null,
    val createdGroupId: String? = null
) {
    /** The Create button is enabled only when the input is valid and no group is created yet. */
    val canSubmit: Boolean
        get() = createdGroupId == null && GroupValidator.validate(name, description) == null
}

/**
 * ViewModel for the Create group screen (wireframe screen 5). The screen sends events up
 * and draws [uiState] (unidirectional data flow).
 *
 * @param authRepository tells who the new group's owner is.
 * @param groupRepository where the group is saved.
 */
class CreateGroupViewModel(
    private val authRepository: AuthRepository = AppServices.authRepository,
    private val groupRepository: GroupRepository = AppServices.groupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())

    /** Current state of the screen. */
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    /** Called when the user edits the name. Text past the maximum length is cut off. */
    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name.take(GroupValidator.MAX_NAME_LENGTH), errorMessage = null) }
    }

    /** Called when the user edits the description. Text past the maximum length is cut off. */
    fun onDescriptionChange(description: String) {
        _uiState.update {
            it.copy(description = description.take(GroupValidator.MAX_DESCRIPTION_LENGTH), errorMessage = null)
        }
    }

    /** Validates the input and creates the group with the signed-in user as owner. */
    fun onCreate() {
        val state = _uiState.value
        if (state.createdGroupId != null) return
        GroupValidator.validate(state.name, state.description)?.let { problem ->
            _uiState.update { it.copy(errorMessage = problem.toMessage()) }
            return
        }
        val userId = authRepository.currentUserId ?: run {
            _uiState.update { it.copy(errorMessage = NOT_SIGNED_IN_MESSAGE) }
            return
        }
        try {
            val group = groupRepository.createGroup(userId, state.name, state.description)
            _uiState.update { it.copy(createdGroupId = group.id) }
        } catch (error: Exception) {
            _uiState.update { it.copy(errorMessage = "Could not create the group. Please try again.") }
        }
    }
}
