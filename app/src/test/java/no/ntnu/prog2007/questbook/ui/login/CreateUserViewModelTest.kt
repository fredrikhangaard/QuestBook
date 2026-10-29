package no.ntnu.prog2007.questbook.ui.login

import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.data.auth.AuthError
import no.ntnu.prog2007.questbook.data.auth.AuthOutcome
import no.ntnu.prog2007.questbook.data.auth.AuthValidationError
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateUserViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun CreateUserViewModel.fillIn(
        username: String = "Emma",
        email: String = "emma@example.com",
        password: String = "secret123"
    ) {
        onUsernameChange(username)
        onEmailChange(email)
        onPasswordChange(password)
    }

    @Test
    fun onCreateUser_validInput_createsAccountAndSignsIn() {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = CreateUserViewModel(repository)
        viewModel.fillIn()

        // Act
        viewModel.onCreateUser()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state.isCreated)
        assertNull(state.errorMessage)
        assertEquals(1, repository.attempts)
        assertEquals("test-user", repository.currentUserId)
    }

    @Test
    fun onCreateUser_emailAlreadyUsed_showsError() {
        // Arrange
        val repository = FakeAuthRepository().apply {
            nextOutcome = AuthOutcome.Failure(AuthError.EMAIL_IN_USE)
        }
        val viewModel = CreateUserViewModel(repository)
        viewModel.fillIn()

        // Act
        viewModel.onCreateUser()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isCreated)
        assertEquals(AuthError.EMAIL_IN_USE.toMessage(), state.errorMessage)
    }

    @Test
    fun onCreateUser_shortPassword_doesNotContactRepository() {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = CreateUserViewModel(repository)
        viewModel.fillIn(password = "12345")

        // Act
        viewModel.onCreateUser()

        // Assert
        assertEquals(0, repository.attempts)
        assertEquals(AuthValidationError.PASSWORD_TOO_SHORT.toMessage(), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun emailError_halfTypedEmail_showsHint() {
        // Arrange
        val viewModel = CreateUserViewModel(FakeAuthRepository())

        // Act
        viewModel.onEmailChange("emma@")

        // Assert
        assertNotNull(viewModel.uiState.value.emailError)
    }

    @Test
    fun emailError_emptyEmail_showsNoHint() {
        // Arrange
        val viewModel = CreateUserViewModel(FakeAuthRepository())

        // Act
        viewModel.onEmailChange("")

        // Assert
        assertNull(viewModel.uiState.value.emailError)
    }
}
