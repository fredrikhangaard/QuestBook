package no.ntnu.prog2007.questbook.ui.groups

import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import no.ntnu.prog2007.questbook.model.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GroupsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val crew = Group(
        id = "crew", name = "Mountain Crew", description = "", inviteCode = "K7MQ2X",
        ownerId = "emma", memberIds = listOf("emma", "test-user")
    )
    private val walkers = Group(
        id = "walkers", name = "Weekend Walks", description = "", inviteCode = "W4LK3R",
        ownerId = "sara", memberIds = listOf("sara")
    )
    private val usernames = mapOf("emma" to "Emma", "sara" to "Sara", "test-user" to "Test User")

    @Test
    fun groups_signedInUser_seesOnlyOwnGroups() {
        // Arrange
        val auth = FakeAuthRepository(signedIn = true)
        val repository = FakeGroupRepository(listOf(crew, walkers), usernames)

        // Act
        val viewModel = GroupsViewModel(auth, repository)

        // Assert
        assertEquals(listOf("crew"), viewModel.groups.value.map { it.id })
    }

    @Test
    fun groups_signedInUser_isListedFirstAsYou() {
        // Arrange
        val auth = FakeAuthRepository(signedIn = true)
        val repository = FakeGroupRepository(listOf(crew), usernames)

        // Act
        val viewModel = GroupsViewModel(auth, repository)

        // Assert
        assertEquals(listOf("You", "Emma"), viewModel.groups.value.single().members)
    }

    @Test
    fun groups_nobodySignedIn_isEmpty() {
        // Arrange
        val auth = FakeAuthRepository(signedIn = false)
        val repository = FakeGroupRepository(listOf(crew, walkers), usernames)

        // Act
        val viewModel = GroupsViewModel(auth, repository)

        // Assert
        assertTrue(viewModel.groups.value.isEmpty())
    }

    @Test
    fun groups_newGroupCreated_appearsWithoutReloading() {
        // Arrange
        val auth = FakeAuthRepository(signedIn = true)
        val repository = FakeGroupRepository(usernames = usernames)
        val viewModel = GroupsViewModel(auth, repository)

        // Act
        repository.createGroup("test-user", "Trail Runners", "")

        // Assert
        assertEquals(listOf("Trail Runners"), viewModel.groups.value.map { it.name })
    }

    @Test
    fun groups_afterSignOut_areCleared() {
        // Arrange
        val auth = FakeAuthRepository(signedIn = true)
        val viewModel = GroupsViewModel(auth, FakeGroupRepository(listOf(crew), usernames))

        // Act
        auth.signOut()

        // Assert: the next person using the phone does not see the previous user's groups
        assertTrue(viewModel.groups.value.isEmpty())
    }

    @Test
    fun groups_userSignsIn_seesTheirGroups() {
        // Arrange
        val auth = FakeAuthRepository(signedIn = false)
        val viewModel = GroupsViewModel(auth, FakeGroupRepository(listOf(crew, walkers), usernames))

        // Act
        runBlocking { auth.signIn("test@example.com", "secret123") }

        // Assert
        assertEquals(listOf("crew"), viewModel.groups.value.map { it.id })
    }
}
