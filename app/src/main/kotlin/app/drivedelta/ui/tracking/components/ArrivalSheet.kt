package app.drivedelta.ui.tracking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.drivedelta.R
import app.drivedelta.ui.theme.DdTextSecondary
import app.drivedelta.ui.theme.LocalDdTokens
import kotlinx.coroutines.delay

/**
 * Geofence auto-finish sheet (F6-B). Counts down from 30 s and auto-confirms the finish when it
 * reaches zero; the driver can finish immediately or tap "I'm just passing" to keep going. The
 * countdown runs in a [LaunchedEffect] tied to the sheet's presence, so dismissing it stops the timer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalSheet(
    destinationName: String,
    onFinish: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val tokens = LocalDdTokens.current
    var secondsLeft by remember { mutableIntStateOf(AUTO_FINISH_SECONDS) }

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
            verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
        ) {
            Text(
                text = stringResource(R.string.tracking_arrived_title, destinationName),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.tracking_arrived_countdown, secondsLeft),
                style = MaterialTheme.typography.bodyLarge,
                color = DdTextSecondary,
            )

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.tracking_finish), style = MaterialTheme.typography.labelLarge)
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
