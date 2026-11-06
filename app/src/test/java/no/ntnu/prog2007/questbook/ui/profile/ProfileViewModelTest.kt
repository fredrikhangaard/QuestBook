package no.ntnu.prog2007.questbook.ui.profile

import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.data.auth.AuthRepository

class ProfileViewModelTest {
    @Test fun failedProfileLoadCanRetryWithoutSigningOut() {
        var failing = true
        val fake = FakeAuthRepository(signedIn = true)
        val auth = object : AuthRepository by fake {
            override suspend fun getCurrentUser(): User? {
                check(!failing) { "offline" }
                return fake.getCurrentUser()
            }
        }
        val vm = ProfileViewModel(auth)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.error != null)
        failing = false
        vm.load()
        assertNull(vm.uiState.value.error)
        assertEquals(fake.currentUserId, vm.uiState.value.user?.uid)
    }

    @Test fun logoutWhileProfileLoadsCannotRestoreOldProfile() = runBlocking {
        val response = CompletableDeferred<User?>()
        val fake = FakeAuthRepository(signedIn = true)
        val auth = object : AuthRepository by fake {
            override suspend fun getCurrentUser(): User? = response.await()
        }
        val vm = ProfileViewModel(auth)
        vm.onSignOut()
        response.complete(User("old-user", "Old user", "old@example.test"))
        assertNull(vm.uiState.value.user)
        assertTrue(vm.uiState.value.isSignedOut)
        assertFalse(vm.uiState.value.isLoading)
    }

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun init_signedInUser_isLoaded() {
        // Arrange
        val emma = User(uid = "u1", username = "Emma", email = "emma@example.com")
        val repository = FakeAuthRepository(user = emma, signedIn = true)

        // Act
        val viewModel = ProfileViewModel(repository)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(emma, state.user)
        assertFalse(state.isLoading)
    }

    @Test
    fun init_nobodySignedIn_showsNoUser() {
        // Arrange
        val repository = FakeAuthRepository(signedIn = false)

        // Act
        val viewModel = ProfileViewModel(repository)

        // Assert
        val state = viewModel.uiState.value
        assertNull(state.user)
        assertFalse(state.isLoading)
    }

    @Test
    fun onSignOut_signsOutAndMarksState() {
        // Arrange
        val repository = FakeAuthRepository(signedIn = true)
        val viewModel = ProfileViewModel(repository)

        // Act
        viewModel.onSignOut()

        // Assert
        assertTrue(viewModel.uiState.value.isSignedOut)
        assertNull(repository.currentUserId)
    }
}
