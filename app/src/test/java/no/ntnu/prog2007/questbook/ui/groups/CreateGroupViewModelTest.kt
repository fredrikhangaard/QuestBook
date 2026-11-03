package no.ntnu.prog2007.questbook.ui.groups

import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import no.ntnu.prog2007.questbook.data.groups.GroupValidationError
import no.ntnu.prog2007.questbook.data.groups.GroupValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class CreateGroupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun onCreate_validName_createsGroupOwnedBySignedInUser() {
        // Arrange
        val groups = FakeGroupRepository(newInviteCode = { "K7MQ2X" })
        val viewModel = CreateGroupViewModel(FakeAuthRepository(signedIn = true), groups)
        viewModel.onNameChange("Mountain Crew")
        viewModel.onDescriptionChange("Weekend hikes")

        // Act
        viewModel.onCreate()

        // Assert
        val created = groups.allGroups.single()
        assertEquals(created.id, viewModel.uiState.value.createdGroupId)
        assertEquals("Mountain Crew", created.name)
        assertEquals("test-user", created.ownerId)
        assertEquals(listOf("test-user"), created.memberIds)
        assertEquals("K7MQ2X", created.inviteCode)
    }

    @Test
    fun onCreate_blankName_showsErrorAndCreatesNothing() {
        // Arrange
        val groups = FakeGroupRepository()
        val viewModel = CreateGroupViewModel(FakeAuthRepository(signedIn = true), groups)
        viewModel.onNameChange("   ")

        // Act
        viewModel.onCreate()

        // Assert
        assertEquals(0, groups.createCalls)
        assertEquals(GroupValidationError.BLANK_NAME.toMessage(), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onCreate_notSignedIn_showsErrorAndCreatesNothing() {
        // Arrange
        val groups = FakeGroupRepository()
        val viewModel = CreateGroupViewModel(FakeAuthRepository(signedIn = false), groups)
        viewModel.onNameChange("Mountain Crew")

        // Act
        viewModel.onCreate()

        // Assert
        assertEquals(0, groups.createCalls)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onNameChange_tooLongName_isCutAtMaximum() {
        // Arrange
        val viewModel = CreateGroupViewModel(FakeAuthRepository(signedIn = true), FakeGroupRepository())

        // Act
        viewModel.onNameChange("a".repeat(GroupValidator.MAX_NAME_LENGTH + 10))

        // Assert
        assertEquals(GroupValidator.MAX_NAME_LENGTH, viewModel.uiState.value.name.length)
    }

    @Test
    fun canSubmit_emptyName_isFalse() {
        // Arrange
        val viewModel = CreateGroupViewModel(FakeAuthRepository(signedIn = true), FakeGroupRepository())

        // Act
        viewModel.onDescriptionChange("Only a description")

        // Assert
        assertFalse(viewModel.uiState.value.canSubmit)
    }
}
