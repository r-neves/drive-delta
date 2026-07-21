package app.drivedelta.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.Trip
import app.drivedelta.ui.permissions.rememberStartTrackingPermissionFlow
import app.drivedelta.ui.tracking.components.PreRideSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dashboard. For now a "Start Ride" FAB that (after the tracking permission chain) opens the pre-ride
 * sheet; starting a ride navigates to the Live Tracking screen via [onStartTracking]. Recent trips,
 * personal bests, and weekly stats (F13) arrive in Checkpoint 9.
 */
@Composable
fun DashboardScreen(
    onSignedOut: () -> Unit,
    onStartTracking: () -> Unit,
    onOpenTrip: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    var showPreRide by remember { mutableStateOf(false) }
    val recentTrips by viewModel.recentTrips.collectAsStateWithLifecycle()
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dashboard_recent_rides),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (recentTrips.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_no_trips),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recentTrips) { trip -> TripCard(trip, onClick = { onOpenTrip(trip.id) }) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onSignedOut()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
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

private val TRIP_DATE_FORMAT = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = TRIP_DATE_FORMAT.format(Date(trip.startTime)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            val km = trip.distanceMeters / 1000f
            val mins = (trip.durationMs / 60000).toInt()
            Text(
                text = stringResource(R.string.dashboard_trip_summary, km, mins),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
