package no.ntnu.prog2007.questbook.ui.login

import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.data.auth.AuthError
import no.ntnu.prog2007.questbook.data.auth.AuthOutcome
import no.ntnu.prog2007.questbook.data.auth.AuthValidationError
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun onSignIn_correctCredentials_signsIn() {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChange("emma@example.com")
        viewModel.onPasswordChange("secret123")

        // Act
        viewModel.onSignIn()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state.isSignedIn)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("test-user", repository.currentUserId)
    }

    @Test
    fun onSignIn_wrongPassword_showsErrorAndStaysSignedOut() {
        // Arrange
        val repository = FakeAuthRepository().apply {
            nextOutcome = AuthOutcome.Failure(AuthError.INVALID_CREDENTIALS)
        }
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChange("emma@example.com")
        viewModel.onPasswordChange("wrong-password")

        // Act
        viewModel.onSignIn()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isSignedIn)
        assertFalse(state.isLoading)
        assertEquals(AuthError.INVALID_CREDENTIALS.toMessage(), state.errorMessage)
        assertNull(repository.currentUserId)
    }

    @Test
    fun onSignIn_invalidEmail_doesNotContactRepository() {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("secret123")

        // Act
        viewModel.onSignIn()

        // Assert
        assertEquals(0, repository.attempts)
        assertEquals(AuthValidationError.INVALID_EMAIL.toMessage(), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onEmailChange_afterFailedSignIn_clearsError() {
        // Arrange
        val repository = FakeAuthRepository().apply {
            nextOutcome = AuthOutcome.Failure(AuthError.NETWORK)
        }
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChange("emma@example.com")
        viewModel.onPasswordChange("secret123")
        viewModel.onSignIn()

        // Act
        viewModel.onEmailChange("emma2@example.com")

        // Assert
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun canSubmit_emptyPassword_isFalse() {
        // Arrange
        val viewModel = LoginViewModel(FakeAuthRepository())

        // Act
        viewModel.onEmailChange("emma@example.com")

        // Assert
        assertFalse(viewModel.uiState.value.canSubmit)
    }
}
