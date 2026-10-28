package no.ntnu.prog2007.questbook.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
            Text("Sample groups", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (groups.isEmpty()) item { Text("You are not in a group yet.") }
        items(groups, key = { it.id }) { group -> GroupCard(group, detailed = true) { onGroupClick(group.id) } }
    }
}

/** Group landing page placeholder. Both Home and Groups open the same destination. */
@Composable
fun GroupDetailScreen(group: GroupSummary?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(group?.name ?: "Group not found", style = MaterialTheme.typography.titleLarge)
        if (group != null) Text("Your group page will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
