package app.drivedelta.ui.tripdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.Segment
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.model.TripDetail
import app.drivedelta.domain.usecase.fuel.TripCostChart
import app.drivedelta.domain.usecase.fuel.TripCostPoint
import app.drivedelta.ui.components.ScatterKind
import app.drivedelta.ui.components.ScatterPoint
import app.drivedelta.ui.components.SpeedCostScatter
import app.drivedelta.ui.components.routeTitle
import app.drivedelta.ui.fuel.EnergyLogSheet
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdPurpleRowBg
import app.drivedelta.ui.theme.DdPurpleRowBorder
import app.drivedelta.ui.theme.DdPurpleRowMuted
import app.drivedelta.ui.theme.DdPurpleRowText
import app.drivedelta.ui.theme.DdPurpleSector
import app.drivedelta.ui.theme.DdSuccess
import app.drivedelta.ui.theme.DdTextTertiary
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TABS = listOf(R.string.trip_tab_map, R.string.trip_tab_splits, R.string.trip_tab_replay, R.string.trip_tab_cost)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    onCompare: (String) -> Unit,
    onRouteSummary: (String) -> Unit,
    onOpenEnergyPrices: () -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Splits is the designed default tab (design/tokens.md §7).
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            routeTitle(state.originName, state.destName, stringResource(R.string.trip_detail_title)),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        state.detail?.trip?.let { trip ->
                            Text(
                                tripSubtitle(trip, state.carName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { CircleIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                } },
                actions = {
                    val trip = state.detail?.trip
                    if (trip != null) OverflowMenu(onInsights = { onRouteSummary(trip.id) }, onCompare = { onCompare(trip.id) })
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
                    SummaryHeader(detail, state.costChart)
                    if (state.costChart?.loggedCost == null && detail.trip.carId != null) {
                        FuelNotLoggedBanner(onAdd = viewModel::openEnergyLog)
                    }
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.background,
                        indicator = { positions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(positions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    ) {
                        TABS.forEachIndexed { i, labelRes ->
                            Tab(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = DdTextTertiary,
                                text = {
                                    Text(
                                        stringResource(labelRes),
                                        fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }
                    when (selectedTab) {
                        0 -> MapTab(detail)
                        1 -> SplitsTab(detail, state, viewModel::setBaseline)
                        2 -> ReplayTab(detail, state, viewModel)
                        else -> CostTab(state.costChart)
                    }
                }
            }
        }
    }

    val tripId = state.detail?.trip?.id
    if (state.showEnergyLog && tripId != null) {
        EnergyLogSheet(
            tripId = tripId,
            onDismiss = viewModel::dismissEnergyLog,
            onSaved = viewModel::onEnergyLogged,
            onOpenPrices = onOpenEnergyPrices,
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
    val baselineMap = if (state.baseline == CompareBaseline.PREVIOUS) state.previousPerRoadKey else detail.bestPerRoadKey

    Column(Modifier.fillMaxSize()) {
        // vs-best / vs-previous baseline toggle (functional; not in the mockup, kept compact).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding, vertical = tokens.spaceSm),
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm),
        ) {
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

        // Column header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding, vertical = tokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.trip_col_segment), style = MaterialTheme.typography.labelSmall, color = DdTextTertiary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.trip_col_time), style = MaterialTheme.typography.labelSmall, color = DdTextTertiary, modifier = Modifier.width(84.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(stringResource(R.string.trip_col_delta), style = MaterialTheme.typography.labelSmall, color = DdTextTertiary, modifier = Modifier.width(96.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }

        LazyColumn {
            itemsIndexed(detail.segments) { index, segment ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SegmentSplitRow(segment, baselineMap[segment.roadKey], detail.bestPerRoadKey[segment.roadKey])
            }
        }
    }
}

@Composable
private fun SegmentSplitRow(segment: Segment, baselineMs: Long?, bestMs: Long?) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    val isPersonalBest = bestMs != null && segment.durationMs <= bestMs
    val timeStyle = ddType.numericMono.copy(fontSize = 22.sp)

    val rowModifier = if (isPersonalBest) {
        Modifier
            .fillMaxWidth()
            .background(DdPurpleRowBg)
            .border(1.dp, DdPurpleRowBorder)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier.padding(horizontal = tokens.screenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                segment.roadName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPersonalBest) DdPurpleRowText else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.trip_seg_dist_km, segment.distanceMeters / 1000f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = if (isPersonalBest) DdPurpleRowMuted else DdTextTertiary,
            )
        }
        Text(
            formatTime(segment.durationMs),
            style = timeStyle,
            color = if (isPersonalBest) DdPurpleRowText else MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(84.dp),
        )
        Column(Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
            if (isPersonalBest) {
                Text("★ ${stringResource(R.string.trip_pb)}", style = ddType.deltaValue.copy(fontSize = 18.sp), color = DdPurpleSector, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.trip_new_best), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = DdPurpleRowMuted)
            } else {
                val delta = baselineMs?.let { segment.durationMs - it }
                if (delta != null) {
                    val faster = delta < 0
                    Text(
                        (if (faster) "▾" else "▴") + formatDeltaSeconds(abs(delta)),
                        style = ddType.deltaValue.copy(fontSize = 18.sp),
                        color = if (faster) DdDeltaFaster else DdError,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (bestMs != null) {
                    Text(
                        stringResource(R.string.trip_best_caption, formatTime(bestMs)),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = DdTextTertiary,
                    )
                }
            }
        }
    }
}

// --- Summary header + app-bar helpers -----------------------------------------------------------

@Composable
private fun SummaryHeader(detail: TripDetail, costChart: TripCostChart?) {
    val tokens = LocalDdTokens.current
    val trip = detail.trip
    val avgKph = if (trip.durationMs > 0) (trip.distanceMeters / (trip.durationMs / 1000f) * 3.6f).roundToInt() else 0
    val total = detail.segments.sumOf { it.durationMs }
    val bestTotal = detail.bestPerRoadKey.values.sum()
    val deltaVsBest = if (bestTotal > 0) total - bestTotal else null
    val loggedCost = costChart?.loggedCost

    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding, vertical = tokens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        HeaderStat(formatClockShort(trip.durationMs), stringResource(R.string.trip_hdr_duration))
        HeaderStat(String.format(Locale.US, "%.1f", trip.distanceMeters / 1000f), stringResource(R.string.trip_hdr_km))
        HeaderStat(avgKph.toString(), stringResource(R.string.trip_hdr_avg))
        HeaderStat(
            loggedCost?.let { formatMoney(it, costChart.currencyCode) } ?: "—",
            stringResource(R.string.dashboard_week_fuel),
        )
        if (deltaVsBest != null) {
            val faster = deltaVsBest <= 0
            HeaderStat(
                (if (faster) "▾" else "▴") + formatClockShort(abs(deltaVsBest)),
                stringResource(R.string.trip_hdr_vs_best),
                valueColor = if (faster) DdDeltaFaster else DdError,
            )
        }
    }
}

/** Dashed "Fuel not logged → Add" banner (design/mockups/Energy Logging-saved-drive-not-logged.png). */
@Composable
private fun FuelNotLoggedBanner(onAdd: () -> Unit) {
    val tokens = LocalDdTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.screenPadding)
            .dashedRoundedBorder(DdPrimary.copy(alpha = 0.6f), tokens.radiusCard)
            .padding(tokens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(tokens.radiusSm)).background(DdPrimary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = DdPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.trip_fuel_not_logged), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.trip_fuel_not_logged_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Button(onClick = onAdd, shape = RoundedCornerShape(tokens.radiusMd)) {
            Text(stringResource(R.string.trip_fuel_add_short))
        }
    }
}

// --- Tab 4: Cost (speed vs. cost scatter) -------------------------------------------------------

@Composable
private fun CostTab(costChart: TripCostChart?) {
    val tokens = LocalDdTokens.current
    if (costChart == null || costChart.points.isEmpty()) {
        CenteredHint(stringResource(R.string.trip_cost_empty))
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.trip_cost_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.route_summary_drive_count, costChart.driveCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val symbol = currencySymbol(costChart.currencyCode)
        SpeedCostScatter(
            points = costChart.points.map { it.toScatterPoint() },
            currencySymbol = symbol,
            pendingLabel = stringResource(R.string.trip_cost_no_cost_yet),
        )
        Text(
            stringResource(
                if (costChart.loggedCost == null) R.string.trip_cost_caption_pending else R.string.trip_cost_caption_logged,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun TripCostPoint.toScatterPoint(): ScatterPoint = ScatterPoint(
    speedKph = speedKph,
    cost = cost,
    kind = when {
        isThisDrive && cost == null -> ScatterKind.THIS_PENDING
        isThisDrive -> ScatterKind.THIS_DRIVE
        isFastest -> ScatterKind.FASTEST
        isCheapest -> ScatterKind.CHEAPEST
        isEstimated -> ScatterKind.ESTIMATED
        else -> ScatterKind.NORMAL
    },
)

@Composable
private fun HeaderStat(value: String, label: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowMenu(onInsights: () -> Unit, onCompare: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    CircleIconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.trip_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.trip_menu_insights)) },
            leadingIcon = { Icon(Icons.Outlined.Insights, contentDescription = null) },
            onClick = { expanded = false; onInsights() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.trip_compare)) },
            leadingIcon = { Icon(Icons.Outlined.CompareArrows, contentDescription = null) },
            onClick = { expanded = false; onCompare() },
        )
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

// --- Shared helpers -----------------------------------------------------------------------------

/** A dashed rounded-rectangle border, drawn behind content (the "Fuel not logged" banner). */
private fun Modifier.dashedRoundedBorder(color: Color, radiusDp: androidx.compose.ui.unit.Dp): Modifier =
    this.drawBehind {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        )
        drawRoundRect(
            color = color,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusDp.toPx()),
            style = stroke,
        )
    }

private fun currencySymbol(code: String): String = try {
    java.util.Currency.getInstance(code).symbol
} catch (e: Exception) {
    "€"
}

private fun formatMoney(v: Float, code: String): String =
    currencySymbol(code) + String.format(Locale.US, "%.2f", v)

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

/** m:ss with no tenths — for the summary header (Duration, vs best). */
private fun formatClockShort(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

private fun formatDeltaSeconds(ms: Long): String = String.format(Locale.US, "%.1f", ms / 1000f)

/** "Today · 18:24 · Model 3" — date · time · car (car omitted when null). */
@Composable
private fun tripSubtitle(trip: Trip, carName: String?): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(trip.startTime).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (start.toLocalDate()) {
        today -> stringResource(R.string.trips_day_today)
        today.minusDays(1) -> stringResource(R.string.trips_day_yesterday)
        else -> start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
    val time = start.format(DateTimeFormatter.ofPattern("HH:mm"))
    return listOfNotNull(day, time, carName).joinToString(" · ")
}
