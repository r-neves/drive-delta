package app.drivedelta.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.ArrivalStatus
import app.drivedelta.service.TrackingForegroundService
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdTextSecondary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
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
import kotlinx.coroutines.launch

/**
 * Live Tracking screen (F9) — matches design/mockups/tracking-hud-{ahead,behind}.png. Full-screen map
 * with a growing route polyline; a "km left" destination chip and a recenter button float at the top;
 * the telemetry HUD (with its own STOP button) sits at the bottom. A manual STOP opens
 * [StopConfirmSheet]; a geofence arrival opens [ArrivalSheet] (30 s auto-finish). Navigates back via
 * [onFinished] once the service reports the trip ended.
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
    val scope = rememberCoroutineScope()

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
            contentPadding = PaddingValues(bottom = 260.dp),
        ) {
            if (routePoints.size >= 2) {
                Polyline(points = routePoints, color = DdPrimary, width = 14f)
            }
        }

        // Top overlay — destination "km left" chip (left) + recenter button (right).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(tokens.screenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.destinationName != null && state.distanceToDestinationMeters != null) {
                DestinationChip(remainingMeters = state.distanceToDestinationMeters!!)
            } else {
                Spacer(Modifier.size(0.dp))
            }
            RecenterButton(
                onClick = {
                    cameraTarget?.let {
                        scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLng(it)) }
                    }
                },
            )
        }

        // Bottom telemetry HUD (its own STOP button).
        HudOverlay(
            state = state,
            onStop = { showStopConfirm = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        )
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

/** "◆ X.X km left" pill — matches the top-left chip in the mockup. */
@Composable
private fun DestinationChip(remainingMeters: Float) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                RoundedCornerShape(50),
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(horizontal = tokens.spaceLg, vertical = tokens.spaceMd),
    ) {
        // Blue diamond marker.
        Box(Modifier.size(10.dp).graphicsLayer(rotationZ = 45f).background(DdPrimary))
        Spacer(Modifier.width(tokens.spaceMd))
        Text(
            text = stringResource(R.string.tracking_km_left, remainingMeters / 1000f),
            style = ddType.numericMono,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RecenterButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    ) {
        Icon(
            imageVector = Icons.Outlined.MyLocation,
            contentDescription = stringResource(R.string.tracking_recenter),
            tint = DdTextSecondary,
        )
    }
}
