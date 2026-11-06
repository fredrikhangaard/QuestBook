package no.ntnu.prog2007.questbook.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.ui.groups.GroupSummary
import no.ntnu.prog2007.questbook.ui.groups.HomeGroups
import no.ntnu.prog2007.questbook.ui.quest.QuestCard

/** Home combines current group shortcuts with the existing quest list. */
@Composable
fun TodayScreen(
    onQuestClick: (String) -> Unit,
    onQuestBookClick: () -> Unit,
    todayViewModel: TodayViewModel = viewModel(),
    onAllQuestsClick: () -> Unit = {},
    groups: List<GroupSummary> = emptyList(),
    onGroupClick: (String) -> Unit = {},
    onAddGroup: () -> Unit = {},
    personalState: no.ntnu.prog2007.questbook.ui.personal.PersonalQuestsUiState? = null,
    onPersonalQuest: (String) -> Unit = {}, onRefreshPersonal: () -> Unit = {},
    challengeState: no.ntnu.prog2007.questbook.ui.challenges.GroupChallengesUiState? = null
) {
    val uiState by todayViewModel.uiState.collectAsState()
    val cards = challengeState?.let { no.ntnu.prog2007.questbook.data.challenges.groupQuestCards(uiState.quests, it.snapshot, it.userId, it.groups.map { g -> g.id }.toSet()) } ?: uiState.quests
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // One group gets a wide card; multiple groups use circular picture shortcuts.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("QuestBook", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                HomeGroups(groups, onGroupClick, onAddGroup)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (personalState != null) item {
            no.ntnu.prog2007.questbook.ui.personal.PersonalQuestSection(personalState, onPersonalQuest, onRefreshPersonal)
            HorizontalDivider(Modifier.padding(top = 16.dp))
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Group quests", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onAllQuestsClick) { Text("See all", style = MaterialTheme.typography.labelMedium) }
            }
        }
        // Cards retain the existing quest-detail navigation callbacks.
        if (cards.isEmpty()) item { Text("No group quests yet.") }
        items(cards.filter { it.status != QuestStatus.COMPLETED }, key = { it.id }) { quest ->
            QuestCard(quest) { onQuestClick(quest.id) }
        }
        // Completed samples remain accessible without appearing under Active quests.
        if (cards.any { it.status == QuestStatus.COMPLETED }) {
            item {
                Text("Completed quests", modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(cards.filter { it.status == QuestStatus.COMPLETED }, key = { it.id }) { quest ->
                QuestCard(quest) { onQuestClick(quest.id) }
            }
        }
        item {
            TextButton(onClick = onQuestBookClick, modifier = Modifier.fillMaxWidth()) { Text("Open QuestBook") }
        }
    }
}
