package no.ntnu.prog2007.questbook.ui.questbook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.photo.MemoryPhotoEntity
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase

/** Only the signed-in user's local history is shown; this is not a shared cloud feed yet. */
data class MemoriesUiState(
    val results: List<QuestResultEntity> = emptyList(),
    val photos: List<MemoryPhotoEntity> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModel(db: QuestBookDatabase) : ViewModel() {
    // Switching accounts cancels the previous user's database observations.
    val state = AppServices.authRepository.currentUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(MemoriesUiState(loading = false))
        else combine(db.trackingDao().observeResults(uid), db.memoryPhotoDao().observePhotos(uid)) { results, photos ->
            MemoriesUiState(results, photos, loading = false)
        }.onStart { emit(MemoriesUiState()) }
    }.catch { emit(MemoriesUiState(loading = false, error = "Could not load saved memories. Reopen this page to retry.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), MemoriesUiState())

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MemoriesViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return MemoriesViewModel(QuestBookDatabase.getDatabase(context.applicationContext)) as T
        }
    }
}
