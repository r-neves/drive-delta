package app.drivedelta.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/**
 * Dashboard (F13): weekly summary, personal bests (most-driven routes + best times), and recent
 * rides (the Trip Detail entry point). "Start Ride" opens the pre-ride sheet after the permission
 * chain and navigates to Live Tracking.
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
    val weekly by viewModel.weeklyStats.collectAsStateWithLifecycle()
    val personalBests by viewModel.personalBests.collectAsStateWithLifecycle()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            item { SectionLabel(stringResource(R.string.dashboard_this_week)) }
            item { WeeklyCard(weekly) }

            if (personalBests.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.dashboard_personal_bests)) }
                items(personalBests, key = { it.routeHash }) { pb -> PersonalBestCard(pb) }
            }

            item { SectionLabel(stringResource(R.string.dashboard_recent_rides)) }
            if (recentTrips.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_no_trips),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(recentTrips, key = { it.id }) { trip -> TripCard(trip, onClick = { onOpenTrip(trip.id) }) }

            item {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.signOut()
                        onSignedOut()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_sign_out))
                }
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun WeeklyCard(stats: WeeklyStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat(stringResource(R.string.dashboard_week_distance), String.format(Locale.US, "%.1f km", stats.distanceKm))
            Stat(stringResource(R.string.dashboard_week_time), formatDuration(stats.driveMinutes))
            Stat(stringResource(R.string.dashboard_week_fuel), String.format(Locale.US, "€%.0f", stats.fuelCost))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonalBestCard(pb: PersonalBest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_route_summary, pb.distanceKm, pb.rideCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.dashboard_best_time, formatBest(pb.bestDurationMs)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
            Text(
                text = stringResource(
                    R.string.dashboard_trip_summary,
                    trip.distanceMeters / 1000f,
                    (trip.durationMs / 60000).toInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m) else String.format(Locale.US, "%dm", m)
}

private fun formatBest(ms: Long): String {
    val totalSec = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
}
