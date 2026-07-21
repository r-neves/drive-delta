package app.drivedelta.ui.tripdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.Segment
import app.drivedelta.domain.model.TripDetail
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdPurpleSector
import app.drivedelta.ui.theme.DdSuccess
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val TABS = listOf(R.string.trip_tab_map, R.string.trip_tab_splits, R.string.trip_tab_replay)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    onCompare: (String) -> Unit,
    onAddFuel: (String) -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val trip = state.detail?.trip
                    if (trip != null) {
                        IconButton(onClick = { onCompare(trip.id) }) {
                            Icon(Icons.Filled.CompareArrows, contentDescription = stringResource(R.string.trip_compare))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val detail = state.detail
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                detail == null -> Text(
                    stringResource(R.string.trip_not_found),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                        TABS.forEachIndexed { i, labelRes ->
                            Tab(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                text = { Text(stringResource(labelRes)) },
                            )
                        }
                    }
                    when (selectedTab) {
                        0 -> MapTab(detail)
                        1 -> SplitsTab(detail, state, viewModel::setBaseline)
                        else -> ReplayTab(detail, state, viewModel)
                    }
                }
            }
        }
    }

    if (state.showFuelPrompt) {
        val tripId = state.detail?.trip?.id
        FuelPromptSheet(
            onAddFuel = { if (tripId != null) { viewModel.dismissFuelPrompt(); onAddFuel(tripId) } },
            onDismiss = viewModel::dismissFuelPrompt,
        )
    }
}

// --- Tab 1: Map (speed-coloured polyline) -------------------------------------------------------

@Composable
private fun MapTab(detail: TripDetail) {
    val points = detail.routePoints.map { LatLng(it.lat, it.lng) }
    if (points.isEmpty()) {
        CenteredHint(stringResource(R.string.trip_no_route))
        return
    }
    val maxSpeed = detail.routePoints.maxOf { it.speedMps }.coerceAtLeast(0.1f)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points[points.size / 2], 15f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(zoomControlsEnabled = true),
    ) {
        // Colour each hop by its speed relative to the trip max: green fast → red slow.
        for (i in 1 until detail.routePoints.size) {
            Polyline(
                points = listOf(points[i - 1], points[i]),
                color = speedColor(detail.routePoints[i].speedMps, maxSpeed),
                width = 12f,
            )
        }
        Marker(state = MarkerState(points.first()), title = stringResource(R.string.trip_start))
        Marker(state = MarkerState(points.last()), title = stringResource(R.string.trip_end))
    }
}

// --- Tab 2: Splits ------------------------------------------------------------------------------

@Composable
private fun SplitsTab(
    detail: TripDetail,
    state: TripDetailUiState,
    onBaseline: (CompareBaseline) -> Unit,
) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    val baselineMap = if (state.baseline == CompareBaseline.PREVIOUS) state.previousPerRoadKey else detail.bestPerRoadKey

    Column(Modifier.fillMaxSize().padding(tokens.screenPadding)) {
        // Summary header
        val total = detail.segments.sumOf { it.durationMs }
        val bestTotal = detail.bestPerRoadKey.values.sum()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryStat(stringResource(R.string.trip_total_time), formatTime(total))
            SummaryStat(stringResource(R.string.trip_best_total), formatTime(bestTotal))
            SummaryStat(stringResource(R.string.trip_delta), formatDelta(total - bestTotal))
        }

        Row(Modifier.padding(vertical = tokens.spaceMd), horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
            FilterChip(
                selected = state.baseline == CompareBaseline.BEST,
                onClick = { onBaseline(CompareBaseline.BEST) },
                label = { Text(stringResource(R.string.trip_vs_best)) },
            )
            FilterChip(
                selected = state.baseline == CompareBaseline.PREVIOUS,
                onClick = { onBaseline(CompareBaseline.PREVIOUS) },
                enabled = state.hasPreviousRun,
                label = { Text(stringResource(R.string.trip_vs_previous)) },
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
            itemsIndexed(detail.segments) { index, segment ->
                SegmentSplitRow(index, segment, baselineMap[segment.roadKey], detail.bestPerRoadKey[segment.roadKey])
            }
        }
    }
}

@Composable
private fun SegmentSplitRow(index: Int, segment: Segment, baselineMs: Long?, bestMs: Long?) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    val isPersonalBest = bestMs != null && segment.durationMs <= bestMs
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", style = ddType.numericMono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = tokens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(
                segment.roadName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPersonalBest) DdPurpleSector else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.trip_segment_dist, segment.distanceMeters.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(segment.durationMs), style = ddType.numericMono, color = MaterialTheme.colorScheme.onSurface)
            val delta = baselineMs?.let { segment.durationMs - it }
            if (delta != null) {
                val faster = delta < 0
                Text(
                    (if (faster) "▾ −" else "▴ +") + formatTime(kotlin.math.abs(delta)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (faster) DdDeltaFaster else DdError,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// --- Tab 3: Replay ------------------------------------------------------------------------------

@Composable
private fun ReplayTab(detail: TripDetail, state: TripDetailUiState, viewModel: TripDetailViewModel) {
    val tokens = LocalDdTokens.current
    val points = detail.routePoints
    if (points.isEmpty()) {
        CenteredHint(stringResource(R.string.trip_no_route))
        return
    }
    val idx = (state.replayFraction * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
    val current = points[idx]
    val latLng = LatLng(current.lat, current.lng)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLng, 16f)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
            ) {
                Polyline(points = points.map { LatLng(it.lat, it.lng) }, color = MaterialTheme.colorScheme.primary, width = 8f)
                Marker(state = MarkerState(latLng))
            }
        }
        Column(Modifier.padding(tokens.screenPadding)) {
            Text(
                stringResource(R.string.trip_replay_speed, (current.speedMps * 3.6f).roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.material3.Slider(
                value = state.replayFraction,
                onValueChange = viewModel::setReplayFraction,
                valueRange = 0f..1f,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::togglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.trip_play_pause),
                    )
                }
                TextButton(onClick = viewModel::cycleSpeed) {
                    Text(stringResource(R.string.trip_replay_multiplier, state.replaySpeed))
                }
            }
        }
    }
}

// --- Fuel prompt --------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelPromptSheet(onAddFuel: () -> Unit, onDismiss: () -> Unit) {
    val tokens = LocalDdTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding).padding(bottom = tokens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
        ) {
            Text(stringResource(R.string.trip_fuel_prompt_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.trip_fuel_prompt_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.trip_fuel_skip)) }
                TextButton(onClick = onAddFuel) { Text(stringResource(R.string.trip_fuel_add)) }
            }
        }
    }
}

// --- Shared helpers -----------------------------------------------------------------------------

@Composable
private fun SummaryStat(label: String, value: String) {
    val ddType = LocalDdType.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = ddType.statValue, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Linear green(fast)→red(slow) blend by speed relative to the trip max. */
private fun speedColor(speedMps: Float, maxSpeedMps: Float): Color {
    val t = (speedMps / maxSpeedMps).coerceIn(0f, 1f)
    return lerp(DdError, DdSuccess, t)
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val tenths = (ms % 1000) / 100
    return String.format(Locale.US, "%d:%02d.%d", m, s, tenths)
}

private fun formatDelta(ms: Long): String = (if (ms < 0) "−" else "+") + formatTime(kotlin.math.abs(ms))
