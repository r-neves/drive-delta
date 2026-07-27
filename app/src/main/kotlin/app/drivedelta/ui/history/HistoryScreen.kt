package app.drivedelta.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.FuelType
import app.drivedelta.ui.cars.badgeColor
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPurpleRowBg
import app.drivedelta.ui.theme.DdPurpleRowBorder
import app.drivedelta.ui.theme.DdPurpleSector
import app.drivedelta.ui.theme.DdSegmentActive
import app.drivedelta.ui.theme.DdSuccess
import app.drivedelta.ui.theme.DdSurface
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

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
    var filterOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

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
                    filterOpen = filterOpen,
                    onOpenFilter = { filterOpen = true },
                    onCloseFilter = { filterOpen = false },
                    cars = state.cars,
                    selectedCarId = state.selectedCarId,
                    onSelectCar = { viewModel.selectCar(it); filterOpen = false },
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
                        RecentCard(
                            item = item,
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
    filterOpen: Boolean,
    onOpenFilter: () -> Unit,
    onCloseFilter: () -> Unit,
    cars: List<app.drivedelta.domain.model.Car>,
    selectedCarId: String?,
    onSelectCar: (String?) -> Unit,
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
        Box {
            IconButton(onClick = onOpenFilter) {
                Icon(
                    Icons.Outlined.FilterList,
                    contentDescription = stringResource(R.string.trips_filter),
                    tint = if (selectedCarId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            DropdownMenu(expanded = filterOpen, onDismissRequest = onCloseFilter) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_filter_all)) },
                    onClick = { onSelectCar(null) },
                )
                cars.forEach { car ->
                    DropdownMenuItem(
                        text = { Text(car.name) },
                        onClick = { onSelectCar(car.id) },
                    )
                }
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCard(item: RecentTripItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    val pb = item.isNewPb
    val titleColor = if (pb) DdPurpleSector else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusCard))
            .background(if (pb) DdPurpleRowBg else DdSurface)
            .border(1.dp, if (pb) DdPurpleRowBorder else DdOutline, RoundedCornerShape(tokens.radiusCard))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(tokens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        // Top: time · (car+fuel) or NEW PB pill
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                TIME_FORMAT.format(Date(item.startTime)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (pb) {
                NewPbPill()
            } else if (item.carName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
                    FuelIcon(item.fuelType)
                    Text(item.carName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        // Middle: Origin → Destination
        Text(
            routeTitle(item.originName, item.destName, stringResource(R.string.trips_drive_fallback)),
            style = MaterialTheme.typography.headlineMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Bottom: duration + distance · delta / best
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatClock(item.durationMs)}   ${String.format(Locale.US, "%.1f", item.distanceMeters / 1000f)} km",
                style = ddType.numericMono,
                color = if (pb) DdPurpleSector else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            when {
                pb -> Text("★ best", style = ddType.numericMono, color = DdPurpleSector, fontWeight = FontWeight.SemiBold)
                item.deltaVsPrevMs != null && item.deltaVsPrevMs != 0L -> {
                    val faster = item.deltaVsPrevMs < 0
                    Text(
                        (if (faster) "▾ " else "▴ ") + formatClock(abs(item.deltaVsPrevMs)),
                        style = ddType.numericMono,
                        color = if (faster) DdDeltaFaster else DdError,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewPbPill() {
    val tokens = LocalDdTokens.current
    Box(
        Modifier
            .clip(RoundedCornerShape(tokens.radiusSm))
            .border(1.dp, DdPurpleRowBorder, RoundedCornerShape(tokens.radiusSm))
            .padding(horizontal = tokens.spaceMd, vertical = tokens.spaceXs),
    ) {
        Text(stringResource(R.string.trips_new_pb), style = MaterialTheme.typography.labelSmall, color = DdPurpleSector, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FuelIcon(fuelType: FuelType?) {
    if (fuelType == null) return
    val icon = if (fuelType == FuelType.ELECTRIC) Icons.Filled.Bolt else Icons.Filled.LocalGasStation
    Icon(icon, contentDescription = null, tint = fuelType.badgeColor, modifier = Modifier.size(18.dp))
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

// --- Helpers ------------------------------------------------------------------------------------

private fun routeTitle(origin: String?, dest: String?, fallback: String): String = when {
    origin != null || dest != null -> "${origin ?: "—"} → ${dest ?: "—"}"
    else -> fallback
}

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

private fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
