package app.drivedelta.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.ui.components.RecentTripCard
import app.drivedelta.ui.permissions.rememberStartTrackingPermissionFlow
import app.drivedelta.ui.theme.DdOnPrimary
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdSurface
import app.drivedelta.ui.theme.DdSurfaceElevated
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import app.drivedelta.ui.tracking.components.PreRideSheet
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * Dashboard (F13; design/mockups/dashboard.png): a date + greeting header with avatar, a Start Ride
 * hero card, a weekly-totals card, rich recent-drive cards (the Trip Detail entry point) and
 * personal-best routes (the Route Summary entry point).
 */
@Composable
fun DashboardScreen(
    onSignedOut: () -> Unit,
    onStartTracking: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenRouteSummary: (String) -> Unit,
    onSeeAllTrips: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val tokens = LocalDdTokens.current
    var showPreRide by remember { mutableStateOf(false) }
    val recentItems by viewModel.recentItems.collectAsStateWithLifecycle()
    val weekly by viewModel.weeklyStats.collectAsStateWithLifecycle()
    val personalBests by viewModel.personalBests.collectAsStateWithLifecycle()
    val requestPermissionsThenSheet = rememberStartTrackingPermissionFlow(onAllGranted = { showPreRide = true })

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = tokens.screenPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = tokens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
        ) {
            item { GreetingHeader(viewModel.userName) }
            item { StartRideCard(onClick = requestPermissionsThenSheet) }
            item { WeeklyCard(weekly) }

            item {
                SectionHeader(
                    stringResource(R.string.dashboard_recent_rides_title),
                    action = stringResource(R.string.dashboard_see_all).takeIf { recentItems.isNotEmpty() },
                    onAction = onSeeAllTrips,
                )
            }
            if (recentItems.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.dashboard_no_trips),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(recentItems, key = { it.tripId }) { item ->
                RecentTripCard(
                    startTime = item.startTime,
                    originName = item.originName,
                    destName = item.destName,
                    carName = item.carName,
                    fuelType = item.fuelType,
                    durationMs = item.durationMs,
                    distanceMeters = item.distanceMeters,
                    isNewPb = false,
                    deltaMs = item.deltaVsBestMs,
                    deltaSuffix = " " + stringResource(R.string.dashboard_vs_best),
                    onClick = { onOpenTrip(item.tripId) },
                )
            }

            if (personalBests.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.dashboard_personal_bests_title)) }
                items(personalBests, key = { it.routeHash }) { pb ->
                    PersonalBestCard(pb, onClick = { onOpenRouteSummary(pb.bestTripId) })
                }
            }

            item {
                Spacer(Modifier.height(tokens.spaceMd))
                OutlinedButton(onClick = { viewModel.signOut(); onSignedOut() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_sign_out))
                }
            }
        }
    }

    if (showPreRide) {
        PreRideSheet(
            onDismiss = { showPreRide = false },
            onStarted = { showPreRide = false; onStartTracking() },
        )
    }
}

@Composable
private fun GreetingHeader(name: String?) {
    val tokens = LocalDdTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (name != null) stringResource(R.string.dashboard_greeting_named, greetingWord(), name)
                else stringResource(R.string.dashboard_greeting, greetingWord()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(DdSurfaceElevated).border(1.dp, DdOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name?.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StartRideCard(onClick: () -> Unit) {
    val tokens = LocalDdTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusLg))
            .background(DdPrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spaceXl, vertical = tokens.spaceXl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.dashboard_start_ride), style = MaterialTheme.typography.headlineMedium, color = DdOnPrimary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.dashboard_start_ride_subtitle), style = MaterialTheme.typography.bodyMedium, color = DdOnPrimary.copy(alpha = 0.85f))
        }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(DdOnPrimary.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = DdOnPrimary)
        }
    }
}

@Composable
private fun WeeklyCard(stats: WeeklyStats) {
    val tokens = LocalDdTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusCard))
            .background(DdSurface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard))
            .padding(tokens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Text(stringResource(R.string.dashboard_this_week), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(String.format(Locale.US, "%.0f", stats.distanceKm), "km", stringResource(R.string.dashboard_week_distance))
            Stat(formatDriveTime(stats.driveMinutes), "h", stringResource(R.string.dashboard_week_time))
            Stat(formatMoney(stats.fuelCost), null, stringResource(R.string.dashboard_week_fuel))
        }
    }
}

@Composable
private fun Stat(value: String, unit: String?, label: String) {
    val ddType = LocalDdType.current
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = ddType.statValue, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            if (unit != null) {
                Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
private fun PersonalBestCard(pb: PersonalBest, onClick: () -> Unit) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusCard))
            .background(DdSurface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard))
            .clickable(onClick = onClick)
            .padding(tokens.spaceLg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.dashboard_route_summary, pb.distanceKm, pb.rideCount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.dashboard_best_time, formatBest(pb.bestDurationMs)),
            style = ddType.numericMono,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// --- Helpers ------------------------------------------------------------------------------------

@Composable
private fun greetingWord(): String {
    val h = LocalTime.now().hour
    return stringResource(
        when {
            h < 12 -> R.string.dashboard_greeting_morning
            h < 18 -> R.string.dashboard_greeting_afternoon
            else -> R.string.dashboard_greeting_evening
        },
    )
}

private fun formatDriveTime(minutes: Int): String = String.format(Locale.US, "%d:%02d", minutes / 60, minutes % 60)

private fun formatBest(ms: Long): String {
    val totalSec = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
}

private fun currencySymbol(): String = try {
    Currency.getInstance(Locale.getDefault()).symbol
} catch (e: Exception) {
    "€"
}

private fun formatMoney(v: Float): String = currencySymbol() + String.format(Locale.US, "%.2f", v)
