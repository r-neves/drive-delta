package app.drivedelta.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.ui.permissions.rememberStartTrackingPermissionFlow

/**
 * Checkpoint 1 stub, plus a Checkpoint 5 tracking test harness. The Start/Stop test-trip controls
 * exercise the GPS foreground service before the Live Tracking screen exists (Checkpoint 6); they
 * are removed once the real Start Ride flow lands. The full dashboard (F13) arrives in Checkpoint 9.
 */
@Composable
fun DashboardScreen(
    onSignedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val summary by viewModel.lastTripSummary.collectAsStateWithLifecycle()

    val requestPermissionsThenStart = rememberStartTrackingPermissionFlow(
        onAllGranted = { viewModel.startTestTrip() },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(24.dp))

            if (isTracking) {
                Text(
                    text = stringResource(R.string.tracking_recording),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::stopTestTrip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tracking_stop_test))
                }
            } else {
                Button(
                    onClick = requestPermissionsThenStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tracking_start_test))
                }
            }

            summary?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.tracking_summary,
                        it.pointCount,
                        it.interpolatedCount,
                        it.distanceKm,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onSignedOut()
                },
            ) {
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }
}
