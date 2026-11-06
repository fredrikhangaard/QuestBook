package no.ntnu.prog2007.questbook.ui.quest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Personal challenges and group quests share this tab without mixing ownership or assignment. */
@Composable
fun QuestsScreen(onQuestClick: (String) -> Unit, questsViewModel: QuestsViewModel = viewModel(),
    personalState: no.ntnu.prog2007.questbook.ui.personal.PersonalQuestsUiState? = null,
    onPersonalQuest: (String) -> Unit = {}, onRefreshPersonal: () -> Unit = {},
    challengeState: no.ntnu.prog2007.questbook.ui.challenges.GroupChallengesUiState? = null) {
    val quests by questsViewModel.quests.collectAsStateWithLifecycle()
    val cards = challengeState?.let { groupState ->
        no.ntnu.prog2007.questbook.data.challenges.groupQuestCards(quests, groupState.snapshot, groupState.userId, groupState.groups.map { it.id }.toSet())
            .filterNot { parent -> parent.status == QuestStatus.COMPLETED && groupState.snapshot.attempts.any { attempt ->
                attempt.challengeId == parent.id && attempt.userId == groupState.userId && personalState?.quests?.any { it.assignment.questId == attempt.attemptId && it.complete } == true
            } }
    } ?: quests
    QuestList(cards, onQuestClick, personalState, onPersonalQuest, onRefreshPersonal)
}

/** Scrollable quest rows; completed history is rendered separately from active quests. */
@Composable
internal fun QuestList(quests: List<Quest>, onQuestClick: (String) -> Unit,
    personalState: no.ntnu.prog2007.questbook.ui.personal.PersonalQuestsUiState? = null,
    onPersonalQuest: (String) -> Unit = {}, onRefreshPersonal: () -> Unit = {}) {
    val visible = quests.filter { it.status != QuestStatus.COMPLETED }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (personalState != null) item {
            no.ntnu.prog2007.questbook.ui.personal.PersonalQuestSection(personalState, onPersonalQuest, onRefreshPersonal)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visible.isNotEmpty()) Text("Group quests", style = MaterialTheme.typography.titleLarge)
            }
        }
        items(visible, key = { it.id }) { quest -> QuestCard(quest) { onQuestClick(quest.id) } }
        item { Text("Completed quests", style = MaterialTheme.typography.titleLarge) }
        val completed = personalState?.quests.orEmpty().filter { it.complete && it.hasResult }
            .sortedByDescending { it.assignment.completedAtMillis }
        if (completed.isEmpty() && quests.none { it.status == QuestStatus.COMPLETED }) item { Text("Your completed quests will appear here.") }
        items(completed, key = { "completed-${it.assignment.questId}" }) { progress ->
            no.ntnu.prog2007.questbook.ui.personal.PersonalQuestCard(progress, onPersonalQuest)
        }
        items(quests.filter { it.status == QuestStatus.COMPLETED }, key = { "legacy-${it.id}" }) { quest -> QuestCard(quest) { onQuestClick(quest.id) } }
    }
}
