package no.ntnu.prog2007.questbook.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import no.ntnu.prog2007.questbook.R

internal data class BottomDestination(val route: String, val label: String, @param:DrawableRes val icon: Int)

internal val bottomDestinations = listOf(
    BottomDestination("today", "Home", R.drawable.ic_nav_home),
    BottomDestination("groups", "Groups", R.drawable.ic_nav_groups),
    BottomDestination("quests", "Quests", R.drawable.ic_nav_quests),
    BottomDestination("questbook", "Memories", R.drawable.ic_nav_memories),
    BottomDestination("profile", "Profile", R.drawable.ic_nav_profile)
)

/** Five labelled tabs matching the wireframe; selection uses green instead of a filled pill. */
@Composable
internal fun QuestBookBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            bottomDestinations.forEach { destination ->
                NavigationBarItem(
                    modifier = Modifier.testTag("tab_${destination.route}"),
                    selected = currentRoute == destination.route,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent
                    ),
                    onClick = { onNavigate(destination.route) },
                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                    label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
