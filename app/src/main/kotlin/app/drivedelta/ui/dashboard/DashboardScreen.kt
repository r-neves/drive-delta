package app.drivedelta.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.drivedelta.R
import app.drivedelta.ui.permissions.rememberStartTrackingPermissionFlow
import app.drivedelta.ui.tracking.components.PreRideSheet

/**
 * Dashboard. For now a "Start Ride" FAB that (after the tracking permission chain) opens the pre-ride
 * sheet; starting a ride navigates to the Live Tracking screen via [onStartTracking]. Recent trips,
 * personal bests, and weekly stats (F13) arrive in Checkpoint 9.
 */
@Composable
fun DashboardScreen(
    onSignedOut: () -> Unit,
    onStartTracking: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    var showPreRide by remember { mutableStateOf(false) }
    val requestPermissionsThenSheet = rememberStartTrackingPermissionFlow(
        onAllGranted = { showPreRide = true },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = requestPermissionsThenSheet,
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                text = { Text(stringResource(R.string.dashboard_start_ride)) },
            )
        },
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

    if (showPreRide) {
        PreRideSheet(
            onDismiss = { showPreRide = false },
            onStarted = {
                showPreRide = false
                onStartTracking()
            },
        )
    }
}
