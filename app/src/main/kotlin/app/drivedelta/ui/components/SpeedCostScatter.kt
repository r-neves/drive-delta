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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** How a single drive renders on the speed-vs-cost scatter. */
enum class ScatterKind { NORMAL, THIS_DRIVE, THIS_PENDING, FASTEST, CHEAPEST, ESTIMATED }

/**
 * One drive on the speed-vs-cost scatter. [cost] is null only for [ScatterKind.THIS_PENDING] — a
 * drive whose fuel hasn't been logged yet, plotted on the speed axis with no cost.
 */
data class ScatterPoint(
    val speedKph: Float,
    val cost: Float?,
    val kind: ScatterKind,
)

/**
 * Speed (km/h) vs. energy cost scatter with a dashed quadratic trend U-curve — the shared chart behind
 * both the Route Summary and the Trip Detail "Speed vs. cost" sections (design/mockups/trip-summary.png,
 * Energy Logging-saved-drive-not-logged.png). Costed points drive the curve; a not-yet-logged drive is
 * marked on the speed axis with the [pendingLabel]. Money labels use [currencySymbol].
 */
@Composable
fun SpeedCostScatter(
    points: List<ScatterPoint>,
    currencySymbol: String,
    modifier: Modifier = Modifier,
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

    // X domain over every drive (padded to sensible 10-km/h steps, min 20-wide window).
    val speeds = points.map { it.speedKph }
    var xMin = floor(((speeds.minOrNull() ?: 40f) - 5) / 10.0) * 10
    var xMax = ceil(((speeds.maxOrNull() ?: 100f) + 5) / 10.0) * 10
    if (xMax - xMin < 20) { xMin -= 10; xMax += 10 }

    // Y domain over costed drives only; fall back to the design's €2–€6 band when none are costed.
    val costs = points.mapNotNull { it.cost }
    var yMin = if (costs.isEmpty()) 2.0 else floor(costs.min().toDouble())
    var yMax = if (costs.isEmpty()) 6.0 else ceil(costs.max().toDouble())
    if (yMax - yMin < 2) { yMin -= 1; yMax += 1 }
    if (yMin < 0) yMin = 0.0

    val leftPad = with(density) { 34.dp.toPx() }
    val bottomPad = with(density) { 22.dp.toPx() }
    val topPad = with(density) { 18.dp.toPx() }
    val trend = fitQuadratic(points.mapNotNull { p -> p.cost?.let { p.speedKph.toDouble() to it.toDouble() } })

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
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
                currencySymbol + v.roundToInt(), 0f, y + axisPaint.textSize / 3f, axisPaint,
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
            val cx = sx(p.speedKph.toDouble())
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
