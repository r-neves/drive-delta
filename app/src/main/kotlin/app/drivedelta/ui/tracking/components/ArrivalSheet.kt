package app.drivedelta.ui.tracking.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivedelta.R
import app.drivedelta.ui.theme.DdSuccess
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import kotlinx.coroutines.delay

/**
 * Geofence auto-finish sheet (F6-B) — matches design/mockups/ride-moments-auto-finish.png. A green
 * countdown ring (30 s) over "You've arrived at <place> 🎉" + a depleting progress bar, with a green
 * Finish Ride button and "I'm just passing". Auto-confirms at zero; the countdown [LaunchedEffect] is
 * tied to the sheet's presence, so dismissing it stops the timer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalSheet(
    destinationName: String,
    onFinish: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    var secondsLeft by remember { mutableIntStateOf(AUTO_FINISH_SECONDS) }
    val fraction by animateFloatAsState(
        targetValue = secondsLeft.toFloat() / AUTO_FINISH_SECONDS,
        animationSpec = tween(1_000),
        label = "countdown",
    )

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
        onFinish()
    }

    ModalBottomSheet(
        onDismissRequest = onKeepGoing,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.screenPadding)
                .padding(bottom = tokens.spaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
        ) {
            // Countdown ring.
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp)) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = DdSuccess,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = secondsLeft.toString(),
                        style = ddType.statValue.copy(fontSize = 34.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.tracking_countdown_unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = DdTextTertiary,
                    )
                }
            }

            Text(
                text = stringResource(R.string.tracking_arrived_title, destinationName),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.tracking_arrived_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = DdTextTertiary,
            )

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = DdSuccess,
                trackColor = trackColor,
            )

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DdSuccess,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Text(stringResource(R.string.tracking_finish), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onKeepGoing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
            ) {
                Text(stringResource(R.string.tracking_just_passing), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private const val AUTO_FINISH_SECONDS = 30
private val trackColor = androidx.compose.ui.graphics.Color(0x22FFFFFF)
