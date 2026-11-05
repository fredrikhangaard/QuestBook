package no.ntnu.prog2007.questbook.ui.quest.route

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.model.QuestResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedRouteViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val sample = SavedRoute(QuestResult("r", "q", "g", "me", "Walk", "Me", 1000, 500, 10.0, "r"), emptyList())

    @Test fun signOutCancelsPendingReadAndDoesNotRestorePrivateRoute() = runTest {
        val users = MutableStateFlow<String?>("me")
        val pending = CompletableDeferred<SavedRoute?>()
        val model = SavedRouteViewModel("q", users) { _, _ -> pending.await() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        assertTrue(model.state.value.loading)
        users.value = null
        assertFalse(model.state.value.loading)
        pending.complete(sample)
        assertNull(model.state.value.route)
    }
    @Test fun accountChangeLoadsUsingNewUidAndClearsOldResult() = runTest {
        val users = MutableStateFlow<String?>("me")
        val requested = mutableListOf<String>()
        val model = SavedRouteViewModel("q", users) { id, uid ->
            assertEquals("q", id); requested += uid
            if (uid == "me") sample else null
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        assertEquals(sample, model.state.value.route)
        users.value = "other"
        assertNull(model.state.value.route)
        assertEquals(listOf("me", "other"), requested)
    }
    @Test fun databaseFailureCanBeRetriedWithoutReopeningPage() = runTest {
        var fail = true
        val model = SavedRouteViewModel("q", MutableStateFlow("me")) { _, _ ->
            if (fail) error("Database unavailable") else sample
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        assertNotNull(model.state.value.error)
        fail = false; model.retry()
        assertNull(model.state.value.error)
        assertEquals(sample, model.state.value.route)
    }
}
