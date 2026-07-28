package app.drivedelta.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.Car
import app.drivedelta.ui.components.RecentTripCard
import app.drivedelta.ui.components.formatClock
import app.drivedelta.ui.components.routeTitle
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPurpleSector
import app.drivedelta.ui.theme.DdSegmentActive
import app.drivedelta.ui.theme.DdSurface
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    onOpenTrip: (String) -> Unit,
    onOpenRouteSummary: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = LocalDdTokens.current
    var tab by remember { mutableStateOf(TripTab.RECENT) }
    var searchActive by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(Unit) {
        viewModel.undoSignal.collect {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    Box(Modifier.fillMaxSize().padding(horizontal = tokens.screenPadding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = tokens.spaceLg, bottom = tokens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
        ) {
            item {
                TripsHeader(
                    searchActive = searchActive,
                    onToggleSearch = { searchActive = !searchActive; if (!searchActive) viewModel.setQuery("") },
                    activeFilterCount = state.activeFilterCount,
                    onOpenFilter = { showFilters = true },
                )
            }
            item { TripTabs(tab, onSelect = { tab = it }) }
            if (searchActive) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.trips_search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    )
                }
            }

            if (state.isEmpty) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = tokens.spaceXl * 2), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (tab == TripTab.RECENT) {
                item { StatsCard(state.stats) }
                state.sections.forEach { section ->
                    item(key = "day_${section.dayEpoch}") { DayHeader(section.dayEpoch) }
                    items(section.items.size, key = { section.items[it].tripId }) { i ->
                        val item = section.items[i]
                        RecentTripCard(
                            startTime = item.startTime,
                            originName = item.originName,
                            destName = item.destName,
                            carName = item.carName,
                            fuelType = item.fuelType,
                            durationMs = item.durationMs,
                            distanceMeters = item.distanceMeters,
                            isNewPb = item.isNewPb,
                            deltaMs = item.deltaVsPrevMs,
                            onClick = { onOpenTrip(item.tripId) },
                            onLongClick = { pendingDelete = item.tripId },
                        )
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.trips_by_route_caption),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = tokens.spaceXs),
                    )
                }
                items(state.routes.size, key = { state.routes[it].routeHash }) { i ->
                    val route = state.routes[i]
                    RouteCard(route, onClick = { onOpenRouteSummary(route.openTripId) })
                }
            }
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = tokens.spaceLg),
        )
    }

    if (showFilters) {
        FiltersSheet(
            cars = state.cars,
            pairOptions = state.pairOptions,
            selectedCarId = state.selectedCarId,
            selectedPair = state.selectedPair,
            dateRange = state.dateRange,
            onSelectCar = viewModel::selectCar,
            onSelectPair = viewModel::selectPair,
            onSelectDateRange = viewModel::setDateRange,
            onClearAll = viewModel::clearFilters,
            onDismiss = { showFilters = false },
        )
    }

    pendingDelete?.let { tripId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTrip(tripId); pendingDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

// --- Header (title + search + filter) -----------------------------------------------------------

@Composable
private fun TripsHeader(
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    activeFilterCount: Int,
    onOpenFilter: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.trips_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleSearch) {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(R.string.trips_search),
                tint = if (searchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onOpenFilter) {
            Icon(
                Icons.Outlined.FilterList,
                contentDescription = if (activeFilterCount > 0) {
                    stringResource(R.string.trips_filter_active, activeFilterCount)
                } else {
                    stringResource(R.string.trips_filter)
                },
                tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TripTabs(selected: TripTab, onSelect: (TripTab) -> Unit) {
    val tokens = LocalDdTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusInput))
            .background(DdSurface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusInput))
            .padding(tokens.spaceXs),
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs),
    ) {
        TabButton(Modifier.weight(1f), stringResource(R.string.trips_tab_recent), selected == TripTab.RECENT) { onSelect(TripTab.RECENT) }
        TabButton(Modifier.weight(1f), stringResource(R.string.trips_tab_by_route), selected == TripTab.BY_ROUTE) { onSelect(TripTab.BY_ROUTE) }
    }
}

@Composable
private fun TabButton(modifier: Modifier, label: String, active: Boolean, onClick: () -> Unit) {
    val tokens = LocalDdTokens.current
    Box(
        modifier
            .clip(RoundedCornerShape(tokens.radiusSm))
            .background(if (active) DdSegmentActive else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = tokens.spaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// --- Recent tab ---------------------------------------------------------------------------------

@Composable
private fun StatsCard(stats: TripsStats) {
    val tokens = LocalDdTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusCard))
            .background(DdSurface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard))
            .padding(vertical = tokens.spaceLg),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatCell(stats.drives.toString(), null, stringResource(R.string.trips_stat_drives), MaterialTheme.colorScheme.onSurface)
        StatCell(stats.distanceKm.roundToInt().toString(), "km", stringResource(R.string.trips_stat_distance), MaterialTheme.colorScheme.onSurface)
        StatCell(stats.newPbs.toString(), null, stringResource(R.string.trips_stat_new_pbs), DdPurpleSector)
    }
}

@Composable
private fun StatCell(value: String, unit: String?, label: String, valueColor: Color) {
    val ddType = LocalDdType.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = ddType.statValue, color = valueColor, fontWeight = FontWeight.Bold)
            if (unit != null) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayHeader(dayEpoch: Long) {
    Text(
        dayLabel(dayEpoch),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = LocalDdTokens.current.spaceSm, bottom = LocalDdTokens.current.spaceXs),
    )
}

// --- By-route tab -------------------------------------------------------------------------------

@Composable
private fun RouteCard(route: RouteItem, onClick: () -> Unit) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    val trendColor = when (route.trend) {
        RouteTrend.FASTER -> DdDeltaFaster
        RouteTrend.SLOWER -> DdError
        RouteTrend.BEST -> DdPurpleSector
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusCard))
            .background(DdSurface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard))
            .clickable(onClick = onClick)
            .padding(tokens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    routeTitle(route.originName, route.destName, stringResource(R.string.trips_drive_fallback)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.trips_route_meta, route.driveCount, route.distanceMeters / 1000f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatClock(route.bestMs), style = ddType.statValue, color = if (route.trend == RouteTrend.BEST) DdPurpleSector else MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(if (route.trend == RouteTrend.BEST) R.string.trips_new_pb else R.string.trips_best),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (route.trend == RouteTrend.BEST) DdPurpleSector else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (route.series.size >= 2) {
            Sparkline(route.series, trendColor, Modifier.fillMaxWidth().height(64.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.trips_last_drives, route.series.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DdTextTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(
                        when (route.trend) {
                            RouteTrend.FASTER -> R.string.trips_trend_faster
                            RouteTrend.SLOWER -> R.string.trips_trend_slower
                            RouteTrend.BEST -> R.string.trips_trend_best
                        },
                    ),
                    style = ddType.numericMono,
                    color = trendColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Line of recent drive times. Faster (shorter) drives plot lower — "faster ↓". */
@Composable
private fun Sparkline(series: List<Long>, color: Color, modifier: Modifier) {
    val min = series.min()
    val max = series.max()
    val span = (max - min).coerceAtLeast(1L)
    Canvas(modifier) {
        val n = series.size
        val stepX = if (n > 1) size.width / (n - 1) else 0f
        val padY = 6f
        fun px(i: Int) = i * stepX
        // Faster (shorter) drives sit lower on the chart — the "faster ↓" legend.
        fun py(v: Long) = padY + (max - v).toFloat() / span * (size.height - 2 * padY)
        val path = androidx.compose.ui.graphics.Path()
        series.forEachIndexed { i, v ->
            val x = px(i); val y = py(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        // End dot on the newest drive.
        drawCircle(color, radius = 6f, center = androidx.compose.ui.geometry.Offset(px(n - 1), py(series.last())))
    }
}

// --- Filters sheet (vehicle / route / date) -----------------------------------------------------

private enum class DatePreset { ANY, D7, D30, MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(
    cars: List<Car>,
    pairOptions: List<RoutePairOption>,
    selectedCarId: String?,
    selectedPair: RoutePair?,
    dateRange: LongRange?,
    onSelectCar: (String?) -> Unit,
    onSelectPair: (RoutePair?) -> Unit,
    onSelectDateRange: (LongRange?) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalDdTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    // Which preset produced the active range, for chip highlighting. A range set via the picker or
    // restored on reopen shows as CUSTOM (with the formatted range on its chip).
    var datePreset by remember { mutableStateOf(if (dateRange == null) DatePreset.ANY else DatePreset.CUSTOM) }
    val anyActive = selectedCarId != null || selectedPair != null || dateRange != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DdSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.screenPadding)
                .padding(bottom = tokens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
        ) {
            Text(
                stringResource(R.string.trips_filter_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Vehicle
            FilterSectionLabel(stringResource(R.string.trips_filter_section_vehicle))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
                FilterPill(stringResource(R.string.history_filter_all), selectedCarId == null) { onSelectCar(null) }
                cars.forEach { car ->
                    FilterPill(car.name, selectedCarId == car.id) {
                        onSelectCar(if (selectedCarId == car.id) null else car.id)
                    }
                }
            }

            // Route (origin → destination)
            if (pairOptions.isNotEmpty()) {
                FilterSectionLabel(stringResource(R.string.trips_filter_section_route))
                val unknown = stringResource(R.string.trips_filter_route_unknown)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
                    FilterPill(stringResource(R.string.history_filter_all), selectedPair == null) { onSelectPair(null) }
                    pairOptions.forEach { opt ->
                        val chosen = selectedPair == opt.pair
                        FilterPill(routeTitle(opt.originName, opt.destName, unknown), chosen) {
                            onSelectPair(if (chosen) null else opt.pair)
                        }
                    }
                }
            }

            // Date
            FilterSectionLabel(stringResource(R.string.trips_filter_section_date))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
                FilterPill(stringResource(R.string.trips_filter_date_any), datePreset == DatePreset.ANY && dateRange == null) {
                    datePreset = DatePreset.ANY; onSelectDateRange(null)
                }
                FilterPill(stringResource(R.string.trips_filter_date_7d), datePreset == DatePreset.D7) {
                    datePreset = DatePreset.D7; onSelectDateRange(presetRange(6))
                }
                FilterPill(stringResource(R.string.trips_filter_date_30d), datePreset == DatePreset.D30) {
                    datePreset = DatePreset.D30; onSelectDateRange(presetRange(29))
                }
                FilterPill(stringResource(R.string.trips_filter_date_month), datePreset == DatePreset.MONTH) {
                    datePreset = DatePreset.MONTH; onSelectDateRange(monthToDateRange())
                }
                val customLabel = if (datePreset == DatePreset.CUSTOM && dateRange != null) {
                    formatRange(dateRange)
                } else {
                    stringResource(R.string.trips_filter_date_custom)
                }
                FilterPill(customLabel, datePreset == DatePreset.CUSTOM) { showDatePicker = true }
            }

            Spacer(Modifier.height(tokens.spaceXs))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onClearAll(); datePreset = DatePreset.ANY },
                    enabled = anyActive,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.trips_filter_clear)) }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.trips_filter_done))
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            datePreset = DatePreset.CUSTOM
                            onSelectDateRange(customRange(start, end))
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.trips_filter_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DateRangePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = LocalDdTokens.current.spaceXs),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

// Local-timezone date-range builders for the presets and the custom picker.

private val filterZone: ZoneId get() = ZoneId.systemDefault()

/** A range covering the last [daysBack]+1 whole days up to the end of today, in local time. */
private fun presetRange(daysBack: Long): LongRange {
    val today = LocalDate.now()
    return dayStart(today.minusDays(daysBack))..dayEnd(today)
}

/** From the first of the current month through the end of today, in local time. */
private fun monthToDateRange(): LongRange {
    val today = LocalDate.now()
    return dayStart(today.withDayOfMonth(1))..dayEnd(today)
}

/** The DateRangePicker reports UTC start-of-day millis; map both ends onto whole local days. */
private fun customRange(startUtcMillis: Long, endUtcMillis: Long): LongRange {
    val start = Instant.ofEpochMilli(startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val end = Instant.ofEpochMilli(endUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return dayStart(start)..dayEnd(end)
}

private fun dayStart(d: LocalDate): Long = d.atStartOfDay(filterZone).toInstant().toEpochMilli()
private fun dayEnd(d: LocalDate): Long = d.plusDays(1).atStartOfDay(filterZone).toInstant().toEpochMilli() - 1

private val RANGE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun formatRange(range: LongRange): String {
    val start = Instant.ofEpochMilli(range.first).atZone(filterZone).toLocalDate()
    val end = Instant.ofEpochMilli(range.last).atZone(filterZone).toLocalDate()
    return "${RANGE_FORMAT.format(start)} – ${RANGE_FORMAT.format(end)}"
}

// --- Helpers ------------------------------------------------------------------------------------

@Composable
private fun dayLabel(epochDay: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (epochDay) {
        today -> stringResource(R.string.trips_day_today)
        today - 1 -> stringResource(R.string.trips_day_yesterday)
        else -> LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
    }
}
