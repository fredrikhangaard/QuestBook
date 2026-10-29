package no.ntnu.prog2007.questbook.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.AuthOutcome
import no.ntnu.prog2007.questbook.data.auth.AuthRepository
import no.ntnu.prog2007.questbook.data.auth.AuthValidationError
import no.ntnu.prog2007.questbook.data.auth.AuthValidator

/**
 * Everything the Create user screen shows.
 *
 * @property isLoading `true` while waiting for Firebase.
 * @property errorMessage a message to show the user, or `null`.
 * @property isCreated `true` once the account exists and the user is signed in.
 */
data class CreateUserUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false
) {
    /** A hint under the email field while the typed email is not valid, otherwise `null`. */
    val emailError: String?
        get() = if (email.isNotBlank() && !AuthValidator.isValidEmail(email)) {
            AuthValidationError.INVALID_EMAIL.toMessage()
        } else {
            null
        }

    /** The Create user button is enabled only when the input is valid and nothing is in progress. */
    val canSubmit: Boolean
        get() = !isLoading && !isCreated &&
            AuthValidator.validateCreateUser(username, email, password) == null
}

/**
 * ViewModel for the Create user screen. The screen sends events up and draws [uiState]
 * (unidirectional data flow).
 *
 * @param authRepository where the new account is created. The app's shared instance by default.
 */
class CreateUserViewModel(
    private val authRepository: AuthRepository = AppServices.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUserUiState())

    /** Current state of the screen. */
    val uiState: StateFlow<CreateUserUiState> = _uiState.asStateFlow()

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    /** Validates the input and, if it is valid, creates the account. */
    fun onCreateUser() {
        val state = _uiState.value
        if (state.isLoading || state.isCreated) return
        AuthValidator.validateCreateUser(state.username, state.email, state.password)?.let { problem ->
            _uiState.update { it.copy(errorMessage = problem.toMessage()) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.createUser(state.username, state.email, state.password)) {
                AuthOutcome.Success -> _uiState.update { it.copy(isLoading = false, isCreated = true) }
                is AuthOutcome.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = outcome.error.toMessage())
                }
            }
        }
    }
}
