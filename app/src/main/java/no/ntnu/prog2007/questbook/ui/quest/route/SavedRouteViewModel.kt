package no.ntnu.prog2007.questbook.ui.quest.route

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase

/** Reads only the route linked to a completion owned by the current account. */
class RoomSavedRouteReader(private val db: QuestBookDatabase) {
    suspend fun load(questId: String, userId: String): SavedRoute? {
        val result = db.trackingDao().getResult(questId)?.takeIf { it.userId == userId } ?: return null
        val points = result.gpsSessionId?.let { session ->
            db.routePointDao().getPointsForSession(session).first().mapNotNull { point ->
                if (point.latitude.isFinite() && point.longitude.isFinite()
                    && point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0)
                    RoutePosition(point.latitude, point.longitude) else null
            }
        }.orEmpty()
        return SavedRoute(result.toResult(), points)
    }
}

data class SavedRouteUiState(val loading: Boolean = true, val route: SavedRoute? = null,
    val error: String? = null)

/** Account changes cancel pending reads and remove the previously displayed private route. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedRouteViewModel(
    questId: String,
    users: Flow<String?>,
    loadRoute: suspend (String, String) -> SavedRoute?
) : ViewModel() {
    private val retries = MutableStateFlow(0)
    val state = combine(users, retries) { uid, _ -> uid }.flatMapLatest { uid ->
        flow {
            emit(SavedRouteUiState())
            if (uid == null) emit(SavedRouteUiState(loading = false))
            else try {
                emit(SavedRouteUiState(loading = false, route = loadRoute(questId, uid)))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                emit(SavedRouteUiState(loading = false, error = "Could not load this saved route."))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), SavedRouteUiState())

    fun retry() { retries.value += 1 }

    class Factory(private val context: Context, private val questId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SavedRouteViewModel::class.java))
            val reader = RoomSavedRouteReader(QuestBookDatabase.getDatabase(context.applicationContext))
            @Suppress("UNCHECKED_CAST")
            return SavedRouteViewModel(questId, AppServices.authRepository.currentUserIdFlow, reader::load) as T
        }
    }
}
