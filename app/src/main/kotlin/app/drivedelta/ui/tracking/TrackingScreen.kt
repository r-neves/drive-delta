package app.drivedelta.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.ArrivalStatus
import app.drivedelta.service.TrackingForegroundService
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.tracking.components.ArrivalSheet
import app.drivedelta.ui.tracking.components.HudOverlay
import app.drivedelta.ui.tracking.components.StopConfirmSheet
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Live Tracking screen (F9). Full-screen map with a growing route polyline, the telemetry HUD, an
 * optional destination chip, and a red STOP button. A manual STOP opens [StopConfirmSheet]; a
 * geofence arrival opens [ArrivalSheet] (30 s auto-finish). Navigates back via [onFinished] once the
 * service reports the trip ended.
 */
@Composable
fun TrackingScreen(
    onFinished: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val tokens = LocalDdTokens.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routePoints by viewModel.routePoints.collectAsStateWithLifecycle()
    val cameraTarget by viewModel.cameraTarget.collectAsStateWithLifecycle()
    val tripEnded by viewModel.tripEnded.collectAsStateWithLifecycle()

    LaunchedEffect(tripEnded) { if (tripEnded) onFinished() }

    var showStopConfirm by remember { mutableStateOf(false) }
    // Local "I'm just passing" dismissal; reset once the driver leaves the geofence (EN_ROUTE).
    var passingDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(state.arrivalStatus) {
        if (state.arrivalStatus == ArrivalStatus.EN_ROUTE) passingDismissed = false
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 16f)
    }
    LaunchedEffect(cameraTarget) {
        cameraTarget?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLng(it)) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = false),
            contentPadding = PaddingValues(bottom = 320.dp),
        ) {
            if (routePoints.size >= 2) {
                Polyline(points = routePoints, color = DdPrimary, width = 14f)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(tokens.screenPadding),
        ) {
            HudOverlay(state = state)
            state.destinationName?.let { name ->
                Spacer(Modifier.height(tokens.spaceSm))
                DestinationChip(name = name, remainingMeters = state.distanceToDestinationMeters)
            }
        }

        Button(
            onClick = { showStopConfirm = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = tokens.spaceXl)
                .fillMaxWidth(0.6f)
                .height(56.dp),
            shape = RoundedCornerShape(tokens.radiusMd),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(stringResource(R.string.tracking_stop), style = MaterialTheme.typography.labelLarge)
        }
    }

    if (showStopConfirm) {
        StopConfirmSheet(
            state = state,
            onFinish = {
                showStopConfirm = false
                viewModel.stop(TrackingForegroundService.TRIGGER_MANUAL)
            },
            onKeepGoing = { showStopConfirm = false },
            onDismiss = { showStopConfirm = false },
        )
    }

    if (state.arrivalStatus == ArrivalStatus.ARRIVED && !passingDismissed && !showStopConfirm) {
        ArrivalSheet(
            destinationName = state.destinationName ?: "",
            onFinish = { viewModel.stop(TrackingForegroundService.TRIGGER_GEOFENCE) },
            onKeepGoing = { passingDismissed = true },
        )
    }
}

@Composable
private fun DestinationChip(name: String, remainingMeters: Float?) {
    val tokens = LocalDdTokens.current
    val label = if (remainingMeters != null) {
        stringResource(R.string.tracking_destination_chip, name, remainingMeters / 1000f)
    } else {
        name
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                RoundedCornerShape(tokens.radiusSm),
            )
            .padding(horizontal = tokens.spaceMd, vertical = tokens.spaceSm),
    )
}
