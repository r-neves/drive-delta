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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
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

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val title = if (state.isEditing) {
        stringResource(R.string.place_edit_title_edit)
    } else {
        stringResource(R.string.place_edit_title_new)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.screenPadding, vertical = tokens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.place_field_name)) },
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.place_error_name_required)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(tokens.radiusInput),
                modifier = Modifier.fillMaxWidth(),
            )

            EmojiPicker(selected = state.iconEmoji, onSelect = viewModel::onEmojiChange)

            AddressSearchBar(
                onPicked = { lat, lng, address -> viewModel.onLocationPicked(lat, lng, address) },
            )

            PlaceMap(
                lat = state.lat,
                lng = state.lng,
                radiusMeters = state.radiusMeters,
                recenterSignal = state.recenterSignal,
                onMarkerMoved = viewModel::onMarkerMoved,
            )

            UseMyLocationButton(onLocation = viewModel::onUseMyLocation)

            if (state.address.isNotBlank()) {
                Text(
                    text = state.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RadiusControl(radiusMeters = state.radiusMeters, onChange = viewModel::onRadiusChange)

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
            ) {
                Text(stringResource(R.string.action_save), style = MaterialTheme.typography.labelLarge)
            }
        }
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
                    .size(48.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surface,
                        CircleShape,
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    )
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSearchBar(onPicked: (Double, Double, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = LocalDdTokens.current

    // PlacesClient only exists once the SDK is initialised (i.e. a PLACES_API_KEY is present).
    val placesClient = remember {
        if (Places.isInitialized()) Places.createClient(context) else null
    }
    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<PredictionItem>>(emptyList()) }

    // Debounced autocomplete lookups.
    LaunchedEffect(query) {
        val client = placesClient
        if (client == null || query.length < 3) {
            predictions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        val request = FindAutocompletePredictionsRequest.builder().setQuery(query).build()
        predictions = runCatching {
            client.findAutocompletePredictions(request).await().autocompletePredictions.map {
                PredictionItem(
                    placeId = it.placeId,
                    primary = it.getPrimaryText(null).toString(),
                    full = it.getFullText(null).toString(),
                )
            }
        }.getOrDefault(emptyList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.place_field_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            enabled = placesClient != null,
            supportingText = if (placesClient == null) {
                { Text(stringResource(R.string.place_search_unavailable)) }
            } else {
                null
            },
            shape = RoundedCornerShape(tokens.radiusInput),
            modifier = Modifier.fillMaxWidth(),
        )
        predictions.forEach { item ->
            Text(
                text = item.full,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val client = placesClient ?: return@clickable
                        scope.launch {
                            val fields = listOf(PlacesPlace.Field.LAT_LNG, PlacesPlace.Field.ADDRESS)
                            runCatching {
                                val place = client
                                    .fetchPlace(FetchPlaceRequest.builder(item.placeId, fields).build())
                                    .await().place
                                val ll = place.latLng
                                if (ll != null) {
                                    onPicked(ll.latitude, ll.longitude, place.address ?: item.full)
                                }
                            }
                            query = ""
                            predictions = emptyList()
                        }
                    }
                    .padding(vertical = tokens.spaceSm),
            )
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
    val tokens = LocalDdTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val markerState = rememberMarkerState(position = LatLng(lat, lng))
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 15f)
    }

    // Programmatic moves (autocomplete / use-my-location / load) recenter the marker + camera.
    LaunchedEffect(recenterSignal) {
        val target = LatLng(lat, lng)
        markerState.position = target
        cameraPositionState.animate(CameraUpdateFactory.newLatLng(target))
    }

    // Marker drags flow back to the ViewModel. Skip echoes of a programmatic set (position already
    // equals the VM's lat/lng) so a picked address isn't overwritten by a reverse-geocode.
    LaunchedEffect(markerState) {
        snapshotFlow { markerState.position }.collect { pos ->
            if (abs(pos.latitude - lat) > COORD_EPS || abs(pos.longitude - lng) > COORD_EPS) {
                onMarkerMoved(pos.latitude, pos.longitude)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(tokens.radiusCard)),
    ) {
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
}

@SuppressLint("MissingPermission")
@Composable
private fun UseMyLocationButton(onLocation: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun fetchLocation() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        scope.launch {
            runCatching { client.lastLocation.await() }.getOrNull()?.let {
                onLocation(it.latitude, it.longitude)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) fetchLocation() }

    OutlinedButton(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) fetchLocation() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.MyLocation, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.place_use_my_location))
    }
}

@Composable
private fun RadiusControl(radiusMeters: Float, onChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.place_field_radius),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.places_radius_badge, radiusMeters.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // 50m..500m in 25m steps → 18 steps between the 19 stops (steps = stops - 2 = 17 interior).
        Slider(
            value = radiusMeters,
            onValueChange = onChange,
            valueRange = 50f..500f,
            steps = 17,
        )
    }
}

private data class PredictionItem(val placeId: String, val primary: String, val full: String)
