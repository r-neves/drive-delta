package app.drivedelta.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.drivedelta.R
import app.drivedelta.domain.model.FuelType
import app.drivedelta.ui.cars.badgeColor
import app.drivedelta.ui.cars.labelRes
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPurpleRowBg
import app.drivedelta.ui.theme.DdPurpleRowBorder
import app.drivedelta.ui.theme.DdPurpleSector
import app.drivedelta.ui.theme.DdSurface
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

/**
 * A rich trip card used on both the Trips (Recent) and Dashboard screens (see design/mockups/
 * trips-recent.png, dashboard.png): time · fuel-type icon + car (or a NEW PB pill), `Origin →
 * Destination`, `duration · distance`, and a coloured delta (or "★ best" for a personal best).
 *
 * [deltaMs] is whatever baseline the caller chose (Trips uses "vs previous drive", the Dashboard uses
 * "vs best"); [deltaSuffix] annotates it (e.g. " vs best"). When [isNewPb] the card is purple and the
 * delta is replaced by "★ best". [timeLabelPrefix] lets the Dashboard prepend "Today "/"Yesterday ".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentTripCard(
    startTime: Long,
    originName: String?,
    destName: String?,
    carName: String?,
    fuelType: FuelType?,
    durationMs: Long,
    distanceMeters: Float,
    isNewPb: Boolean,
    deltaMs: Long?,
    modifier: Modifier = Modifier,
    deltaSuffix: String = "",
    timeLabelPrefix: String? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusCard))
            .background(if (isNewPb) DdPurpleRowBg else DdSurface)
            .border(1.dp, if (isNewPb) DdPurpleRowBorder else DdOutline, RoundedCornerShape(tokens.radiusCard))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(tokens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val time = TIME_FORMAT.format(Date(startTime))
            Text(
                if (timeLabelPrefix != null) "$timeLabelPrefix · $time" else time,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isNewPb) {
                NewPbPill()
            } else if (carName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
                    FuelIcon(fuelType)
                    Text(carName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(
            routeTitle(originName, destName, stringResource(R.string.trips_drive_fallback)),
            style = MaterialTheme.typography.headlineMedium,
            color = if (isNewPb) DdPurpleSector else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatClock(durationMs)}   ${String.format(Locale.US, "%.1f", distanceMeters / 1000f)} km",
                style = ddType.numericMono,
                color = if (isNewPb) DdPurpleSector else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            when {
                isNewPb -> Text("★ best", style = ddType.numericMono, color = DdPurpleSector, fontWeight = FontWeight.SemiBold)
                deltaMs != null && deltaMs != 0L -> {
                    val faster = deltaMs < 0
                    Text(
                        (if (faster) "▾ " else "▴ ") + formatClock(abs(deltaMs)) + deltaSuffix,
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
    // The fuel type is conveyed only by this icon (no adjacent label), so name it for screen readers.
    Icon(icon, contentDescription = stringResource(fuelType.labelRes), tint = fuelType.badgeColor, modifier = Modifier.size(18.dp))
}

/** "Home → Office", or [fallback] when the trip has no linked places. */
fun routeTitle(origin: String?, dest: String?, fallback: String): String = when {
    origin != null || dest != null -> "${origin ?: "—"} → ${dest ?: "—"}"
    else -> fallback
}

/** m:ss (or h:mm:ss for long drives). */
fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
