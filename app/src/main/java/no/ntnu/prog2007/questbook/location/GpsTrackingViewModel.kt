package no.ntnu.prog2007.questbook.location

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase

class GpsTrackingViewModel(private val repository: GpsRepository) : ViewModel() {
    val uiState = repository.state
    fun startTracking() = repository.startTracking()
    fun stopTracking() = repository.stopTracking()
    fun permissionDenied() = repository.permissionDenied()
    override fun onCleared() = repository.close()

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GpsTrackingViewModel::class.java))
            val tracker = LocationTracker(appContext)
            val source = object : GpsSource {
                override fun start(onPoint: (RoutePoint, Boolean) -> Unit) {
                    tracker.startTracking { location ->
                        onPoint(RoutePoint(location.latitude, location.longitude,
                            location.time, location.accuracy), location.hasAccuracy())
                    }
                }
                override fun stop() = tracker.stopTracking()
            }
            val dao = QuestBookDatabase.getDatabase(appContext).routePointDao()
            @Suppress("UNCHECKED_CAST")
            return GpsTrackingViewModel(GpsRepository(source, dao::insertPoint)) as T
        }
    }
}
