package app.drivedelta.ui.tracking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.Place
import app.drivedelta.ui.tracking.PreRideViewModel

/**
 * Pre-ride setup sheet (F5): car selector (pre-selects the default), optional origin/destination
 * place dropdowns, a nearby-place suggestion chip, and Start Ride. Requires at least one car — with
 * none, it shows a prompt to add one. On start it calls the ViewModel, which creates the trip +
 * launches the service and emits the trip id via [onStarted].
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

    LaunchedEffect(startedTripId) { if (startedTripId != null) onStarted() }

    var selectedCar by remember(cars) { mutableStateOf(cars.firstOrNull { it.isDefault } ?: cars.firstOrNull()) }
    var origin by remember { mutableStateOf<Place?>(null) }
    var destination by remember { mutableStateOf<Place?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

            nearbyPlace?.let { place ->
                if (origin == null) {
                    AssistChip(
                        onClick = { origin = place },
                        label = { Text(stringResource(R.string.preride_nearby_chip, place.iconEmoji, place.name)) },
                    )
                }
            }

            PlaceDropdown(
                label = stringResource(R.string.preride_origin),
                places = places,
                selected = origin,
                onSelect = { origin = it },
            )
            PlaceDropdown(
                label = stringResource(R.string.preride_destination),
                places = places,
                selected = destination,
                onSelect = { destination = it },
            )

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
                Text(stringResource(R.string.preride_start), style = MaterialTheme.typography.labelLarge)
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
