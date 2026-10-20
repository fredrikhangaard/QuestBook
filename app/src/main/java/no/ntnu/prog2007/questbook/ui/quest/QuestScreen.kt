package no.ntnu.prog2007.questbook.ui.quest

import android.net.http.HeaderBlock
import android.provider.ContactsContract
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
@Composable
fun Title() {

}
@Composable
fun QuestScreen() {
    Column (
        modifier = Modifier.padding(vertical = 60.dp, horizontal = 16.dp)
    ) {
        //Title screen for the page
        Text(
            text = "Quest Overview",
            style = MaterialTheme.typography.headlineLarge
        )

        //green line under "Quest Overview"
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 4.dp,
            color = Color.hsv(hue = 75f, saturation = 0.4f, value = 0.6f)
        )
        //Hard coded credentials for simulation of quest screen

        Text("plass Waterfall Run")
        Text("Run at least 5 km")
        Text("Distance: 0.0 / 5.0 km")

        Button(
            onClick = {}
        ) {
            Text("Start Quest")
        }
    }
}