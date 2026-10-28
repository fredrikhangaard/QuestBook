package no.ntnu.prog2007.questbook.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.ntnu.prog2007.questbook.R

/** Create-or-join chooser shared by the Home shortcut and Groups overview. */
@Composable
fun AddGroupScreen(onBack: () -> Unit, onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("‹ Back") }
        Text("Groups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(40.dp))
        Icon(painterResource(R.drawable.ic_nav_groups), contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
        Text("Adventures are better together.", style = MaterialTheme.typography.titleMedium)
        Text("Create a new group or join your friends with a code.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(10.dp)) { Text("Create a new group") }
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(10.dp)) { Text("Join a group with code") }
    }
}

/** Navigation placeholder until create/join services are implemented by the group feature. */
@Composable
fun GroupSetupPlaceholder(isJoining: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(if (isJoining) "Join group" else "Create group", style = MaterialTheme.typography.titleLarge)
        Text(if (isJoining) "Joining a group with a code will be available here."
            else "Group name, picture and setup will be available here.")
    }
}
