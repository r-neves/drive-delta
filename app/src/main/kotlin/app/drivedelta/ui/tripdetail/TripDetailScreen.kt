package app.drivedelta.ui.tripdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.MapProperties
import app.drivedelta.ui.theme.DdAmber
import app.drivedelta.ui.theme.DdBackground
import app.drivedelta.ui.theme.DdSurfaceElevated
import app.drivedelta.ui.theme.DdSurfaceSheet
import app.drivedelta.ui.theme.DdTextBright
import app.drivedelta.ui.theme.DdTextDim
import app.drivedelta.ui.theme.DdTextSecondary
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
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

private val TABS = listOf(R.string.trip_tab_map, R.string.trip_tab_splits, R.string.trip_tab_segments, R.string.trip_tab_cost)

/** How far a summary stat's value may shrink to fit its column, and in what steps. */
private const val MIN_STAT_VALUE_SP = 15
private const val STAT_SHRINK_STEP = 0.92f

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
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                    if (trip != null) OverflowMenu(
                        onInsights = { onRouteSummary(trip.id) },
                        onCompare = { onCompare(trip.id) },
                        onRecalculate = viewModel::recalculateSegments,
                        onDelete = { showDeleteConfirm = true },
                    )
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
                        2 -> SegmentsTab(detail, state, viewModel)
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteTrip(onDeleted = onBack)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
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
        // The dark map from design/tokens.md §2.1: the default Google styling washes out the
        // speed colouring this tab exists to show.
        properties = MapProperties(mapStyleOptions = rememberDarkMapStyle()),
        uiSettings = MapUiSettings(zoomControlsEnabled = true),
    ) {
        // Colour by speed relative to the trip max: green fast → red slow. One Polyline per *run* of
        // similar speed, not per hop: a hop-by-hop draw meant 5,162 Polyline objects on the 173 km
        // reference drive, which ran the Maps renderer out of heap and took the whole app down with
        // an OutOfMemoryError. Quantising the gradient to a few dozen steps collapses that to tens of
        // objects and is indistinguishable at any zoom the map will show.
        val runs = remember(detail) { speedRuns(detail.routePoints, maxSpeed) }
        runs.forEach { run ->
            Polyline(
                points = run.indices.map { points[it] },
                color = speedColor(run.speedMps, maxSpeed),
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

    // Opening a drive straight after it finishes beats PostRideWorker to the segments, so say so
    // rather than showing a bare empty table.
    if (detail.segments.isEmpty()) {
        CenteredHint(
            stringResource(
                if (state.processing) R.string.trip_splits_processing else R.string.trip_no_segments,
            ),
        )
        return
    }

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

    // design/mockups/trip-detail.png shows four stats: Duration · km · avg km/h · vs best. Fuel cost
    // was added later, and a fifth unweighted column overflowed the row — "vs best" wrapped onto two
    // lines and dragged its label out of alignment. Only show cost once it's actually logged; until
    // then FuelNotLoggedBanner already covers that state, so the common case is the designed four.
    val stats = buildList {
        add(HeaderStatData(formatClockShort(trip.durationMs), stringResource(R.string.trip_hdr_duration)))
        add(HeaderStatData(String.format(Locale.US, "%.1f", trip.distanceMeters / 1000f), stringResource(R.string.trip_hdr_km)))
        add(HeaderStatData(avgKph.toString(), stringResource(R.string.trip_hdr_avg)))
        if (loggedCost != null && costChart != null) {
            add(HeaderStatData(formatMoney(loggedCost, costChart.currencyCode), stringResource(R.string.dashboard_week_fuel)))
        }
        if (deltaVsBest != null) {
            val faster = deltaVsBest <= 0
            add(
                HeaderStatData(
                    value = (if (faster) "▾" else "▴") + formatClockShort(abs(deltaVsBest)),
                    label = stringResource(R.string.trip_hdr_vs_best),
                    valueColor = if (faster) DdDeltaFaster else DdError,
                ),
            )
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding, vertical = tokens.spaceMd),
        // A gutter wide enough that two numbers still read as two numbers when both fill their
        // column — 8dp between a bold 24sp "1:29:50" and a "173.1" reads as one long number.
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        stats.forEach { stat ->
            HeaderStat(
                value = stat.value,
                label = stat.label,
                valueColor = stat.valueColor ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One Trip Detail summary stat. Held in a list so the row can size itself to the stat count. */
private data class HeaderStatData(
    val value: String,
    val label: String,
    val valueColor: Color? = null,
)

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
private fun HeaderStat(
    value: String,
    label: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // Never wrap: a wrapped value pushes its own label down and breaks the row's baseline. But a
        // value can be wider than its share of the row — a 1:29:50 duration is three times the width
        // of a "116" average, and every column gets the same weight — and left to overflow it paints
        // over its neighbour: a 1 h 30 drive rendered "1:29:50173.1". Shrink to fit instead, one
        // step at a time, down to a floor past which clipping beats unreadable. Only the value that
        // needs it shrinks, so a short drive keeps the designed 24sp headline.
        val base = MaterialTheme.typography.headlineMedium
        var style by remember(value, base) { mutableStateOf(base) }
        Text(
            value,
            style = style,
            color = valueColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { layout ->
                if (layout.hasVisualOverflow && style.fontSize > MIN_STAT_VALUE_SP.sp) {
                    style = style.copy(fontSize = style.fontSize * STAT_SHRINK_STEP)
                }
            },
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun OverflowMenu(
    onInsights: () -> Unit,
    onCompare: () -> Unit,
    onRecalculate: () -> Unit,
    onDelete: () -> Unit,
) {
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
        DropdownMenuItem(
            text = { Text(stringResource(R.string.trip_menu_recalculate)) },
            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
            onClick = { expanded = false; onRecalculate() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.trip_menu_delete), color = DdError) },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = DdError) },
            onClick = { expanded = false; onDelete() },
        )
    }
}

// --- Tab 3: Segments ----------------------------------------------------------------------------

/**
 * Steps through the drive one stretch of road at a time (`design/segments-tab/Main.dc.html`).
 *
 * This replaced a Replay scrubber that slid a marker along the whole trace: pretty, but it answered
 * no question a driver asks. Stepping segment by segment does — each stop is a named road with a
 * time, a delta and a place on the map. It only became worth building once segments were worth
 * looking at: the same 173 km drive used to be 853 segments averaging six seconds, and a stepper
 * through those would have been worse than the replay it replaces.
 */
@Composable
private fun SegmentsTab(detail: TripDetail, state: TripDetailUiState, viewModel: TripDetailViewModel) {
    if (detail.segments.isEmpty()) {
        CenteredHint(
            stringResource(
                if (state.processing) R.string.trip_splits_processing else R.string.trip_no_segments,
            ),
        )
        return
    }

    val selected = state.selectedSegment.coerceIn(0, detail.segments.lastIndex)
    val shapes = remember(detail) { segmentShapes(detail) }
    val bands = remember(detail) { speedBands(detail.segments) }
    val records = remember(detail) {
        detail.segments.map { s -> detail.bestPerRoadKey[s.roadKey]?.let { s.durationMs <= it } == true }
    }
    val segment = detail.segments[selected]
    val bestMs = detail.bestPerRoadKey[segment.roadKey]
    val isPersonalBest = records[selected]
    val accent = if (isPersonalBest) DdPurpleSector else bands[selected].color

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(shapes[selected].first(), 14f)
    }
    // Follow the selection rather than the drive: framing the lit stretch is the whole point of
    // stepping. Bounds can be rejected before the map has been measured, hence the guard.
    LaunchedEffect(selected, shapes) {
        val bounds = LatLngBounds.builder().apply { shapes[selected].forEach { include(it) } }.build()
        runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 140), 600) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = MapProperties(mapStyleOptions = rememberDarkMapStyle()),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            ) {
                // The whole route stays visible so the lit stretch reads in context, dimmed to its
                // speed band — which doubles as the shape of the drive.
                shapes.forEachIndexed { index, path ->
                    if (index != selected) {
                        Polyline(points = path, color = bands[index].color.copy(alpha = 0.30f), width = 10f)
                    }
                }
                Polyline(points = shapes[selected], color = DdBackground, width = 22f)
                Polyline(points = shapes[selected], color = accent, width = 13f)
                Circle(
                    center = shapes[selected].first(),
                    radius = 25.0,
                    fillColor = DdBackground,
                    strokeColor = accent,
                    strokeWidth = 6f,
                )
                Circle(
                    center = shapes[selected].last(),
                    radius = 20.0,
                    fillColor = accent,
                    strokeColor = accent,
                    strokeWidth = 2f,
                )
            }
            SpeedBandLegend(Modifier.align(Alignment.BottomStart))
            OrdinalChip(selected + 1, detail.segments.size, Modifier.align(Alignment.TopEnd))
        }
        SegmentPanel(
            segment = segment,
            band = bands[selected],
            isPersonalBest = isPersonalBest,
            bestMs = bestMs,
            deltaMs = (if (state.baseline == CompareBaseline.PREVIOUS) {
                state.previousPerRoadKey
            } else {
                detail.bestPerRoadKey
            })[segment.roadKey]?.let { segment.durationMs - it },
            segments = detail.segments,
            bands = bands,
            records = records,
            selected = selected,
            onSelect = viewModel::selectSegment,
            onStep = viewModel::stepSegment,
        )
    }
}

/** The detail card and stepper below the map. */
@Composable
private fun SegmentPanel(
    segment: Segment,
    band: SpeedBand,
    isPersonalBest: Boolean,
    bestMs: Long?,
    deltaMs: Long?,
    segments: List<Segment>,
    bands: List<SpeedBand>,
    records: List<Boolean>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onStep: (Int) -> Unit,
) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    val accent = if (isPersonalBest) DdPurpleSector else band.color
    val ink = if (isPersonalBest) DdPurpleRowText else MaterialTheme.colorScheme.onSurface
    val muted = if (isPersonalBest) DdPurpleRowMuted else DdTextDim

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(DdSurfaceSheet)
            .border(
                width = 1.dp,
                color = if (isPersonalBest) DdPurpleRowBorder else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            )
            .padding(horizontal = tokens.screenPadding, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Text(
                        stringResource(if (isPersonalBest) R.string.trip_band_purple else band.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = DdTextSecondary,
                    )
                }
                Text(
                    segment.roadName,
                    style = MaterialTheme.typography.titleLarge,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(
                        R.string.trip_seg_detail,
                        segment.distanceMeters / 1000f,
                        (segment.avgSpeedMps * 3.6f).roundToInt(),
                        (segment.maxSpeedMps * 3.6f).roundToInt(),
                    ),
                    style = ddType.numericMono.copy(fontSize = 11.sp),
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = tokens.spaceMd)) {
                Text(formatTime(segment.durationMs), style = MaterialTheme.typography.displayMedium, color = ink)
                if (isPersonalBest) {
                    Text(
                        "★ ${stringResource(R.string.trip_pb)}",
                        style = ddType.deltaValue.copy(fontSize = 15.sp),
                        color = DdPurpleSector,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        stringResource(R.string.trip_new_best),
                        style = ddType.numericMono.copy(fontSize = 10.sp),
                        color = muted,
                    )
                } else {
                    if (deltaMs != null) {
                        val faster = deltaMs < 0
                        Text(
                            (if (faster) "▾" else "▴") + formatDeltaSeconds(abs(deltaMs)),
                            style = ddType.deltaValue.copy(fontSize = 15.sp),
                            color = if (faster) DdDeltaFaster else DdError,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (bestMs != null) {
                        Text(
                            stringResource(R.string.trip_best_caption, formatTime(bestMs)),
                            style = ddType.numericMono.copy(fontSize = 10.sp),
                            color = muted,
                        )
                    }
                }
            }
        }

        SegmentRail(
            segments = segments,
            bands = bands,
            records = records,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(26.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
        ) {
            StepButton(
                icon = Icons.Filled.ChevronLeft,
                enabled = selected > 0,
                contentDescription = stringResource(R.string.trip_seg_prev),
                onClick = { onStep(-1) },
            )
            Text(
                when (selected) {
                    0 -> stringResource(R.string.trip_seg_start)
                    segments.lastIndex -> stringResource(R.string.trip_seg_end)
                    else -> stringResource(R.string.trip_seg_of, selected + 1, segments.size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = DdTextTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            StepButton(
                icon = Icons.Filled.ChevronRight,
                enabled = selected < segments.lastIndex,
                contentDescription = stringResource(R.string.trip_seg_next),
                onClick = { onStep(1) },
            )
        }
    }
}

/**
 * Every segment at a glance: one block per segment, as wide as its share of the drive's distance,
 * coloured by speed band, with the selected one full height and lit. It is both the shape of the
 * drive and a way to jump straight to a stretch.
 *
 * Drawn as one canvas rather than a row of boxes because a real drive has far more segments than the
 * artboard's eight. At 80, laying out 80 views with a 3dp gutter spends 237dp of a 320dp row on
 * gutters, so every block collapses to its minimum width and the proportionality — the entire point
 * — is lost. Here the gap is a single pixel and the widths stay true at any count.
 */
@Composable
private fun SegmentRail(
    segments: List<Segment>,
    bands: List<SpeedBand>,
    records: List<Boolean>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    val total = segments.sumOf { it.distanceMeters.toDouble() }.coerceAtLeast(1.0)
    val outline = MaterialTheme.colorScheme.outline

    Canvas(
        modifier.pointerInput(segments) {
            detectTapGestures { offset ->
                // Map the tap back through the same proportional split used to draw.
                var travelled = 0.0
                val fraction = (offset.x / size.width).coerceIn(0f, 1f) * total
                segments.forEachIndexed { index, segment ->
                    travelled += segment.distanceMeters
                    if (fraction <= travelled) {
                        onSelect(index)
                        return@detectTapGestures
                    }
                }
                onSelect(segments.lastIndex)
            }
        },
    ) {
        val gap = 1.dp.toPx()
        val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        var x = 0f
        segments.forEachIndexed { index, segment ->
            val width = (segment.distanceMeters / total * size.width).toFloat()
            val isSelected = index == selected
            val height = if (isSelected) size.height else size.height * 0.55f
            drawRoundRect(
                color = when {
                    isSelected && records[index] -> DdPurpleSector
                    isSelected -> bands[index].color
                    else -> bands[index].color.copy(alpha = 0.35f)
                },
                topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2f),
                size = androidx.compose.ui.geometry.Size((width - gap).coerceAtLeast(1f), height),
                cornerRadius = radius,
            )
            x += width
        }
        // A hairline under the whole rail so a drive of very short segments still reads as a strip.
        drawLine(
            color = outline,
            start = androidx.compose.ui.geometry.Offset(0f, size.height),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    // A dead control is dimmed rather than hidden, so the row doesn't reflow at either end.
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) DdSurfaceElevated else DdSurfaceSheet)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) DdTextBright else DdTextDisabled,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun OrdinalChip(position: Int, total: Int, modifier: Modifier) {
    val ddType = LocalDdType.current
    Row(
        modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DdGlassPanel)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text("$position", style = ddType.numericMono.copy(fontSize = 16.sp), color = DdTextBright)
        Text(" / $total", style = ddType.numericMono.copy(fontSize = 16.sp), color = DdTextTertiary)
    }
}

@Composable
private fun SpeedBandLegend(modifier: Modifier) {
    Row(
        modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DdGlassPanel)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedBand.entries.forEach { band ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(band.color))
                Text(stringResource(band.labelRes), style = MaterialTheme.typography.labelSmall, color = DdTextSecondary)
            }
        }
    }
}

/** How a segment was driven relative to the rest of the drive. */
private enum class SpeedBand(val color: Color, val labelRes: Int) {
    FAST(DdSuccess, R.string.trip_band_fast),
    STEADY(DdAmber, R.string.trip_band_steady),
    SLOW(DdError, R.string.trip_band_slow),
}

/**
 * Bands are relative to the drive's own fastest stretch, not to an absolute speed: the point is to
 * show where *this* drive flowed and where it didn't, and a 50 km/h town run has fast stretches too.
 */
private fun speedBands(segments: List<Segment>): List<SpeedBand> {
    val fastest = segments.maxOf { it.avgSpeedMps }.coerceAtLeast(0.1f)
    return segments.map { segment ->
        when {
            segment.avgSpeedMps >= fastest * 0.66f -> SpeedBand.FAST
            segment.avgSpeedMps >= fastest * 0.33f -> SpeedBand.STEADY
            else -> SpeedBand.SLOW
        }
    }
}

/**
 * The path to draw for each segment.
 *
 * Segments carry only their end coordinates, so the shape comes from the raw trace — sliced by
 * *time*, not by matching coordinates back to fixes. Segments tile the drive and their durations sum
 * to it (the CP22 contract), so each one's window is just the running total of the durations before
 * it. Matching by nearest coordinate is what produced boundaries that jumped ahead and swallowed
 * their neighbours, and there is no reason to reintroduce it here.
 *
 * Falls back to a straight line between the segment's endpoints when the drive has no route points —
 * they are local-only, so a drive restored from Firestore onto another device has none.
 */
private fun segmentShapes(detail: TripDetail): List<List<LatLng>> {
    val trace = detail.routePoints
    val straight = detail.segments.map {
        listOf(LatLng(it.startLat, it.startLng), LatLng(it.endLat, it.endLng))
    }
    if (trace.size < 2) return straight

    val start = trace.first().timestamp
    var elapsed = 0L
    return detail.segments.mapIndexed { index, segment ->
        val from = start + elapsed
        elapsed += segment.durationMs
        val to = start + elapsed
        val slice = trace.filter { it.timestamp in from..to }.map { LatLng(it.lat, it.lng) }
        if (slice.size >= 2) slice else straight[index]
    }
}

/**
 * The dark map from `design/tokens.md` §2.1 — the HUD and the Segments panel are both designed to
 * sit over it, and the default Google styling washes the route colours out. Parsed once per
 * composition; the resource never changes.
 */
@Composable
private fun rememberDarkMapStyle(): MapStyleOptions {
    val context = LocalContext.current
    return remember(context) { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) }
}

/** Semi-opaque panel behind the map chips — the flat fallback for the design's backdrop blur. */
private val DdGlassPanel = Color(0xE6101216)

/** The disabled arrow ink from the artboard: present, but plainly not a control right now. */
private val DdTextDisabled = Color(0xFF3A4048)

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
/** A stretch of the trace drawn as one polyline because its speed rounds to the same shade. */
private class SpeedRun(val indices: IntRange, val speedMps: Float)

/**
 * Splits the trace into at most [MAX_SPEED_RUNS] stretches, each coloured by the mean speed of the
 * fixes in it.
 *
 * The count has to be *bounded*, not merely reduced. Drawing one polyline per hop put 5,162 of them
 * on the 173 km reference drive and the Maps renderer ran the heap out — a hard crash on the app's
 * own showcase drive. Merging neighbours of similar speed is not enough either: GPS speed jitters
 * across any threshold you pick, so a noisy trace still yields thousands of runs. A fixed budget
 * cannot, however long the drive.
 */
private fun speedRuns(points: List<app.drivedelta.domain.model.RoutePoint>, maxSpeedMps: Float): List<SpeedRun> {
    if (points.size < 2) return emptyList()
    val stride = maxOf(1, (points.size - 1) / MAX_SPEED_RUNS)
    val runs = mutableListOf<SpeedRun>()
    var start = 0
    while (start < points.lastIndex) {
        // Stretches overlap by one point so the line stays continuous across a colour change.
        val end = minOf(start + stride, points.lastIndex)
        val mean = (start..end).map { points[it].speedMps }.average().toFloat()
        runs += SpeedRun(start..end, mean)
        start = end
    }
    return runs
}

private const val MAX_SPEED_RUNS = 120

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
