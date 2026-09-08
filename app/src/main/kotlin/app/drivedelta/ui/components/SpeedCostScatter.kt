package app.drivedelta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdPurpleSector
import app.drivedelta.ui.theme.DdSuccess
import app.drivedelta.ui.theme.DdTextTertiary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * What a drive's cost is plotted against.
 *
 * [DURATION] is the default because it is the question actually being asked — "did going faster cost
 * me more?" is really "did arriving sooner cost me more?", and a duration is the number the driver
 * felt. Average speed is the same drive seen through its distance, which only compares across drives
 * of the same length; it stays available because on a fixed route it is the cleaner independent
 * variable.
 */
enum class ScatterAxis { DURATION, SPEED }

/** How a single drive renders on the cost scatter. */
enum class ScatterKind { NORMAL, THIS_DRIVE, THIS_PENDING, FASTEST, CHEAPEST, ESTIMATED }

/**
 * One drive on the cost scatter. Carries **both** x metrics so the axis can be switched without
 * rebuilding the data upstream. [cost] is null only for [ScatterKind.THIS_PENDING] — a drive whose
 * fuel hasn't been logged yet, plotted on the x axis with no cost.
 */
data class ScatterPoint(
    val speedKph: Float,
    val durationMs: Long,
    val cost: Float?,
    val kind: ScatterKind,
) {
    fun x(axis: ScatterAxis): Double = when (axis) {
        ScatterAxis.SPEED -> speedKph.toDouble()
        ScatterAxis.DURATION -> durationMs / 60_000.0
    }
}

/**
 * Energy cost against either ride duration or average speed, with a dashed quadratic trend U-curve —
 * the shared chart behind both the Route Summary and the Trip Detail cost sections
 * (design/mockups/trip-summary.png, Energy Logging-saved-drive-not-logged.png). Costed points drive
 * the curve; a not-yet-logged drive is marked on the x axis with the [pendingLabel]. Money labels use
 * [currencySymbol].
 *
 * The trend is only drawn when the fit is genuinely U-shaped, so switching [axis] to one where cost
 * rises or falls monotonically simply leaves the points without a curve rather than inventing one.
 */
@Composable
fun SpeedCostScatter(
    points: List<ScatterPoint>,
    currencySymbol: String,
    modifier: Modifier = Modifier,
    axis: ScatterAxis = ScatterAxis.DURATION,
    thisDriveLabel: String = "THIS DRIVE",
    pendingLabel: String = "NO COST YET",
) {
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
    val pendingPaint = remember(density) {
        android.graphics.Paint().apply {
            color = DdPrimary.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    val (xMin, xMax) = scatterXDomain(points.map { it.x(axis) }, axis)

    // Y domain over costed drives only; fall back to the design's €2–€6 band when none are costed.
    val costs = points.mapNotNull { it.cost }
    var yMin = if (costs.isEmpty()) 2.0 else floor(costs.min().toDouble())
    var yMax = if (costs.isEmpty()) 6.0 else ceil(costs.max().toDouble())
    if (yMax - yMin < 2) { yMin -= 1; yMax += 1 }
    if (yMin < 0) yMin = 0.0

    // A clock label needs hours once the axis runs past one, and that decision is made here, for the
    // whole axis, so every tick reads the same way.
    val useHours = axis == ScatterAxis.DURATION && xMax >= MINUTES_PER_HOUR
    val xSteps = if (useHours) 3 else 4

    val leftPad = with(density) { 34.dp.toPx() }
    val bottomPad = with(density) { 22.dp.toPx() }
    val topPad = with(density) { 18.dp.toPx() }
    // Labels are drawn centred-ish at `x - textSize`, so the rightmost one overhangs the plot by
    // (its width − textSize). Reserve exactly that, or "1:30:00" is clipped off the canvas edge.
    val rightPad = (
        axisPaint.measureText(formatAxisValue(xMax, axis, useHours)) - axisPaint.textSize
        ).coerceAtLeast(0f)
    val trend = fitQuadratic(points.mapNotNull { p -> p.cost?.let { p.x(axis) to it.toDouble() } })

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val plotLeft = leftPad
        val plotRight = size.width - rightPad
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
                currencySymbol + v.roundToInt(), 0f, y + axisPaint.textSize / 3f, axisPaint,
            )
        }
        // X-axis labels: km/h, or a clock. The clock format is chosen once for the whole axis, from
        // its widest value — picking per tick gives a 50–62 minute route "50:00 · 54:00 · 1:02:00",
        // where the first two now read as hours beside their h:mm:ss sibling. Long routes also get
        // fewer ticks: "1:24:00" five times across a phone's width collides.
        for (i in 0..xSteps) {
            val v = xMin + (xMax - xMin) * i / xSteps
            val x = sx(v)
            drawContext.canvas.nativeCanvas.drawText(
                formatAxisValue(v, axis, useHours), x - axisPaint.textSize, size.height - 2f, axisPaint,
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
            val cx = sx(p.x(axis))
            when (p.kind) {
                ScatterKind.THIS_PENDING -> {
                    // Drive not yet logged: sits on the speed axis only, marked on the bottom edge.
                    val cy = plotBottom
                    drawCircle(
                        DdPrimary, radius = r, center = Offset(cx, cy),
                        style = Stroke(
                            width = with(density) { 1.5.dp.toPx() },
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                        ),
                    )
                    drawContext.canvas.nativeCanvas.drawText(pendingLabel, cx - r * 5.5f, cy - r * 2.2f, pendingPaint)
                }
                ScatterKind.THIS_DRIVE -> {
                    val cy = sy((p.cost ?: yMin).toDouble())
                    drawLine(
                        DdSuccess.copy(alpha = 0.5f), Offset(cx, cy), Offset(cx, plotBottom),
                        strokeWidth = with(density) { 1.dp.toPx() },
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                    drawCircle(DdSuccess.copy(alpha = 0.18f), radius = r * 2.4f, center = Offset(cx, cy))
                    drawCircle(DdSuccess, radius = r * 1.1f, center = Offset(cx, cy))
                    drawContext.canvas.nativeCanvas.drawText(thisDriveLabel, cx - r * 5f, cy - r * 3f, labelPaint)
                }
                ScatterKind.FASTEST -> {
                    val cy = sy((p.cost ?: yMin).toDouble())
                    drawCircle(DdPurpleSector, radius = r * 1.7f, center = Offset(cx, cy), style = Stroke(width = with(density) { 2.dp.toPx() }))
                    drawCircle(DdPurpleSector, radius = r, center = Offset(cx, cy))
                }
                ScatterKind.CHEAPEST -> {
                    val cy = sy((p.cost ?: yMin).toDouble())
                    drawCircle(DdSuccess.copy(alpha = 0.7f), radius = r * 1.7f, center = Offset(cx, cy), style = Stroke(width = with(density) { 2.dp.toPx() }))
                    drawCircle(DdTextTertiary, radius = r * 0.8f, center = Offset(cx, cy))
                }
                ScatterKind.ESTIMATED -> {
                    // Gap-filled estimate: hollow, muted.
                    val cy = sy((p.cost ?: yMin).toDouble())
                    drawCircle(DdTextTertiary.copy(alpha = 0.6f), radius = r * 0.9f, center = Offset(cx, cy), style = Stroke(width = with(density) { 1.dp.toPx() }))
                }
                ScatterKind.NORMAL -> {
                    val cy = sy((p.cost ?: yMin).toDouble())
                    drawCircle(DdTextTertiary, radius = r * 0.8f, center = Offset(cx, cy))
                }
            }
        }
    }
}

/**
 * The x domain for [axis]: the drives' own range, padded out to round steps — 10 km/h on the speed
 * axis, 1 minute on the duration axis — and widened to a minimum window so two near-identical drives
 * don't fill the plot.
 *
 * Extracted and tested because this arithmetic has been wrong three times in three different ways.
 * The invariants it has to hold, all of them learned the hard way:
 *  - **On the grid.** Both ends stay multiples of the step, because the ticks are drawn from this
 *    range; an off-grid domain labels a duration axis 7:30 / 8:15 / 9:00.
 *  - **Never negative.** A drive can't take less than no time, and a negative tick is nonsense.
 *  - **Only ever widened.** Shrinking an end to satisfy the minimum window puts a real drive on the
 *    plot edge, drawn half-clipped.
 *  - **At least the minimum window**, after the zero clamp rather than before it — clamping second
 *    left a single sub-minute drive with a 0..2 window against a minimum of 3.
 */
internal fun scatterXDomain(values: List<Double>, axis: ScatterAxis): Pair<Double, Double> {
    val step = if (axis == ScatterAxis.SPEED) SPEED_STEP else DURATION_STEP
    val minWindow = if (axis == ScatterAxis.SPEED) SPEED_MIN_WINDOW else DURATION_MIN_WINDOW
    var min = floor(((values.minOrNull() ?: step * 4) - step / 2) / step) * step
    var max = ceil(((values.maxOrNull() ?: step * 10) + step / 2) / step) * step
    if (min < 0) min = 0.0
    while (max - min < minWindow) {
        if (min >= step) min -= step
        if (max - min < minWindow) max += step
    }
    return min to max
}

private const val SPEED_STEP = 10.0
private const val DURATION_STEP = 1.0
private const val SPEED_MIN_WINDOW = 20.0
private const val DURATION_MIN_WINDOW = 3.0

/**
 * Axis tick label: whole km/h on the speed axis, a clock on the duration axis.
 *
 * The clock carries seconds below an hour, so a 2.4-minute drive doesn't read as "2", and hours
 * above it — an unbounded minutes field turned a 90-minute route's ticks into "84:00", which reads
 * as hours and minutes at a glance and means something else entirely. [useHours] is decided once for
 * the whole axis rather than per tick, so a route straddling the hour doesn't mix both formats.
 */
internal fun formatAxisValue(value: Double, axis: ScatterAxis, useHours: Boolean): String = when (axis) {
    ScatterAxis.SPEED -> value.roundToInt().toString()
    ScatterAxis.DURATION -> {
        val totalSeconds = (value * 60).roundToInt()
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        if (useHours) {
            String.format(Locale.US, "%d:%02d:%02d", totalSeconds / 3600, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", totalSeconds / 60, seconds)
        }
    }
}

/** The duration axis works in minutes, so this is where its labels switch to h:mm:ss. */
internal const val MINUTES_PER_HOUR = 60.0

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
