
package no.ntnu.prog2007.questbook.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GpsTestScreen() {

    val context = LocalContext.current

    val viewModel: GpsTrackingViewModel = viewModel(
        factory = remember(context.applicationContext) {
            GpsTrackingViewModel.Factory(context.applicationContext)
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Request Android runtime permissions
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val fineGranted =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

            if (fineGranted) {
                viewModel.startTracking()
            } else {
                viewModel.permissionDenied()
            }
        }

    // Stop GPS when the screen leaves composition
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.stopTracking()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "GPS Tracking Test",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Status: ${state.status}")

        HorizontalDivider()

        // Distance information
        Text(
            text = String.format(
                Locale.US,
                "Distance: %.2f km",
                state.distanceMeters / 1000.0
            ),
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = String.format(
                Locale.US,
                "Target: %.2f km",
                state.requiredDistanceMeters / 1000.0
            )
        )

        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = String.format(
                Locale.US,
                "Progress: %.1f%%",
                state.progress * 100f
            )
        )

        if (state.distanceCompleted) {
            Text(
                text = "Distance requirement completed!",
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text("Keep moving to reach your target!")
        }

        HorizontalDivider()

        // GPS counters
        Text("GPS points received: ${state.pointCount}")
        Text("Route points saved: ${state.routePoints.size}")

        HorizontalDivider()

        // Start tracking
        Button(
            onClick = {

                val hasFinePermission =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                if (hasFinePermission) {
                    viewModel.startTracking()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            enabled = !state.isTracking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start GPS Tracking")
        }

        // Stop tracking
        OutlinedButton(
            onClick = {
                viewModel.stopTracking()
            },
            enabled = state.isTracking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop GPS Tracking")
        }

        HorizontalDivider()

        // Current location details
        Text(
            text = "Current GPS Location",
            style = MaterialTheme.typography.titleMedium
        )

        if (state.hasLocation) {
            Text(
                String.format(
                    Locale.US,
                    "Latitude: %.6f",
                    state.latitude
                )
            )

            Text(
                String.format(
                    Locale.US,
                    "Longitude: %.6f",
                    state.longitude
                )
            )

            Text(
                String.format(
                    Locale.US,
                    "Accuracy: %.1f meters",
                    state.accuracy
                )
            )
        } else {
            Text("No GPS location received yet")
        }

        HorizontalDivider()

        Text(
            text = "Recorded Route",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "${state.routePoints.size} accepted GPS points"
        )

        if (state.routePoints.isNotEmpty()) {
            val first = state.routePoints.first()
            val last = state.routePoints.last()

            Text(
                text = String.format(
                    Locale.US,
                    "Start: %.5f, %.5f",
                    first.latitude,
                    first.longitude
                )
            )

            Text(
                text = String.format(
                    Locale.US,
                    "Latest: %.5f, %.5f",
                    last.latitude,
                    last.longitude
                )
            )
        }
    }
}
