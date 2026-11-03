package no.ntnu.prog2007.questbook.ui.groups

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shows the group's invite code with buttons to copy or share it, so members can
 * invite friends. Friends type the code under Groups › + Add new › Join a group with code.
 */
@Composable
internal fun InviteCard(inviteCode: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Invite friends", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Share this code. Friends join under Groups › + Add new › Join a group with code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = inviteCode,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                modifier = Modifier.testTag("group_invite_code")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyInviteCode(context, inviteCode) }) { Text("Copy") }
                Button(onClick = { shareInviteCode(context, inviteCode) }) { Text("Share") }
            }
        }
    }
}

private fun copyInviteCode(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("QuestBook invite code", code))
    // Android 13 and newer show their own confirmation when something is copied.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Invite code copied", Toast.LENGTH_SHORT).show()
    }
}

private fun shareInviteCode(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join my group on QuestBook with the invite code $code")
    }
    context.startActivity(Intent.createChooser(send, "Share invite code"))
}
