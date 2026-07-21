package app.drivedelta.ui.tracking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.drivedelta.R
import app.drivedelta.domain.model.TrackingState
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.util.Locale

/**
 * Manual-stop confirmation sheet (F6-A): current stats + Finish (red) / Keep going. [onFinish] fires
 * the manual stop; [onKeepGoing]/[onDismiss] leave the ride running.
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
            Text(
                text = stringResource(R.string.tracking_stop_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(stringResource(R.string.tracking_stat_time), formatElapsedStat(state.elapsedMs))
                StatItem(
                    stringResource(R.string.tracking_stat_distance),
                    String.format(Locale.US, "%.1f km", state.distanceMeters / 1000f),
                )
                StatItem(
                    stringResource(R.string.tracking_stat_avg_speed),
                    String.format(Locale.US, "%d km/h", avgSpeedKph(state)),
                )
            }

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.tracking_finish), style = MaterialTheme.typography.labelLarge)
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
private fun StatItem(label: String, value: String) {
    val ddType = LocalDdType.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = ddType.statValue, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = DdTextTertiary)
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
