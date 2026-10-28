package no.ntnu.prog2007.questbook.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileScreen() = PlaceholderPage("Profile", "Your profile and settings will appear here.")

@Composable
private fun PlaceholderPage(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}
