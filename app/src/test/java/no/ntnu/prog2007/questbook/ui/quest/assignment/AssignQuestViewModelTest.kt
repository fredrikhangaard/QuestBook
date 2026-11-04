package no.ntnu.prog2007.questbook.ui.quest.assignment

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import no.ntnu.prog2007.questbook.data.groups.GroupRepository
import no.ntnu.prog2007.questbook.data.repository.FakeQuestRepository
import no.ntnu.prog2007.questbook.data.repository.QuestRepository
import no.ntnu.prog2007.questbook.model.*
import no.ntnu.prog2007.questbook.ui.groups.GroupSummary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Assignment authorization, actual stored IDs/names, and asynchronous persistence failures. */
class AssignQuestViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val auth = FakeAuthRepository(signedIn = true)
    private val names = mapOf("test-user" to "Test User", "emma" to "Emma")
    private val groups = FakeGroupRepository(usernames = names)
    private var group = GroupSummary("crew", "Crew", listOf("You", "Emma"), "",
        memberIds = listOf("test-user", "emma"))
    private fun vm(repo: QuestRepository, groupRepo: GroupRepository = groups) =
        AssignQuestViewModel("quest", repo, auth, groupRepo) { group }
    private suspend fun repository(status: QuestStatus = QuestStatus.NOT_STARTED): FakeQuestRepository {
        val repo = FakeQuestRepository()
        repo.addQuest(Quest("quest", "crew", QuestType.DISTANCE, "Walk", "Walk outside", "", status,
            requiredDistanceKm = 1.0, createdByUserId = "test-user"))
        return repo
    }

    @Test fun selectionStoresActualIdAndUsernameThenCanBeReassignedBeforeStart() = runBlocking {
        val repo = repository()
        val vm = vm(repo)
        vm.open()
        assertTrue(vm.uiState.value.members.first().isCurrentUser)
        vm.select("test-user"); vm.assign()
        assertEquals("test-user", repo.getQuest("quest")!!.assignedUserId)
        assertEquals("Test User", repo.getQuest("quest")!!.assignedTo)
        assertFalse(vm.uiState.value.isOpen)
        vm.open()
        assertEquals("test-user", vm.uiState.value.selectedUserId)
        vm.select("emma"); vm.assign()
        assertEquals("emma", repo.getQuest("quest")!!.assignedUserId)
        assertEquals("Emma", repo.getQuest("quest")!!.assignedTo)
        assertEquals(1.0, repo.getQuest("quest")!!.requiredDistanceKm!!, 0.0)
    }

    @Test fun missingSelectionAndOutsiderCannotBeAssigned() = runBlocking {
        val repo = repository()
        val vm = vm(repo)
        vm.open(); vm.select("outsider"); vm.assign()
        assertNull(repo.getQuest("quest")!!.assignedUserId)
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun membershipIsRecheckedAfterMemberWasChosen() = runBlocking {
        val repo = repository()
        val vm = vm(repo)
        vm.open(); vm.select("emma")
        // A member leaves after selection but before save: the old picker is not sufficient authorization.
        group = group.copy(memberIds = listOf("test-user"), members = listOf("You"))
        vm.assign()
        assertNull(repo.getQuest("quest")!!.assignedUserId)
        assertTrue(vm.uiState.value.error!!.contains("no longer"))
    }

    @Test fun requesterMustStillBelongToCorrectGroup() = runBlocking {
        val repo = repository()
        val vm = vm(repo)
        vm.open(); vm.select("emma")
        group = group.copy(memberIds = listOf("emma"))
        vm.assign()
        assertNull(repo.getQuest("quest")!!.assignedUserId)
        assertNotNull(vm.uiState.value.error)
        group = group.copy(id = "different", memberIds = listOf("test-user", "emma"))
        vm.open()
        assertTrue(vm.uiState.value.members.isEmpty())
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun signedOutAndStartedOrCompletedQuestsCannotOpenPicker() = runBlocking {
        for (status in listOf(QuestStatus.IN_PROGRESS, QuestStatus.COMPLETED)) {
            val vm = vm(repository(status))
            vm.open()
            assertTrue(vm.uiState.value.members.isEmpty())
            assertNotNull(vm.uiState.value.error)
        }
        val vm = vm(repository())
        auth.signOut(); vm.open()
        assertTrue(vm.uiState.value.members.isEmpty())
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun saveFailureKeepsSelectionAndCanBeRetried() = runBlocking {
        val repo = repository()
        var failing = true
        val wrapped = object : QuestRepository by repo {
            override suspend fun assignQuest(questId: String, groupId: String, userId: String, displayName: String) {
                check(!failing) { "Disk is unavailable" }
                repo.assignQuest(questId, groupId, userId, displayName)
            }
        }
        val vm = vm(wrapped)
        vm.open(); vm.select("test-user"); vm.assign()
        assertTrue(vm.uiState.value.isOpen)
        assertEquals("test-user", vm.uiState.value.selectedUserId)
        assertNotNull(vm.uiState.value.error)
        failing = false; vm.assign()
        assertFalse(vm.uiState.value.isOpen)
        assertEquals("test-user", repo.getQuest("quest")!!.assignedUserId)
    }

    @Test fun repeatedSaveTapsWaitForOneWrite() = runBlocking {
        val repo = repository()
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val wrapped = object : QuestRepository by repo {
            override suspend fun assignQuest(questId: String, groupId: String, userId: String, displayName: String) {
                writes++; gate.await(); repo.assignQuest(questId, groupId, userId, displayName)
            }
        }
        val vm = vm(wrapped)
        vm.open(); vm.select("emma"); vm.assign(); vm.assign()
        vm.dismiss()
        assertTrue(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.isOpen)
        gate.complete(Unit)
        assertEquals(1, writes)
        assertFalse(vm.uiState.value.isOpen)
    }

    @Test fun loadingCanBeCancelledWithoutReopeningTheDialog() = runBlocking {
        val repo = repository()
        val gate = CompletableDeferred<Unit>()
        val delayed = object : GroupRepository by groups {
            override suspend fun getUsernames(userIds: List<String>): Map<String, String> {
                gate.await(); return names
            }
        }
        val vm = vm(repo, delayed)
        vm.open(); assertTrue(vm.uiState.value.isLoading)
        vm.dismiss(); gate.complete(Unit)
        assertFalse(vm.uiState.value.isOpen)
        assertFalse(vm.uiState.value.isLoading)
        vm.open()
        assertEquals(2, vm.uiState.value.members.size)
    }
}
