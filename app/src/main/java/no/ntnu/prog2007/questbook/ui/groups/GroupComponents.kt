package no.ntnu.prog2007.questbook.ui.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.ntnu.prog2007.questbook.R

/** Local picture placeholder until groups can upload their own images. */
@Composable
private fun GroupPicture(modifier: Modifier) {
    Image(painterResource(R.drawable.group_mountains), contentDescription = null,
        modifier = modifier, contentScale = ContentScale.Crop)
}

/** Wide group card; the overview additionally shows the group's description. */
@Composable
internal fun GroupCard(group: GroupSummary, detailed: Boolean = false, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("group_${group.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GroupPicture(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(group.members.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (detailed) {
                    Text("${group.members.size} members", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(group.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Home switches between an empty state, one wide card, and a horizontal group carousel. */
@Composable
internal fun HomeGroups(groups: List<GroupSummary>, onGroupClick: (String) -> Unit, onAddGroup: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Your groups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onAddGroup, modifier = Modifier.testTag("home_add_group")) { Text("+ Add new") }
        }
        when (groups.size) {
            0 -> Text("Join or create a group to get started.", style = MaterialTheme.typography.bodyMedium)
            1 -> GroupCard(groups.single()) { onGroupClick(groups.single().id) }
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(groups, key = { it.id }) { group ->
                    Column(Modifier.width(88.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { onGroupClick(group.id) }.padding(4.dp).testTag("group_${group.id}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupPicture(Modifier.size(64.dp).clip(CircleShape))
                        Text(group.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
