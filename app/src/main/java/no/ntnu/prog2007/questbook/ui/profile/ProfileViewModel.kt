package no.ntnu.prog2007.questbook.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.AuthRepository
import no.ntnu.prog2007.questbook.model.User

/**
 * Everything the Profile screen shows.
 *
 * @property user the signed-in user, or `null` while loading or if nobody is signed in.
 * @property isLoading `true` until the profile has been loaded.
 * @property isSignedOut `true` after the user tapped Log out; the screen then navigates away.
 */
data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val isSignedOut: Boolean = false
)

/**
 * ViewModel for the Profile screen. Loads the signed-in user and handles Log out.
 *
 * @param authRepository where the user is loaded from. The app's shared instance by default.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository = AppServices.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())

    /** Current state of the screen. */
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.update { it.copy(user = user, isLoading = false) }
        }
    }

    /** Signs the user out. */
    fun onSignOut() {
        authRepository.signOut()
        _uiState.update { it.copy(user = null, isSignedOut = true) }
    }
}
