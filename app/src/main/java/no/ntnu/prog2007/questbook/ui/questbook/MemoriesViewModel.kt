package no.ntnu.prog2007.questbook.ui.questbook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import java.time.ZoneId
import no.ntnu.prog2007.questbook.data.personal.*
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
    val error: String? = null,
    val summary: WeeklyActivitySummary? = null,
    val weekOffset: Long = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModel(db: QuestBookDatabase) : ViewModel() {
    private val selectedWeek = MutableStateFlow(0L)
    private var summaryUser: String? = null
    fun previousWeek() { selectedWeek.value = (selectedWeek.value - 1).coerceAtLeast(-520) }
    fun nextWeek() { selectedWeek.value = (selectedWeek.value + 1).coerceAtMost(0) }

    // Results with a GPS session represent actual activity. Goal awards do not add distance again.
    val state = AppServices.authRepository.currentUserIdFlow.flatMapLatest { uid ->
        if (summaryUser != uid) {
            selectedWeek.value = 0
            summaryUser = uid
        }
        if (uid == null) flowOf(MemoriesUiState(loading = false))
        else combine(db.trackingDao().observeResults(uid), db.memoryPhotoDao().observePhotos(uid),
            db.personalQuestDao().observeEntries(uid), db.personalQuestDao().observeAssignments(uid),
            combine(selectedWeek, flow { while (true) { emit(System.currentTimeMillis()); delay(30000) } }) { offset, now -> offset to now }
        ) { results, photos, entries, assignments, selection ->
            val window = questWindow(selection.second, ZoneId.systemDefault(), QuestCadence.WEEKLY, selection.first)
            MemoriesUiState(results.filter { it.completedAtMillis >= window.start && it.completedAtMillis < window.end }, photos,
                loading = false, summary = weeklyActivitySummary(results, assignments, questActivities(results, entries), window),
                weekOffset = selection.first)
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
