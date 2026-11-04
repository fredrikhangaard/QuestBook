package no.ntnu.prog2007.questbook.location

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase

/**
 * Small coordinator for the standalone GPS test screen. Quest-specific start/finish
 * rules live in QuestTrackingViewModel; both screens reuse the same GPS engine.
 */
class GpsTrackingViewModel(private val repository: GpsRepository) : ViewModel() {
    val uiState = repository.state
    fun startTracking() = repository.startTracking()
    fun stopTracking() = repository.stopTracking()
    fun permissionDenied() = repository.permissionDenied()
    override fun onCleared() = repository.close()

    /** Wires a point-only repository for testing GPS without creating a quest result. */
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GpsTrackingViewModel::class.java))
            val source = AndroidGpsSource(appContext)
            val dao = QuestBookDatabase.getDatabase(appContext).routePointDao()
            @Suppress("UNCHECKED_CAST")
            return GpsTrackingViewModel(GpsRepository(source, dao::insertPoint)) as T
        }
    }
}
