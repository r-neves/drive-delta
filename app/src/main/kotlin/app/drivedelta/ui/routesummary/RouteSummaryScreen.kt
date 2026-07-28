package app.drivedelta.ui.routesummary

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.RouteDrivePoint
import app.drivedelta.domain.model.RouteSummary
import app.drivedelta.ui.components.ScatterKind
import app.drivedelta.ui.components.ScatterPoint
import app.drivedelta.ui.components.SpeedCostScatter
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPurpleSector
import app.drivedelta.ui.theme.DdSuccess
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSummaryScreen(
    onBack: () -> Unit,
    viewModel: RouteSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = LocalDdTokens.current
    val context = LocalContext.current
    val summary = state.summary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (summary != null) {
                        Column {
                            Text(
                                routeTitle(summary),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                routeSubtitle(summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(stringResource(R.string.route_summary_title), color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (summary != null) {
                        val text = shareText(summary)
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.route_summary_share))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                summary == null -> Text(
                    stringResource(R.string.trip_not_found),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = tokens.screenPadding)
                        .padding(bottom = tokens.spaceXl),
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
                ) {
                    Spacer(Modifier.height(tokens.spaceXs))
                    RideSavedBanner(summary)
                    TotalTimeCard(summary, state.currencyCode)
                    SegmentTiles(summary)
                    ScatterSection(summary, state.currencyCode)
                }
            }
        }
    }
}

// --- Ride saved banner --------------------------------------------------------------------------

@Composable
private fun RideSavedBanner(summary: RouteSummary) {
    val tokens = LocalDdTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMd))
            .background(DdSuccess.copy(alpha = 0.08f))
            .border(1.dp, DdSuccess.copy(alpha = 0.5f), RoundedCornerShape(tokens.radiusMd))
            .padding(tokens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(DdSuccess.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = DdSuccess)
        }
        Column {
            Text(
                stringResource(R.string.route_summary_ride_saved),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = DdSuccess,
            )
            Text(
                stringResource(
                    R.string.route_summary_ride_saved_detail,
                    summary.segmentsTimed,
                    summary.newPersonalBests,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = DdSuccess.copy(alpha = 0.85f),
            )
        }
    }
}

// --- Total-time card ----------------------------------------------------------------------------

@Composable
private fun TotalTimeCard(summary: RouteSummary, currencyCode: String) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusCard))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard))
            .padding(tokens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    stringResource(R.string.route_summary_total_time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(tokens.spaceSm))
                Text(
                    formatClock(summary.totalTimeMs),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.route_summary_vs_best),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(tokens.spaceSm))
                val delta = summary.deltaVsBestMs
                val faster = delta < 0
                val neutral = delta == 0L
                Text(
                    (if (neutral) "" else if (faster) "▾ " else "▴ ") + formatClock(abs(delta)),
                    style = ddType.deltaValue,
                    color = when {
                        neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                        faster -> DdDeltaFaster
                        else -> DdError
                    },
                )
                Text(
                    stringResource(R.string.route_summary_best_caption, formatClock(summary.bestTotalMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DdTextTertiary,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DdOutline))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCell(
                value = String.format(Locale.US, "%.1f", summary.distanceMeters / 1000f),
                unit = "km",
                label = stringResource(R.string.route_summary_distance),
            )
            StatCell(
                value = summary.avgSpeedKph.roundToInt().toString(),
                unit = "km/h",
                label = stringResource(R.string.route_summary_avg_speed),
            )
            StatCell(
                value = summary.energyCost?.let { formatMoney(it, currencyCode) } ?: "—",
                unit = "",
                label = stringResource(R.string.route_summary_energy_cost),
            )
        }
    }
}

@Composable
private fun StatCell(value: String, unit: String, label: String) {
    val ddType = LocalDdType.current
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = ddType.statValue, color = MaterialTheme.colorScheme.onSurface)
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- Faster / Slower / Purple tiles -------------------------------------------------------------

@Composable
private fun SegmentTiles(summary: RouteSummary) {
    val tokens = LocalDdTokens.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd)) {
        SegmentTile(Modifier.weight(1f), summary.fasterCount, stringResource(R.string.route_summary_faster), DdDeltaFaster)
        SegmentTile(Modifier.weight(1f), summary.slowerCount, stringResource(R.string.route_summary_slower), DdError)
        SegmentTile(Modifier.weight(1f), summary.purpleCount, stringResource(R.string.route_summary_purple), DdPurpleSector)
    }
}

@Composable
private fun SegmentTile(modifier: Modifier, count: Int, label: String, color: Color) {
    val tokens = LocalDdTokens.current
    Column(
        modifier
            .clip(RoundedCornerShape(tokens.radiusMd))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(tokens.radiusMd))
            .padding(vertical = tokens.spaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.9f))
    }
}

// --- Speed vs. cost scatter ---------------------------------------------------------------------

@Composable
private fun ScatterSection(summary: RouteSummary, currencyCode: String) {
    val tokens = LocalDdTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceMd)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.route_summary_speed_cost),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.route_summary_drive_count, summary.driveCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusCard))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard))
                .padding(tokens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
        ) {
            Text(
                stringResource(R.string.route_summary_scatter_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.scatter.size < 2) {
                Text(
                    stringResource(R.string.route_summary_scatter_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DdTextTertiary,
                    modifier = Modifier.padding(vertical = tokens.spaceLg),
                )
            } else {
                SpeedCostScatter(
                    points = summary.scatter.map { it.toScatterPoint() },
                    currencySymbol = currencySymbol(currencyCode),
                )
            }
        }
    }
}

/** Maps a route drive to a scatter marker; this-drive wins over fastest/cheapest, matching the design. */
private fun RouteDrivePoint.toScatterPoint(): ScatterPoint = ScatterPoint(
    speedKph = avgSpeedKph,
    cost = energyCost,
    kind = when {
        isThisDrive -> ScatterKind.THIS_DRIVE
        isFastest -> ScatterKind.FASTEST
        isCheapest -> ScatterKind.CHEAPEST
        else -> ScatterKind.NORMAL
    },
)

// --- Helpers ------------------------------------------------------------------------------------

private val SUBTITLE_FORMAT = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())

private fun routeTitle(s: RouteSummary): String {
    val origin = s.originName ?: "—"
    val dest = s.destinationName ?: "—"
    return "$origin → $dest"
}

private fun routeSubtitle(s: RouteSummary): String {
    val date = SUBTITLE_FORMAT.format(Date(s.startTime))
    return if (s.carName != null) "$date · ${s.carName}" else date
}

private fun shareText(s: RouteSummary): String {
    val delta = s.deltaVsBestMs
    val vs = when {
        delta == 0L -> "matched my best"
        delta < 0 -> "${formatClock(abs(delta))} faster than my best"
        else -> "${formatClock(delta)} off my best"
    }
    return "DriveDelta — ${routeTitle(s)}: ${formatClock(s.totalTimeMs)} ($vs) over ${
        String.format(Locale.US, "%.1f", s.distanceMeters / 1000f)
    } km."
}

private fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

private fun currencySymbol(code: String): String = try {
    Currency.getInstance(code).symbol
} catch (e: Exception) {
    "€"
}

private fun formatMoney(v: Float, code: String): String =
    currencySymbol(code) + String.format(Locale.US, "%.2f", v)
