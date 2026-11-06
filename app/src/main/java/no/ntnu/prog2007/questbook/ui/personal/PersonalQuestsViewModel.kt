package no.ntnu.prog2007.questbook.ui.personal

import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZoneId
import java.util.UUID
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase

/** One shared observer keeps solo quests current on Home, Quests, and personal details. */
data class PersonalQuestsUiState(val userId: String? = null, val quests: List<PersonalQuestProgress> = emptyList(),
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalQuestsViewModel(private val db: QuestBookDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault) : ViewModel() {
    private val store = PersonalQuestStore(db)
    private val mutable = MutableStateFlow(PersonalQuestsUiState())
    val state = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)
    private var pending: Pair<String, String>? = null

    init {
        viewModelScope.launch {
            AppServices.authRepository.currentUserIdFlow.collectLatest { uid ->
                mutable.value = PersonalQuestsUiState(userId = uid, loading = uid != null)
                if (uid == null) return@collectLatest
                combine(db.personalQuestDao().observeAssignments(uid), db.personalQuestDao().observeEntries(uid),
                    db.trackingDao().observeResults(uid), refreshes,
                    flow { while (true) { emit(now()); delay(30000) } }) { _, _, _, _, time -> time }
                    .collectLatest { time ->
                        try {
                            val snapshot = store.refresh(uid, time, zone())
                            mutable.update { it.copy(userId = uid, quests = snapshot.quests, loading = false, error = null) }
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { mutable.update { it.copy(loading = false, error = "Could not load personal quests. Try refreshing.") } }
                    }
            }
        }
    }
    fun refresh() { refreshes.value += 1 }

    private fun action(block: suspend (String) -> Unit) {
        if (state.value.busy) return
        val uid = AppServices.authRepository.currentUserId ?: return
        if (state.value.userId != uid) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block(uid); refresh() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = error.message ?: "Could not save activity. Try again.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun logManual(id: String, quantity: String, minutes: String) = action { uid ->
        val amount = quantity.trim().replace(',', '.').toDoubleOrNull() ?: error("Enter a valid activity amount.")
        val time = if (minutes.isBlank()) 0.0 else minutes.trim().replace(',', '.').toDoubleOrNull() ?: error("Enter valid active minutes.")
        val key = "$uid/$id/$quantity/$minutes"
        val operation = pending?.takeIf { it.first == key }?.second ?: UUID.randomUUID().toString().also { pending = key to it }
        store.logManual(uid, id, amount, time, operation, now())
        pending = null
    }
    /** A weekly distance goal starts a bounded 5 km (or remaining-distance) quest, never passive tracking. */
    fun track(id: String, onReady: (String) -> Unit) = action { uid ->
        val progress = checkNotNull(state.value.quests.find { it.assignment.questId == id })
        val target = if (progress.assignment.cadence == "WEEKLY") {
            val remaining = progress.assignment.target - progress.value
            check(progress.hasSavedSession || (remaining > 0 && now() < progress.assignment.periodEndMillis))
            store.boundedWalk(uid, id, remaining, now(), zone())
        } else id
        store.prepareGps(uid, target, now())
        if (AppServices.authRepository.currentUserId == uid) onReady(target)
    }
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PersonalQuestsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return PersonalQuestsViewModel(QuestBookDatabase.getDatabase(context.applicationContext)) as T
        }
    }
}
