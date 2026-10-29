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
import no.ntnu.prog2007.questbook.data.auth.AuthValidator

/**
 * Everything the Sign in screen shows.
 *
 * @property isLoading `true` while waiting for Firebase.
 * @property errorMessage a message to show the user, or `null`.
 * @property isSignedIn `true` once sign-in succeeded; the screen then navigates on.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSignedIn: Boolean = false
) {
    /** The Sign in button is enabled only when the input is valid and nothing is in progress. */
    val canSubmit: Boolean
        get() = !isLoading && !isSignedIn && AuthValidator.validateSignIn(email, password) == null
}

/**
 * ViewModel for the Sign in screen. The screen sends events ([onEmailChange],
 * [onPasswordChange], [onSignIn]) up, and draws [uiState] (unidirectional data flow).
 *
 * @param authRepository where the sign-in is sent. The app's shared instance by default.
 */
class LoginViewModel(
    private val authRepository: AuthRepository = AppServices.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())

    /** Current state of the screen. */
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    /** Validates the input and, if it is valid, signs in. */
    fun onSignIn() {
        val state = _uiState.value
        if (state.isLoading || state.isSignedIn) return
        AuthValidator.validateSignIn(state.email, state.password)?.let { problem ->
            _uiState.update { it.copy(errorMessage = problem.toMessage()) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.signIn(state.email, state.password)) {
                AuthOutcome.Success -> _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                is AuthOutcome.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = outcome.error.toMessage())
                }
            }
        }
    }
}
