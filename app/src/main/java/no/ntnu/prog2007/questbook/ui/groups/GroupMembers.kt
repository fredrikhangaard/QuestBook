package no.ntnu.prog2007.questbook.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** "1 member" or "N members". */
internal fun memberCountLabel(count: Int): String = if (count == 1) "1 member" else "$count members"

/**
 * Lists everyone in the group (PB-04). The signed-in user is shown first as "You",
 * and the group's owner is marked. Updates live when someone joins.
 */
@Composable
internal fun GroupMembersSection(group: GroupSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Members", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = memberCountLabel(group.members.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        group.members.forEachIndexed { index, name ->
            val isOwner = group.ownerId.isNotEmpty() && group.memberIds.getOrNull(index) == group.ownerId
            MemberRow(name = name, isOwner = isOwner, modifier = Modifier.testTag("group_member_$index"))
        }
    }
}

@Composable
private fun MemberRow(name: String, isOwner: Boolean, modifier: Modifier = Modifier) {
    // Merged, so a screen reader reads the name and "Owner" together.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (isOwner) {
            Text("Owner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupMembersSectionPreview() {
    GroupMembersSection(
        GroupSummary(
            id = "crew", name = "Mountain Crew", members = listOf("You", "Emma", "Jonas"),
            description = "", ownerId = "emma", memberIds = listOf("me", "emma", "jonas")
        )
    )
}
