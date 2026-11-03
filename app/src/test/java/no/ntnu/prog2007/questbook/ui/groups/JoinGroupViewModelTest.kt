package no.ntnu.prog2007.questbook.ui.groups

import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import no.ntnu.prog2007.questbook.data.groups.GroupError
import no.ntnu.prog2007.questbook.model.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JoinGroupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val friendsGroup = Group(
        id = "walkers", name = "Weekend Walks", description = "", inviteCode = "K7MQ2X",
        ownerId = "sara", memberIds = listOf("sara")
    )

    @Test
    fun onJoin_correctCode_addsUserToGroup() {
        // Arrange
        val groups = FakeGroupRepository(listOf(friendsGroup))
        val viewModel = JoinGroupViewModel(FakeAuthRepository(signedIn = true), groups)
        viewModel.onCodeChange("k7mq2x")

        // Act
        viewModel.onJoin()

        // Assert
        assertEquals("walkers", viewModel.uiState.value.joinedGroupId)
        assertTrue("test-user" in groups.allGroups.single().memberIds)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onJoin_unknownCode_showsNotFound() {
        // Arrange
        val groups = FakeGroupRepository(listOf(friendsGroup))
        val viewModel = JoinGroupViewModel(FakeAuthRepository(signedIn = true), groups)
        viewModel.onCodeChange("ZZZZZZ")

        // Act
        viewModel.onJoin()

        // Assert
        assertNull(viewModel.uiState.value.joinedGroupId)
        assertEquals(GroupError.CODE_NOT_FOUND.toMessage(), viewModel.uiState.value.errorMessage)
        assertFalse("test-user" in groups.allGroups.single().memberIds)
    }

    @Test
    fun onJoin_incompleteCode_doesNotContactRepository() {
        // Arrange
        val groups = FakeGroupRepository(listOf(friendsGroup))
        val viewModel = JoinGroupViewModel(FakeAuthRepository(signedIn = true), groups)
        viewModel.onCodeChange("K7M")

        // Act
        viewModel.onJoin()

        // Assert
        assertEquals(0, groups.joinCalls)
        assertEquals(GroupError.INVALID_CODE.toMessage(), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onJoin_offline_showsNetworkError() {
        // Arrange
        val groups = FakeGroupRepository(listOf(friendsGroup)).apply { nextJoinError = GroupError.NETWORK }
        val viewModel = JoinGroupViewModel(FakeAuthRepository(signedIn = true), groups)
        viewModel.onCodeChange("K7MQ2X")

        // Act
        viewModel.onJoin()

        // Assert
        assertFalse(viewModel.uiState.value.isJoining)
        assertEquals(GroupError.NETWORK.toMessage(), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onCodeChange_typedWithSpacesAndLowercase_isNormalizedAndCut() {
        // Arrange
        val viewModel = JoinGroupViewModel(FakeAuthRepository(signedIn = true), FakeGroupRepository())

        // Act
        viewModel.onCodeChange("k7m q2-x9")

        // Assert
        assertEquals("K7MQ2X", viewModel.uiState.value.code)
    }

    @Test
    fun onJoin_alreadyMember_showsAlreadyInGroupAndChangesNothing() {
        // Arrange
        val myGroup = friendsGroup.copy(memberIds = listOf("sara", "test-user"))
        val groups = FakeGroupRepository(listOf(myGroup))
        val viewModel = JoinGroupViewModel(FakeAuthRepository(signedIn = true), groups)
        viewModel.onCodeChange("K7MQ2X")

        // Act
        viewModel.onJoin()

        // Assert
        assertNull(viewModel.uiState.value.joinedGroupId)
        assertEquals(GroupError.ALREADY_MEMBER.toMessage(), viewModel.uiState.value.errorMessage)
        assertEquals(listOf("sara", "test-user"), groups.allGroups.single().memberIds)
    }
}
