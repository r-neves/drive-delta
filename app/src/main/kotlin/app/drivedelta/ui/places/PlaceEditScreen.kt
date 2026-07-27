package app.drivedelta.ui.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdSurface
import app.drivedelta.ui.theme.DdSurfaceSheet
import app.drivedelta.ui.theme.LocalDdTokens
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place as PlacesPlace
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

/** Emoji options for the picker (design/F3). */
private val EMOJI_OPTIONS = listOf(
    "🏠", "🏢", "🏋️", "⛽", "🛒", "🏖️", "🏫", "🏥", "⚽", "🎯",
    "🍕", "🏨", "🚉", "✈️", "🏕️", "🏪", "🎭", "🎮", "🌳", "🏟️",
)

private const val COORD_EPS = 1e-6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditScreen(
    onDone: () -> Unit,
    viewModel: PlaceEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = LocalDdTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    // Address autocomplete lives at the screen level so the search field can sit in the top bar
    // (design/mockups/place-edit.png). PlacesClient only exists once the SDK is initialised.
    val placesClient = remember { if (Places.isInitialized()) Places.createClient(context) else null }
    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<PredictionItem>>(emptyList()) }
    LaunchedEffect(query) {
        val client = placesClient
        if (client == null || query.length < 3) { predictions = emptyList(); return@LaunchedEffect }
        delay(300)
        val request = FindAutocompletePredictionsRequest.builder().setQuery(query).build()
        predictions = runCatching {
            client.findAutocompletePredictions(request).await().autocompletePredictions.map {
                PredictionItem(it.placeId, it.getPrimaryText(null).toString(), it.getFullText(null).toString())
            }
        }.getOrDefault(emptyList())
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Top bar: back + inline search field.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.screenPadding, vertical = tokens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
            ) {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = placesClient != null,
                    placeholder = { Text(stringResource(R.string.place_field_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    shape = RoundedCornerShape(tokens.radiusInput),
                    colors = fieldColors(),
                )
            }

            // Address predictions (shown while typing).
            predictions.forEach { item ->
                Text(
                    item.full,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val client = placesClient ?: return@clickable
                            scope.launch {
                                val fields = listOf(PlacesPlace.Field.LAT_LNG, PlacesPlace.Field.ADDRESS)
                                runCatching {
                                    val place = client.fetchPlace(FetchPlaceRequest.builder(item.placeId, fields).build()).await().place
                                    place.latLng?.let { viewModel.onLocationPicked(it.latitude, it.longitude, place.address ?: item.full) }
                                }
                                query = ""
                                predictions = emptyList()
                            }
                        }
                        .padding(horizontal = tokens.screenPadding, vertical = tokens.spaceSm),
                )
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Map hero + "Use my location" overlay.
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    PlaceMap(
                        lat = state.lat,
                        lng = state.lng,
                        radiusMeters = state.radiusMeters,
                        recenterSignal = state.recenterSignal,
                        onMarkerMoved = viewModel::onMarkerMoved,
                    )
                    UseMyLocationButton(
                        onLocation = viewModel::onUseMyLocation,
                        modifier = Modifier.align(Alignment.BottomStart).padding(tokens.spaceLg),
                    )
                }

                // Bottom panel.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(DdSurfaceSheet, RoundedCornerShape(topStart = tokens.radiusLg, topEnd = tokens.radiusLg))
                        .padding(horizontal = tokens.screenPadding, vertical = tokens.spaceXl),
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
                ) {
                    LabeledSection(stringResource(R.string.place_label_name)) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::onNameChange,
                            singleLine = true,
                            isError = state.nameError,
                            supportingText = if (state.nameError) ({ Text(stringResource(R.string.place_error_name_required)) }) else null,
                            shape = RoundedCornerShape(tokens.radiusInput),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    LabeledSection(stringResource(R.string.place_label_icon)) {
                        EmojiPicker(selected = state.iconEmoji, onSelect = viewModel::onEmojiChange)
                    }

                    RadiusControl(radiusMeters = state.radiusMeters, onChange = viewModel::onRadiusChange)

                    Button(
                        onClick = viewModel::save,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(tokens.radiusMd),
                    ) {
                        Text(stringResource(R.string.place_edit_save), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSection(label: String, content: @Composable () -> Unit) {
    val tokens = LocalDdTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    val tokens = LocalDdTokens.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        items(EMOJI_OPTIONS) { emoji ->
            val isSelected = emoji == selected
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (isSelected) DdPrimary.copy(alpha = 0.18f) else DdSurface,
                        RoundedCornerShape(tokens.radiusMd),
                    )
                    .border(
                        1.dp,
                        if (isSelected) DdPrimary else DdOutline,
                        RoundedCornerShape(tokens.radiusMd),
                    )
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun PlaceMap(
    lat: Double,
    lng: Double,
    radiusMeters: Float,
    recenterSignal: Int,
    onMarkerMoved: (Double, Double) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val markerState = rememberMarkerState(position = LatLng(lat, lng))
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 15f)
    }

    LaunchedEffect(recenterSignal) {
        val target = LatLng(lat, lng)
        markerState.position = target
        cameraPositionState.animate(CameraUpdateFactory.newLatLng(target))
    }

    LaunchedEffect(markerState) {
        snapshotFlow { markerState.position }.collect { pos ->
            if (abs(pos.latitude - lat) > COORD_EPS || abs(pos.longitude - lng) > COORD_EPS) {
                onMarkerMoved(pos.latitude, pos.longitude)
            }
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
    ) {
        Marker(state = markerState, draggable = true)
        Circle(
            center = LatLng(lat, lng),
            radius = radiusMeters.toDouble(),
            strokeColor = primary,
            strokeWidth = 3f,
            fillColor = primary.copy(alpha = 0.15f),
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun UseMyLocationButton(onLocation: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun fetchLocation() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        scope.launch {
            runCatching { client.lastLocation.await() }.getOrNull()?.let { onLocation(it.latitude, it.longitude) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchLocation()
    }

    OutlinedButton(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (granted) fetchLocation() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        modifier = modifier,
        shape = CircleShape,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = DdSurface),
    ) {
        Icon(Icons.Filled.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.place_use_my_location), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RadiusControl(radiusMeters: Float, onChange: (Float) -> Unit) {
    val tokens = LocalDdTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.place_label_radius), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.places_radius_badge, radiusMeters.toInt()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        // 50m..500m in 25m steps → 17 interior steps.
        Slider(value = radiusMeters, onValueChange = onChange, valueRange = 50f..500f, steps = 17)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DdSurface,
    unfocusedContainerColor = DdSurface,
    disabledContainerColor = DdSurface,
    errorContainerColor = DdSurface,
    unfocusedBorderColor = DdOutline,
    focusedBorderColor = DdPrimary,
)

private data class PredictionItem(val placeId: String, val primary: String, val full: String)
