package no.ntnu.prog2007.questbook.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.AuthRepository
import no.ntnu.prog2007.questbook.data.groups.GroupError
import no.ntnu.prog2007.questbook.data.groups.GroupRepository
import no.ntnu.prog2007.questbook.data.groups.InviteCode
import no.ntnu.prog2007.questbook.data.groups.JoinOutcome

/**
 * Everything the Join group screen shows.
 *
 * @property code the invite code, already in upper case without spaces.
 * @property isJoining `true` while waiting for the server.
 * @property errorMessage a message to show the user, or `null`.
 * @property joinedGroupId the group's id once joined; the screen then opens it.
 */
data class JoinGroupUiState(
    val code: String = "",
    val isJoining: Boolean = false,
    val errorMessage: String? = null,
    val joinedGroupId: String? = null
) {
    /** The Join button is enabled only for a complete code while nothing is in progress. */
    val canSubmit: Boolean
        get() = !isJoining && joinedGroupId == null && InviteCode.isValid(code)
}

/**
 * ViewModel for the Join group screen. The screen sends events up and draws [uiState]
 * (unidirectional data flow).
 *
 * @param authRepository tells which user joins.
 * @param groupRepository checks the code and adds the user to the group.
 */
class JoinGroupViewModel(
    private val authRepository: AuthRepository = AppServices.authRepository,
    private val groupRepository: GroupRepository = AppServices.groupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinGroupUiState())

    /** Current state of the screen. */
    val uiState: StateFlow<JoinGroupUiState> = _uiState.asStateFlow()

    /** Called when the user edits the code. It is shown in the stored form: upper case, no spaces. */
    fun onCodeChange(input: String) {
        _uiState.update {
            it.copy(code = InviteCode.normalize(input).take(InviteCode.LENGTH), errorMessage = null)
        }
    }

    /** Checks the code and joins the group. */
    fun onJoin() {
        val state = _uiState.value
        if (state.isJoining || state.joinedGroupId != null) return
        if (!InviteCode.isValid(state.code)) {
            _uiState.update { it.copy(errorMessage = GroupError.INVALID_CODE.toMessage()) }
            return
        }
        val userId = authRepository.currentUserId ?: run {
            _uiState.update { it.copy(errorMessage = NOT_SIGNED_IN_MESSAGE) }
            return
        }
        _uiState.update { it.copy(isJoining = true, errorMessage = null) }
        viewModelScope.launch {
            when (val outcome = groupRepository.joinGroup(userId, state.code)) {
                is JoinOutcome.Success -> _uiState.update {
                    it.copy(isJoining = false, joinedGroupId = outcome.groupId)
                }
                is JoinOutcome.Failure -> _uiState.update {
                    it.copy(isJoining = false, errorMessage = outcome.error.toMessage())
                }
            }
        }
    }
}
