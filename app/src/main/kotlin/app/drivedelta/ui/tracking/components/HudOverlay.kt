package app.drivedelta.ui.tracking.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivedelta.R
import app.drivedelta.domain.model.TrackingState
import app.drivedelta.ui.theme.DdAmber
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdPurpleSector
import app.drivedelta.ui.theme.DdTextSecondary
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The race-engineer HUD panel (F9) — matches design/mockups/tracking-hud-{ahead,behind}.png. A glass
 * panel pinned to the bottom of the map. Header: a RECORDING pulse (SEARCHING while acquiring GPS) +
 * the current road. Main block: the big speed readout (left) beside the segment time, AHEAD/BEHIND
 * BEST status and coloured seconds delta (right). Footer: ELAPSED / DISTANCE stats and the STOP
 * button. The segment status / best / delta only render once a best time is known — live splits are
 * deferred by design (bestSegmentMs stays null), so the delta area is quietly omitted until then.
 */
@Composable
fun HudOverlay(
    state: TrackingState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    val acquiring = state.isTracking && state.currentLocation == null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                RoundedCornerShape(tokens.radiusHudPanel),
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(tokens.radiusHudPanel),
            )
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
    ) {
        // --- Header: recording/searching status + current road ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordingIndicator(acquiring = acquiring)
            Text(
                text = (state.currentRoadName ?: "—").uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = DdTextSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = tokens.spaceMd),
            )
        }

        // --- Main: speed (left) beside segment time + delta (right) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (acquiring) "--" else state.currentSpeedKph.roundToInt().toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.tracking_unit_kmh),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                    color = DdTextSecondary,
                    modifier = Modifier.padding(top = tokens.spaceXs),
                )
            }
            SegmentBlock(state = state, acquiring = acquiring)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // --- Footer: elapsed / distance stats + STOP ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spaceXl)) {
                StatBlock(
                    label = stringResource(R.string.tracking_label_elapsed),
                    value = if (acquiring) "--:--" else formatElapsed(state.elapsedMs),
                )
                StatBlock(
                    label = stringResource(R.string.tracking_label_distance),
                    value = String.format(Locale.US, "%.1f km", state.distanceMeters / 1000f),
                )
            }
            StopButton(onStop = onStop)
        }
    }
}

@Composable
private fun RecordingIndicator(acquiring: Boolean) {
    val tokens = LocalDdTokens.current
    val transition = rememberInfiniteTransition(label = "recording")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val dotColor = if (acquiring) DdAmber else DdError
    val label = if (acquiring) R.string.tracking_searching else R.string.tracking_recording
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .alpha(pulse)
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(tokens.spaceSm))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = DdTextSecondary,
        )
    }
}

@Composable
private fun SegmentBlock(state: TrackingState, acquiring: Boolean) {
    val ddType = LocalDdType.current
    val bestMs = state.bestSegmentMs
    Column(horizontalAlignment = Alignment.End) {
        if (bestMs != null) {
            val deltaMs = state.currentSegmentElapsedMs - bestMs
            val faster = deltaMs < 0
            val statusColor = if (faster) DdDeltaFaster else DdError
            Text(
                text = stringResource(
                    if (faster) R.string.tracking_ahead_of_best else R.string.tracking_behind_best,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
        }
        Text(
            text = if (acquiring) "--:--" else formatSegment(state.currentSegmentElapsedMs),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (bestMs != null) {
            val deltaMs = state.currentSegmentElapsedMs - bestMs
            val faster = deltaMs < 0
            val statusColor = if (faster) DdDeltaFaster else DdError
            val glyph = if (faster) "▾" else "▴"
            val sign = if (faster) "−" else "+"
            Text(
                text = "$glyph $sign${formatDeltaSeconds(abs(deltaMs))}",
                style = ddType.deltaValue,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tracking_best_caption, formatSegment(bestMs)),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                color = DdTextTertiary,
            )
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    val ddType = LocalDdType.current
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = DdTextTertiary)
        Text(text = value, style = ddType.statValue, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** HUD STOP button — bordered red pill with a square glyph (design/tokens.md §7). */
@Composable
private fun StopButton(onStop: () -> Unit) {
    val tokens = LocalDdTokens.current
    Row(
        modifier = Modifier
            .width(120.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(tokens.radiusMd))
            .background(DdError.copy(alpha = 0.13f))
            .border(1.dp, DdError.copy(alpha = 0.38f), RoundedCornerShape(tokens.radiusMd))
            .clickable(onClick = onStop),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(DdError))
        Spacer(Modifier.width(tokens.spaceSm))
        Text(
            text = stringResource(R.string.tracking_stop),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
            color = Color(0xFFFF7A88),
        )
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

private fun formatSegment(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val tenths = (ms % 1000) / 100
    return String.format(Locale.US, "%d:%02d.%d", m, s, tenths)
}

private fun formatDeltaSeconds(ms: Long): String =
    String.format(Locale.US, "%.1fs", ms / 1000f)
