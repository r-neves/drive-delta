package app.drivedelta.ui.tracking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 *
 * Under [SHORT_RIDE_MS] the sheet changes its question. A ride that short is almost always Start Ride
 * pressed by mistake, and finishing it silently left a 0.0 km entry in the history that then had to
 * be hunted down and deleted — so the short version asks whether to keep it and offers [onDiscard],
 * which throws the recording away entirely. The default answer is still Keep — it holds the primary
 * button's position — but the *red* moves with the meaning, onto Discard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopConfirmSheet(
    state: TrackingState,
    finishing: Boolean,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onKeepGoing: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalDdTokens.current
    // Latched at open, not recomputed from the live elapsed time. The sheet asks about the ride as
    // it was when the driver decided to stop; leaving it live meant the whole button set could morph
    // under a finger as the clock crossed 30 s, and a discard started at 29.x s lost its own button —
    // spinner, label and all — mid-flight.
    val isShortRide = remember { state.elapsedMs < SHORT_RIDE_MS }
    // Which action the in-flight [finishing] belongs to, so the spinner and the verb land on the
    // button that was actually pressed.
    var discardRequested by remember { mutableStateOf(false) }

    // Fully expanded + scrollable: at the half-expanded height the Finish Ride button can fall
    // inside the navigation-bar strip and become unreachable.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
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
                    text = stringResource(
                        if (isShortRide) R.string.tracking_short_title else R.string.tracking_stop_title,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isShortRide) {
                        stringResource(R.string.tracking_short_subtitle, formatElapsedStat(state.elapsedMs))
                    } else {
                        stringResource(R.string.tracking_stop_subtitle)
                    },
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

            // Red means "this ends/destroys the ride". On a normal ride that is Finish, and it keeps
            // the destructive styling. On a short ride the primary button *keeps* the recording, so
            // it drops to the neutral primary and the red moves to Discard — otherwise a driver who
            // has learned "the red button is the one that ends this" would read the colour backwards
            // at exactly the moment the two actions stop being the same thing.
            Button(
                onClick = onFinish,
                enabled = !finishing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
                colors = if (isShortRide) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = DdError,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                },
            ) {
                if (finishing && !discardRequested) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = if (isShortRide) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onError
                        },
                    )
                    Spacer(Modifier.size(tokens.spaceMd))
                }
                Text(
                    stringResource(
                        when {
                            finishing && !discardRequested -> R.string.tracking_finishing
                            isShortRide -> R.string.tracking_keep
                            else -> R.string.tracking_finish
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isShortRide) {
                OutlinedButton(
                    onClick = { discardRequested = true; onDiscard() },
                    enabled = !finishing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(tokens.radiusMd),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DdError),
                ) {
                    if (finishing && discardRequested) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = DdError,
                        )
                        Spacer(Modifier.size(tokens.spaceMd))
                    }
                    Text(
                        stringResource(
                            if (discardRequested) R.string.tracking_discarding else R.string.tracking_discard,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            OutlinedButton(
                onClick = onKeepGoing,
                enabled = !finishing,
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

/**
 * Below this a ride is treated as a mis-tap rather than a drive. Thirty seconds: long enough that a
 * genuine short hop (moving the car off a driveway) is never questioned, short enough that pressing
 * Start Ride and immediately pressing Stop always is.
 */
private const val SHORT_RIDE_MS = 30_000L

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
