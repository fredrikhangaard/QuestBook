package no.ntnu.prog2007.questbook.ui.quest.tracking

import kotlinx.coroutines.*
import no.ntnu.prog2007.questbook.MainDispatcherRule
import no.ntnu.prog2007.questbook.data.repository.FakeQuestRepository
import no.ntnu.prog2007.questbook.data.tracking.QuestTrackingStore
import no.ntnu.prog2007.questbook.location.*
import no.ntnu.prog2007.questbook.location.database.RoutePointEntity
import no.ntnu.prog2007.questbook.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuestTrackingViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private class Source : GpsSource {
        var starts = 0
        lateinit var point: (RoutePoint, Boolean) -> Unit
        override fun start(onPoint: (RoutePoint, Boolean) -> Unit) { starts++; point = onPoint }
        override fun stop() {}
    }
    // This fake isolates coordinator decisions; QuestTrackingStoreTest covers real Room transactions.
    private class Store : QuestTrackingStore {
        var begins = 0
        var finishes = 0
        var failFinish = false
        var gate: CompletableDeferred<Unit>? = null
        var saved = GpsTrackingUiState(sessionId = "session", requiredDistanceMeters = 5.0)
        override suspend fun load(questId: String, userId: String): GpsTrackingUiState? = null
        override suspend fun result(questId: String, userId: String): QuestResult? = null
        override suspend fun begin(questId: String, userId: String, nowMillis: Long): GpsTrackingUiState {
            begins++; gate?.await(); return saved
        }
        override suspend fun checkpoint(point: RoutePointEntity?, state: GpsTrackingUiState) { saved = state }
        override suspend fun finish(questId: String, userId: String, sessionId: String, nowMillis: Long): QuestResult {
            finishes++; check(!failFinish)
            check(saved.distanceCompleted)
            return QuestResult(sessionId, questId, "group", userId, "Walk", "Me", nowMillis,
                saved.elapsedMillis, saved.distanceMeters, sessionId)
        }
    }
    private suspend fun setup(assignee: String? = "me", type: QuestType = QuestType.DISTANCE,
        access: Boolean = true, currentUser: () -> String? = { "me" },
        background: Boolean = false): Triple<QuestTrackingViewModel, Source, Pair<GpsRepository, Store>> {
        val quests = FakeQuestRepository()
        quests.addQuest(Quest("gps", "group", type, "Walk", "Walk outside", "Me", QuestStatus.NOT_STARTED,
            requiredDistanceKm = 0.005, assignedUserId = assignee))
        val source = Source()
        val store = Store()
        val gps = GpsRepository(source, {}, Dispatchers.Unconfined, persistCheckpoint = store::checkpoint)
        val vm = QuestTrackingViewModel("gps", quests, store, gps, currentUser, { access },
            keepTrackingInBackground = background)
        return Triple(vm, source, gps to store)
    }

    @Test fun onlyAssignedDistanceQuestsInCurrentGroupStart() = runBlocking {
        for ((assignee, type, access) in listOf(Triple(null, QuestType.DISTANCE, true),
            Triple("other", QuestType.DISTANCE, true), Triple("me", QuestType.WORKOUT, true),
            Triple("me", QuestType.DISTANCE, false))) {
            val (vm, source, dependencies) = setup(assignee, type, access)
            try {
                vm.start()
                assertEquals(0, source.starts)
                assertEquals(0, dependencies.second.begins)
                assertNotNull(vm.uiState.value.error)
            } finally { dependencies.first.close() }
        }
    }

    @Test fun finishWaitsForSavedGpsAndCannotCreateDuplicateResults() = runBlocking {
        val (vm, source, dependencies) = setup()
        try {
            vm.start()
            vm.finish()
            assertEquals(0, dependencies.second.finishes)
            source.point(RoutePoint(0.0, 0.0, 1000, 5f), true)
            source.point(RoutePoint(0.00005, 0.0, 4000, 5f), true)
            assertFalse(vm.gpsState.value.isTracking)
            vm.finish(); vm.finish()
            assertEquals(1, dependencies.second.finishes)
            assertEquals("session", vm.uiState.value.result?.gpsSessionId)
            assertTrue(vm.uiState.value.result!!.distanceMeters!! >= 5)
        } finally { dependencies.first.close() }
    }

    @Test fun failedCompletionCanBeRetriedWithoutResettingRoute() = runBlocking {
        val (vm, source, dependencies) = setup()
        try {
            vm.start()
            source.point(RoutePoint(0.0, 0.0, 1000, 5f), true)
            source.point(RoutePoint(0.00005, 0.0, 4000, 5f), true)
            dependencies.second.failFinish = true
            vm.finish()
            assertNull(vm.uiState.value.result)
            assertNotNull(vm.uiState.value.error)
            dependencies.second.failFinish = false
            vm.finish()
            assertNotNull(vm.uiState.value.result)
            assertEquals(2, dependencies.second.finishes)
            assertEquals(1, source.starts)
        } finally { dependencies.first.close() }
    }

    @Test fun leavingDuringStartWriteDoesNotStartLocationLater() = runBlocking {
        val (vm, source, dependencies) = setup()
        try {
            // Hold the database operation open while the screen leaves the foreground.
            dependencies.second.gate = CompletableDeferred()
            vm.start()
            vm.screenStopped()
            dependencies.second.gate!!.complete(Unit)
            assertEquals(0, source.starts)
            assertFalse(vm.gpsState.value.isTracking)
            vm.screenStarted()
            vm.start()
            assertEquals(1, source.starts)
        } finally { dependencies.first.close() }
    }

    @Test fun serviceOwnedWalkContinuesWhenScreenStopsButPauseStillWorks() = runBlocking {
        val (vm, source, dependencies) = setup(background = true)
        try {
            vm.start()
            vm.screenStopped()
            assertTrue(vm.gpsState.value.isTracking)
            source.point(RoutePoint(0.0, 0.0, 1000, 5f), true)
            assertEquals(1, vm.gpsState.value.pointCount)
            vm.pause()
            source.point(RoutePoint(0.00005, 0.0, 4000, 5f), true)
            assertFalse(vm.gpsState.value.isTracking)
            assertEquals(1, vm.gpsState.value.pointCount)
        } finally { dependencies.first.close() }
    }

    @Test fun accountChangeDuringRoomWriteCannotStartPreviousUsersGps() = runBlocking {
        var user: String? = "me"
        val (vm, source, dependencies) = setup(currentUser = { user }, background = true)
        try {
            dependencies.second.gate = CompletableDeferred()
            vm.start()
            user = "another-account"
            dependencies.second.gate!!.complete(Unit)
            assertEquals(0, source.starts)
            assertFalse(vm.gpsState.value.isTracking)
            assertNotNull(vm.uiState.value.error)
        } finally { dependencies.first.close() }
    }
}
