package app.drivedelta.ui.tracking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivedelta.R
import app.drivedelta.domain.model.TrackingState
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.util.Locale

/**
 * Manual-stop confirmation sheet (F6-A) — matches design/mockups/ride-moments-stop-confirm.png. A red
 * stop badge, "Finish this ride?" + a save-to-history subtitle, a bordered Elapsed/Distance/Avg stats
 * card, and Finish (red) / Keep going buttons. [onFinish] fires the manual stop; [onKeepGoing]/
 * [onDismiss] leave the ride running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopConfirmSheet(
    state: TrackingState,
    onFinish: () -> Unit,
    onKeepGoing: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalDdTokens.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.screenPadding)
                .padding(bottom = tokens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
        ) {
            // Red stop badge.
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DdError.copy(alpha = 0.13f))
                    .border(1.dp, DdError.copy(alpha = 0.38f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(DdError))
            }

            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
                Text(
                    text = stringResource(R.string.tracking_stop_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.tracking_stop_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = DdTextTertiary,
                )
            }

            // Bordered stats card with vertical dividers.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusMd))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(tokens.radiusMd))
                    .padding(vertical = tokens.spaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatItem(Modifier.weight(1f), stringResource(R.string.tracking_stat_elapsed), formatElapsedStat(state.elapsedMs), null)
                VerticalDivider(Modifier.height(40.dp), color = MaterialTheme.colorScheme.outline)
                StatItem(Modifier.weight(1f), stringResource(R.string.tracking_stat_distance), String.format(Locale.US, "%.1f", state.distanceMeters / 1000f), "km")
                VerticalDivider(Modifier.height(40.dp), color = MaterialTheme.colorScheme.outline)
                StatItem(Modifier.weight(1f), stringResource(R.string.tracking_stat_avg), avgSpeedKph(state).toString(), "km/h")
            }

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DdError,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.tracking_finish), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onKeepGoing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
            ) {
                Text(stringResource(R.string.tracking_keep_going), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StatItem(modifier: Modifier, label: String, value: String, unit: String?) {
    val ddType = LocalDdType.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = ddType.statValue, color = MaterialTheme.colorScheme.onSurface)
            if (unit != null) {
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = DdTextTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = DdTextTertiary)
    }
}

private fun avgSpeedKph(state: TrackingState): Int {
    val seconds = state.elapsedMs / 1000f
    if (seconds <= 0f) return 0
    return ((state.distanceMeters / seconds) * 3.6f).toInt()
}

private fun formatElapsedStat(ms: Long): String {
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
