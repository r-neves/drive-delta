package app.drivedelta.ui.tracking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.drivedelta.domain.model.TrackingState
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdTextSecondary
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The race-engineer HUD panel (F9). Top block: speed readout + elapsed/distance stats. Bottom block:
 * current road, segment time vs best, and the coloured delta. Road name / segment / best / delta stay
 * at their "no data" placeholders until Roads snapping and live splits arrive (CP7/CP9); the delta
 * then renders green when faster, red when slower, grey when no best is known.
 */
@Composable
fun HudOverlay(
    state: TrackingState,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current

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
            .padding(tokens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
    ) {
        // Speed readout
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.currentSpeedKph.roundToInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = " km/h",
                style = MaterialTheme.typography.titleMedium,
                color = DdTextSecondary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        // Elapsed + distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatBlock(label = "TIME", value = formatElapsed(state.elapsedMs))
            StatBlock(
                label = "DISTANCE",
                value = String.format(Locale.US, "%.1f km", state.distanceMeters / 1000f),
                alignEnd = true,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Segment block
        Text(
            text = state.currentRoadName ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "SEGMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = DdTextTertiary,
                )
                Text(
                    text = formatSegment(state.currentSegmentElapsedMs),
                    style = ddType.numericMono,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            DeltaBadge(bestMs = state.bestSegmentMs, currentMs = state.currentSegmentElapsedMs)
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, alignEnd: Boolean = false) {
    val ddType = LocalDdType.current
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = DdTextTertiary)
        Text(text = value, style = ddType.statValue, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DeltaBadge(bestMs: Long?, currentMs: Long) {
    val ddType = LocalDdType.current
    // No best known yet → grey placeholder. (Live splits arrive in CP9.)
    if (bestMs == null) {
        Text(text = "—", style = ddType.deltaValue, color = DdTextTertiary)
        return
    }
    val deltaMs = currentMs - bestMs
    val faster = deltaMs < 0
    val color = if (faster) DdDeltaFaster else MaterialTheme.colorScheme.error
    val glyph = if (faster) "▾" else "▴"
    val sign = if (faster) "−" else "+"
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "$glyph $sign${formatSegment(kotlin.math.abs(deltaMs))}",
            style = ddType.deltaValue,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "best ${formatSegment(bestMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = DdTextTertiary,
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
