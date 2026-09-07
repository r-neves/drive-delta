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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.ArrivalStatus
import app.drivedelta.domain.model.Place
import app.drivedelta.service.TrackingForegroundService
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdRouteCasing
import app.drivedelta.ui.theme.DdSuccess
import app.drivedelta.ui.theme.DdTextSecondary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import app.drivedelta.ui.components.rememberMapDot
import app.drivedelta.ui.components.rememberMapPin
import app.drivedelta.ui.tracking.components.ArrivalSheet
import app.drivedelta.ui.tracking.components.HudOverlay
import app.drivedelta.ui.tracking.components.StopConfirmSheet
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.MarkerState
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
    onFinished: (tripId: String?) -> Unit,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val tokens = LocalDdTokens.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routePoints by viewModel.routePoints.collectAsStateWithLifecycle()
    val cameraTarget by viewModel.cameraTarget.collectAsStateWithLifecycle()
    val cameraBearing by viewModel.cameraBearing.collectAsStateWithLifecycle()
    val finishedTripId by viewModel.finishedTripId.collectAsStateWithLifecycle()
    val finishing by viewModel.finishing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(finishedTripId) {
        finishedTripId?.let { onFinished(it.ifEmpty { null }) }
    }

    var showStopConfirm by remember { mutableStateOf(false) }
    // Local "I'm just passing" dismissal; reset once the driver leaves the geofence (EN_ROUTE).
    var passingDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(state.arrivalStatus) {
        if (state.arrivalStatus == ArrivalStatus.EN_ROUTE) passingDismissed = false
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), FOLLOW_ZOOM)
    }
    // Heading-up follow: animate target *and* bearing together so the map turns with the car. The
    // animation is stretched over the ~3 s between camera updates, so the rotation reads as a smooth
    // sweep rather than a snap. Maps takes the shortest way round, so 350° → 10° doesn't spin back.
    LaunchedEffect(cameraTarget, cameraBearing) {
        val target = cameraTarget ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(FOLLOW_ZOOM)
                    .bearing(cameraBearing)
                    .build(),
            ),
            durationMs = CAMERA_ANIMATION_MS,
        )
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
                // Two-layer trace, as in design/mockups/tracking-hud-ahead.png: a dark casing under
                // the blue stroke so the route stays readable over light map features.
                Polyline(points = routePoints, color = DdRouteCasing, width = 22f)
                Polyline(points = routePoints, color = DdPrimary, width = 14f)
            }
            // The finish area, drawn at its real geofence radius: the driver can see the circle
            // coming rather than watching a number shrink and guessing where it hits zero. Its own
            // icon rides on top, and the origin's does too, so both ends of the ride are on the map
            // with the icons the user picked for those places.
            state.destinationPlace?.let { DestinationGeofence(it, state.arrivalStatus) }
            state.originPlace?.let { PlacePin(it, DdPrimary) }
            state.currentLocation?.let { LocationPuck(it) }
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
                    // Restore heading-up follow, not just the position, so panning away is undoable.
                    val target = cameraTarget ?: return@RecenterButton
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(target)
                                    .zoom(FOLLOW_ZOOM)
                                    .bearing(cameraBearing)
                                    .build(),
                            ),
                        )
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
            finishing = finishing,
            onFinish = { viewModel.stop(TrackingForegroundService.TRIGGER_MANUAL) },
            onDiscard = { viewModel.discard() },
            onKeepGoing = { showStopConfirm = false },
            onDismiss = { if (!finishing) showStopConfirm = false },
        )
    }

    // Stays up while `finishing` so the sheet can show progress instead of vanishing into a frozen
    // map; the LaunchedEffect above navigates away once the service confirms the trip is finalised.
    if (state.arrivalStatus == ArrivalStatus.ARRIVED && !passingDismissed && !showStopConfirm) {
        ArrivalSheet(
            destinationName = state.destinationName ?: "",
            finishing = finishing,
            onFinish = { viewModel.stop(TrackingForegroundService.TRIGGER_GEOFENCE) },
            onKeepGoing = { passingDismissed = true },
        )
    }
}

/**
 * The driver's position at the head of the trace — the blue dot with a dark ring and a soft glow in
 * design/mockups/tracking-hud-ahead.png, which the live map was missing entirely.
 *
 * Drawn from the tracking service's own fixes rather than `MapProperties.isMyLocationEnabled`: the
 * built-in blue dot runs a second, independent location request, which is wasted battery on a screen
 * that already has a high-accuracy stream, and it can't be styled to the brand.
 *
 * The translucent circle is the real GPS accuracy radius, so a poor fix is visible rather than
 * implied by a dot that always looks equally confident.
 */
@Composable
private fun LocationPuck(location: android.location.Location) {
    val position = LatLng(location.latitude, location.longitude)
    val puck = rememberMapDot(DdPrimary, diameter = PUCK_DIAMETER)
    // maps-compose 4.4.1 has no rememberUpdatedMarkerState, and rememberMarkerState treats its
    // position argument as an initial value only, so the state has to be pushed each fix.
    val markerState = remember { MarkerState(position) }
    LaunchedEffect(position) { markerState.position = position }

    if (location.hasAccuracy() && location.accuracy > 0f) {
        Circle(
            center = position,
            radius = location.accuracy.toDouble(),
            fillColor = DdPrimary.copy(alpha = 0.12f),
            strokeColor = DdPrimary.copy(alpha = 0.35f),
            strokeWidth = 2f,
        )
    }
    Marker(
        state = markerState,
        icon = puck,
        anchor = Offset(0.5f, 0.5f),
        flat = true,          // stays put when the map rotates, instead of counter-rotating
        zIndex = 2f,
    )
}

/**
 * The destination's geofence, drawn as it actually is: a circle at the saved place's own radius.
 * It brightens once the arrival detector says the driver is inside, which is the moment the ride is
 * about to auto-finish — worth seeing before the sheet appears rather than at the same time.
 */
@Composable
private fun DestinationGeofence(place: Place, arrivalStatus: ArrivalStatus) {
    val inside = arrivalStatus != ArrivalStatus.EN_ROUTE
    Circle(
        center = LatLng(place.lat, place.lng),
        radius = place.radiusMeters.toDouble(),
        fillColor = DdSuccess.copy(alpha = if (inside) 0.28f else 0.14f),
        strokeColor = DdSuccess.copy(alpha = if (inside) 1f else 0.75f),
        strokeWidth = 5f,
    )
    PlacePin(place, DdSuccess)
}

/** A saved place on the live map, wearing the emoji chosen for it in the place editor. */
@Composable
private fun PlacePin(place: Place, accent: androidx.compose.ui.graphics.Color) {
    val position = LatLng(place.lat, place.lng)
    val icon = rememberMapPin(place.iconEmoji, accent)
    val markerState = remember(place.id) { MarkerState(position) }
    LaunchedEffect(position) { markerState.position = position }
    Marker(
        state = markerState,
        icon = icon,
        anchor = Offset(0.5f, 1f),
        title = place.name,
        zIndex = 1f,
    )
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

/** Follow-mode zoom: close enough to read the road you're on. */
private val FOLLOW_ZOOM = 17f

/** Matches the ~3 s camera update throttle so rotation sweeps instead of snapping. */
private const val CAMERA_ANIMATION_MS = 2_500

private const val PUCK_DIAMETER = 46f
