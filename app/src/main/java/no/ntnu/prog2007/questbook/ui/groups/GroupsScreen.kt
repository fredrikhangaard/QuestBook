package no.ntnu.prog2007.questbook.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.ui.quest.QuestCard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** All current groups with more detail than the Home shortcuts. */
@Composable
fun GroupsScreen(groups: List<GroupSummary>, onGroupClick: (String) -> Unit, onAddGroup: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Groups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onAddGroup) { Text("+ Add new") }
            }
        }
        if (groups.isEmpty()) item { Text("You are not in a group yet.") }
        items(groups, key = { it.id }) { group -> GroupCard(group, detailed = true) { onGroupClick(group.id) } }
    }
}

/** Displays the selected group's quests and creation action. */
@Composable
fun GroupDetailScreen(
    group: GroupSummary?,
    onBack: () -> Unit,
    onCreateQuest: () -> Unit,
    onQuestClick: (String) -> Unit,
    challengeState: no.ntnu.prog2007.questbook.ui.challenges.GroupChallengesUiState? = null,
    onGetQuest: () -> Unit = {}
) {
    val allQuests by QuestDependencies.questRepository.quests
        .collectAsStateWithLifecycle()

    val definitions = challengeState?.snapshot?.challenges.orEmpty().filter { it.groupId == group?.id }
    val weekly = definitions.firstOrNull { it.kind == "WEEKLY" && System.currentTimeMillis() in it.startMillis until it.endMillis }
    val groupQuests = allQuests.filter { it.groupId == group?.id && !it.id.startsWith("attempt-") && challengeState?.snapshot?.attempts?.none { a -> a.attemptId == it.id } != false && it.id != weekly?.questId &&
        definitions.none { d -> d.questId == it.id && d.kind == "WEEKLY" } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("‹ Back")
            }
        }

        if (group == null) {
            item { GroupLoadingOrMissing() }
        } else {
            item {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (group.description.isNotEmpty()) {
                item {
                    Text(
                        group.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { GroupMembersSection(group) }

            if (group.inviteCode.isNotEmpty()) {
                item { InviteCard(group.inviteCode) }
            }

            item {
                Button(
                    onClick = onCreateQuest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text("+ Create quest")
                }
            }

            item {
                OutlinedButton(onClick = onGetQuest, enabled = challengeState?.busy != true && challengeState?.loading != true,
                    modifier = Modifier.fillMaxWidth()) { Text("Get quest") }
                challengeState?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            weekly?.let { goal -> item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Weekly group goal", style = MaterialTheme.typography.titleSmall)
                    Text(allQuests.find { it.id == goal.questId }?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    val value = challengeState?.snapshot?.progress?.get(goal.questId) ?: 0.0
                    Text(no.ntnu.prog2007.questbook.ui.personal.metricProgressText(
                        no.ntnu.prog2007.questbook.data.personal.ActivityMetric.valueOf(goal.metric), value, goal.target))
                    LinearProgressIndicator(progress = { (value / goal.target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { onQuestClick(goal.questId) }) { Text("View group goal") }
                } }
            } }
            item {
                Text(
                    "Group quests",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (groupQuests.isEmpty()) {
                item {
                    Text("No quests yet. Create your group's first quest.")
                }
            }

            items(groupQuests, key = { it.id }) { quest ->
                val mine = challengeState?.snapshot?.attempts?.find { it.challengeId == quest.id && it.userId == challengeState.userId }
                val completed = challengeState?.snapshot?.results?.any { it.questId == mine?.attemptId } == true
                QuestCard(if (completed) quest.copy(status = no.ntnu.prog2007.questbook.model.QuestStatus.COMPLETED) else quest) {
                    onQuestClick(quest.id)
                }
            }
        }
    }
}

/**
 * Shown while a group is not in the list yet, e.g. right after it was created or joined
 * and before Firestore has delivered it. Says "not found" only if it still is missing.
 */
@Composable
private fun GroupLoadingOrMissing() {
    var waitedLongEnough by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_000)
        waitedLongEnough = true
    }
    if (waitedLongEnough) {
        Text("Group not found", style = MaterialTheme.typography.titleLarge)
    } else {
        CircularProgressIndicator()
    }
}
