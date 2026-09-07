package app.drivedelta.ui.tracking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.Place
import app.drivedelta.ui.tracking.PreRideViewModel

/**
 * Pre-ride setup sheet (F5): car selector (pre-selects the default), origin/destination place
 * dropdowns and Start Ride. Requires at least one car — with none, it shows a prompt to add one. On
 * start it calls the ViewModel, which creates the trip + launches the service and emits the trip id
 * via [onStarted].
 *
 * The origin fills itself in from the saved place the driver is standing in, and says so. It used to
 * be offered as a chip that had to be tapped, which is a question with only one sensible answer —
 * you are where you are. It stays a plain dropdown, so changing it costs the same as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreRideSheet(
    onDismiss: () -> Unit,
    onStarted: () -> Unit,
    viewModel: PreRideViewModel = hiltViewModel(),
) {
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val nearbyPlace by viewModel.nearbyPlace.collectAsStateWithLifecycle()
    val startedTripId by viewModel.startedTripId.collectAsStateWithLifecycle()

    // Consume the event: the ViewModel is scoped to the Dashboard, not to this sheet, so an
    // unconsumed id would replay on the next open and skip straight to tracking with no ride.
    LaunchedEffect(startedTripId) {
        if (startedTripId != null) {
            onStarted()
            viewModel.onStartHandled()
        }
    }

    // Re-detect on every open: the ViewModel is scoped to the Dashboard, so without this the sheet
    // would show wherever the driver happened to be the first time it was opened.
    LaunchedEffect(Unit) { viewModel.refreshNearbyPlace() }

    var selectedCar by remember(cars) { mutableStateOf(cars.firstOrNull { it.isDefault } ?: cars.firstOrNull()) }
    var origin by remember { mutableStateOf<Place?>(null) }
    var destination by remember { mutableStateOf<Place?>(null) }
    // Detection can land after the sheet is already up, so the fill is an effect rather than an
    // initial value — but it stops the moment the driver expresses an opinion, including "none".
    var originChosen by remember { mutableStateOf(false) }
    LaunchedEffect(nearbyPlace) { if (!originChosen && nearbyPlace != null) origin = nearbyPlace }

    // Open fully expanded and let the content scroll: at the half-expanded height the Start Ride
    // button falls below the screen edge, inside the navigation-bar strip, and is unreachable.
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.preride_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (cars.isEmpty()) {
                Text(
                    text = stringResource(R.string.preride_no_cars),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            CarDropdown(cars = cars, selected = selectedCar, onSelect = { selectedCar = it })

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlaceDropdown(
                    label = stringResource(R.string.preride_origin),
                    places = places,
                    selected = origin,
                    onSelect = { origin = it; originChosen = true },
                )
                if (!originChosen && origin != null && origin == nearbyPlace) {
                    Text(
                        stringResource(R.string.preride_origin_detected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PlaceDropdown(
                label = stringResource(R.string.preride_destination),
                places = places,
                selected = destination,
                onSelect = { destination = it },
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.preride_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    viewModel.startRide(
                        carId = selectedCar?.id,
                        originPlaceId = origin?.id,
                        destinationPlaceId = destination?.id,
                    )
                },
                enabled = selectedCar != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.preride_start), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarDropdown(cars: List<Car>, selected: Car?, onSelect: (Car) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            readOnly = true,
            value = selected?.name ?: "",
            onValueChange = {},
            label = { Text(stringResource(R.string.preride_car)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cars.forEach { car ->
                DropdownMenuItem(
                    text = { Text(car.name) },
                    onClick = { onSelect(car); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceDropdown(
    label: String,
    places: List<Place>,
    selected: Place?,
    onSelect: (Place?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            readOnly = true,
            value = selected?.let { "${it.iconEmoji} ${it.name}" } ?: "",
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.preride_place_none)) },
                onClick = { onSelect(null); expanded = false },
            )
            places.forEach { place ->
                DropdownMenuItem(
                    text = { Text("${place.iconEmoji} ${place.name}") },
                    onClick = { onSelect(place); expanded = false },
                )
            }
        }
    }
}
