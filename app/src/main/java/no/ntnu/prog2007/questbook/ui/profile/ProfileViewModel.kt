package no.ntnu.prog2007.questbook.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
 * @property error retryable profile-read failure, separate from whether the account is signed in.
 */
data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val isSignedOut: Boolean = false,
    val error: String? = null
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
    private var profileLoad: Job? = null

    init { load() }

    /** Network/profile failures stay on screen with a retry, while logout remains available. */
    fun load() {
        if (profileLoad?.isActive == true || uiState.value.isSignedOut) return
        val uid = authRepository.currentUserId
        _uiState.update { it.copy(isLoading = true, error = null) }
        profileLoad = viewModelScope.launch {
            try {
                val user = authRepository.getCurrentUser()
                // A slow request must not repopulate the old profile after logout or account switching.
                if (authRepository.currentUserId == uid && !uiState.value.isSignedOut)
                    _uiState.update { it.copy(user = user, isLoading = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (authRepository.currentUserId == uid && !uiState.value.isSignedOut)
                    _uiState.update { it.copy(isLoading = false, error = "Could not load your profile. Try again.") }
            }
        }
    }

    /** Signs the user out. */
    fun onSignOut() {
        profileLoad?.cancel()
        authRepository.signOut()
        _uiState.update { it.copy(user = null, isSignedOut = true, isLoading = false, error = null) }
    }
}
