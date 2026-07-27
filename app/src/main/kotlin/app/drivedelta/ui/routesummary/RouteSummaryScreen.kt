package app.drivedelta.ui.routesummary

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.RouteDrivePoint
import app.drivedelta.domain.model.RouteSummary
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPrimary
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
import kotlin.math.ceil
import kotlin.math.floor
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
                    TotalTimeCard(summary)
                    SegmentTiles(summary)
                    ScatterSection(summary)
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
private fun TotalTimeCard(summary: RouteSummary) {
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
                value = summary.energyCost?.let { formatMoney(it) } ?: "—",
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
private fun ScatterSection(summary: RouteSummary) {
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
                ScatterChart(summary.scatter)
            }
        }
    }
}

@Composable
private fun ScatterChart(points: List<RouteDrivePoint>) {
    val density = LocalDensity.current
    val axisPaint = remember(density) {
        android.graphics.Paint().apply {
            color = DdTextTertiary.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
        }
    }
    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            color = DdSuccess.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    // Domain (padded, with sensible fallbacks matching the design's 40–100 km/h × £2–6 range).
    val speeds = points.map { it.avgSpeedKph }
    val costs = points.map { it.energyCost }
    var xMin = floor((speeds.min() - 5) / 10.0) * 10
    var xMax = ceil((speeds.max() + 5) / 10.0) * 10
    if (xMax - xMin < 20) { xMin -= 10; xMax += 10 }
    var yMin = floor(costs.min().toDouble())
    var yMax = ceil(costs.max().toDouble())
    if (yMax - yMin < 2) { yMin -= 1; yMax += 1 }
    if (yMin < 0) yMin = 0.0

    val leftPad = with(density) { 34.dp.toPx() }
    val bottomPad = with(density) { 22.dp.toPx() }
    val topPad = with(density) { 18.dp.toPx() }
    val trend = fitQuadratic(points.map { it.avgSpeedKph.toDouble() to it.energyCost.toDouble() })

    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val plotLeft = leftPad
        val plotRight = size.width
        val plotTop = topPad
        val plotBottom = size.height - bottomPad
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop

        fun sx(speed: Double) = plotLeft + ((speed - xMin) / (xMax - xMin) * plotW).toFloat()
        fun sy(cost: Double) = plotBottom - ((cost - yMin) / (yMax - yMin) * plotH).toFloat()

        // Horizontal gridlines + currency labels.
        val ySteps = (yMax - yMin).toInt().coerceIn(1, 6)
        for (i in 0..ySteps) {
            val v = yMin + (yMax - yMin) * i / ySteps
            val y = sy(v)
            drawLine(DdOutline, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(
                moneyAxisLabel(v.toFloat()), 0f, y + axisPaint.textSize / 3f, axisPaint,
            )
        }
        // X-axis speed labels.
        val xSteps = 4
        for (i in 0..xSteps) {
            val v = xMin + (xMax - xMin) * i / xSteps
            val x = sx(v)
            drawContext.canvas.nativeCanvas.drawText(
                v.roundToInt().toString(), x - axisPaint.textSize, size.height - 2f, axisPaint,
            )
        }

        // Trend U-curve (quadratic fit), dashed.
        trend?.let { (a, b, c) ->
            val path = Path()
            var started = false
            var xv = xMin
            val stepX = (xMax - xMin) / 48.0
            while (xv <= xMax + 1e-6) {
                val yv = (a * xv * xv + b * xv + c).coerceIn(yMin, yMax)
                val px = sx(xv); val py = sy(yv)
                if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
                xv += stepX
            }
            drawPath(
                path,
                color = DdPrimary.copy(alpha = 0.7f),
                style = Stroke(
                    width = with(density) { 1.5.dp.toPx() },
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                ),
            )
        }

        // Points.
        val r = with(density) { 4.dp.toPx() }
        points.forEach { p ->
            val cx = sx(p.avgSpeedKph.toDouble())
            val cy = sy(p.energyCost.toDouble())
            when {
                p.isThisDrive -> {
                    drawLine(
                        DdSuccess.copy(alpha = 0.5f), Offset(cx, cy), Offset(cx, plotBottom),
                        strokeWidth = with(density) { 1.dp.toPx() },
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                    drawCircle(DdSuccess.copy(alpha = 0.18f), radius = r * 2.4f, center = Offset(cx, cy))
                    drawCircle(DdSuccess, radius = r * 1.1f, center = Offset(cx, cy))
                    drawContext.canvas.nativeCanvas.drawText("THIS DRIVE", cx - r * 5f, cy - r * 3f, labelPaint)
                }
                p.isFastest -> {
                    drawCircle(DdPurpleSector, radius = r * 1.7f, center = Offset(cx, cy), style = Stroke(width = with(density) { 2.dp.toPx() }))
                    drawCircle(DdPurpleSector, radius = r, center = Offset(cx, cy))
                }
                p.isCheapest -> {
                    drawCircle(DdSuccess.copy(alpha = 0.7f), radius = r * 1.7f, center = Offset(cx, cy), style = Stroke(width = with(density) { 2.dp.toPx() }))
                    drawCircle(DdTextTertiary, radius = r * 0.8f, center = Offset(cx, cy))
                }
                else -> drawCircle(DdTextTertiary, radius = r * 0.8f, center = Offset(cx, cy))
            }
        }
    }
}

// --- Helpers ------------------------------------------------------------------------------------

/** Least-squares quadratic fit y = a·x² + b·x + c; null if fewer than 3 points, singular, or not a U. */
private fun fitQuadratic(pts: List<Pair<Double, Double>>): Triple<Double, Double, Double>? {
    if (pts.size < 3) return null
    var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
    var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
    for ((x, y) in pts) {
        val x2 = x * x
        s0 += 1; s1 += x; s2 += x2; s3 += x2 * x; s4 += x2 * x2
        t0 += y; t1 += x * y; t2 += x2 * y
    }
    val m = arrayOf(
        doubleArrayOf(s0, s1, s2),
        doubleArrayOf(s1, s2, s3),
        doubleArrayOf(s2, s3, s4),
    )
    val d = det3(m)
    if (abs(d) < 1e-9) return null
    val rhs = doubleArrayOf(t0, t1, t2)
    val c = det3(replaceCol(m, 0, rhs)) / d
    val b = det3(replaceCol(m, 1, rhs)) / d
    val a = det3(replaceCol(m, 2, rhs)) / d
    if (a <= 0) return null // draw only an upward (U-shaped) curve; a downward fit reads wrong
    return Triple(a, b, c)
}

private fun det3(m: Array<DoubleArray>): Double =
    m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
        m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
        m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])

private fun replaceCol(m: Array<DoubleArray>, col: Int, v: DoubleArray): Array<DoubleArray> =
    Array(3) { r -> DoubleArray(3) { c -> if (c == col) v[r] else m[r][c] } }

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

private fun currencySymbol(): String = try {
    Currency.getInstance(Locale.getDefault()).symbol
} catch (e: Exception) {
    "€"
}

private fun formatMoney(v: Float): String = currencySymbol() + String.format(Locale.US, "%.2f", v)

private fun moneyAxisLabel(v: Float): String = currencySymbol() + v.roundToInt()
