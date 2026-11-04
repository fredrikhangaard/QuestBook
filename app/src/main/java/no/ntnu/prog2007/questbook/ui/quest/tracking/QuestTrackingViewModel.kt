package no.ntnu.prog2007.questbook.ui.quest.tracking

import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.repository.*
import no.ntnu.prog2007.questbook.data.tracking.*
import no.ntnu.prog2007.questbook.location.*
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.QuestResult

/** Screen operations are separate from GPS samples so loading/saving never resets a route. */
data class QuestTrackingUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val error: String? = null,
    val result: QuestResult? = null
)

/** Coordinates the selected assigned quest, permissions, resumable GPS, and atomic completion. */
class QuestTrackingViewModel(
    private val questId: String,
    private val quests: QuestRepository,
    private val store: QuestTrackingStore,
    private val gps: GpsRepository,
    private val currentUserId: () -> String?,
    private val canAccessGroup: (String) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val releaseGps: () -> Unit = { gps.close() },
    private val completedGps: () -> Unit = {}
) : ViewModel() {
    private val mutableState = MutableStateFlow(QuestTrackingUiState())
    val uiState = mutableState.asStateFlow()
    /** Live measurements come from the GPS engine, while uiState describes screen operations. */
    val gpsState = gps.state
    // A permission result or pending Room operation must not restart GPS after navigation.
    private var foreground = true
    private var loading = false

    init { load() }

    /** Loads previously saved progress paused, without requesting permission or starting GPS. */
    fun load() {
        if (mutableState.value.isBusy || loading || gpsState.value.isTracking) return
        loading = true
        mutableState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val user = currentUserId()
                val quest = quests.getQuest(questId)
                if (user != null && quest != null && canAccessGroup(quest.groupId)) {
                    val result = store.result(questId, user)
                    if (result != null) mutableState.update { it.copy(result = result) }
                    // A retained controller may still have queued writes newer than the Room snapshot.
                    else if (gpsState.value.sessionId.isBlank()) store.load(questId, user)?.let { gps.restore(it) }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(error = "Could not load saved tracking. Try again.") } }
            finally { loading = false; mutableState.update { it.copy(isLoading = false) } }
        }
    }

    /** Called only after Android grants precise location. Requires assignment to the current user. */
    fun start() {
        if (!foreground || uiState.value.isLoading || uiState.value.isBusy || uiState.value.result != null || gpsState.value.isTracking) return
        mutableState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val user = checkNotNull(currentUserId()) { "Sign in before tracking." }
                val quest = checkNotNull(quests.getQuest(questId)) { "Quest not found." }
                check(canAccessGroup(quest.groupId)) { "Open a group you belong to before tracking." }
                // Screen button state is not authorization: validate again before changing storage.
                validateTrackingQuest(quest, user)
                // begin() persists IN_PROGRESS and the session before requesting location updates.
                val snapshot = store.begin(questId, user, nowMillis())
                if (gpsState.value.sessionId.isBlank()) gps.restore(snapshot)
                // Navigation or app backgrounding during a Room write must not start GPS later.
                if (foreground) gps.resumeTracking()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableState.update { it.copy(error = error.message ?: "Could not start tracking.") } }
            finally { mutableState.update { it.copy(isBusy = false) } }
        }
    }

    /** Android permission remains a screen concern; this only forwards its result to the engine. */
    fun permissionDenied() = gps.permissionDenied()
    fun tick() = gps.tick()
    fun pause() = gps.stopTracking()
    /** Returning to the page makes Start/Resume possible; it does not resume automatically. */
    fun screenStarted() { foreground = true }
    /** Pauses when the page is left, the app backgrounds, or the device is locked. */
    fun screenStopped() { foreground = false; pause() }

    /** Stops location updates, drains/retries writes, then saves the result and COMPLETED state together. */
    fun finish() {
        if (uiState.value.isLoading || uiState.value.isBusy || uiState.value.result != null || !gpsState.value.distanceCompleted) return
        pause()
        mutableState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val user = checkNotNull(currentUserId())
                val quest = checkNotNull(quests.getQuest(questId))
                check(canAccessGroup(quest.groupId)) { "This group is no longer available." }
                // Finish must wait for the final accepted point/checkpoint, including any failed writes.
                gps.flush()
                val result = store.finish(questId, user, gpsState.value.sessionId, nowMillis())
                mutableState.update { it.copy(result = result) }
                completedGps()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(error = "Could not save completion. Your route is kept. Try Finish again.") } }
            finally { mutableState.update { it.copy(isBusy = false) } }
        }
    }

    /** A paused user can retry checkpoint writes without restarting the route. */
    fun retrySaving() {
        if (uiState.value.isBusy) return
        mutableState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try { gps.flush() } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(error = "Still unable to save GPS data. Try again.") } }
            finally { mutableState.update { it.copy(isBusy = false) } }
        }
    }

    /** Always stop the provider; the factory decides whether a paused writer must be retained. */
    override fun onCleared() { foreground = false; gps.stopTracking(); releaseGps() }

    /** Uses the existing Room database and Fused Location provider; no Activity is retained. */
    class Factory(context: Context, private val questId: String, private val canAccessGroup: (String) -> Boolean) : ViewModelProvider.Factory {
        private val app = context.applicationContext
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(QuestTrackingViewModel::class.java))
            val database = QuestBookDatabase.getDatabase(app)
            val store = RoomQuestTrackingStore(database)
            // Scope retained controllers to both user and quest, keeping account sessions separate.
            val key = "${AppServices.authRepository.currentUserId}:$questId"
            val gps = TrackingRuntime.controllers.getOrPut(key) {
                GpsRepository(AndroidGpsSource(app), database.routePointDao()::insertPoint,
                    persistCheckpoint = store::checkpoint)
            }
            @Suppress("UNCHECKED_CAST")
            return QuestTrackingViewModel(questId, QuestDependencies.questRepository, store, gps,
                { AppServices.authRepository.currentUserId }, canAccessGroup,
                releaseGps = {
                    // Reopening details reuses pending writes and live state, avoiding a stale checkpoint read.
                    if (gps.state.value.sessionId.isBlank()) {
                        TrackingRuntime.controllers.remove(key)
                        gps.close()
                    }
                }, completedGps = {
                    // The result is durable and all writes drained, so this controller is no longer needed.
                    TrackingRuntime.controllers.remove(key)
                    gps.close()
                }) as T
        }
    }
}

/** Paused sessions retain one serialized writer until completion or application restart. */
private object TrackingRuntime {
    val controllers = mutableMapOf<String, GpsRepository>()
}
