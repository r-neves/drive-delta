package app.drivedelta.ui.fuel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.Car
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdSuccess
import app.drivedelta.ui.theme.DdSurface
import app.drivedelta.ui.theme.LocalDdTokens
import java.util.Locale

/**
 * Fuel / energy log (F12). Adopts the Car-edit form language (design/mockups/car-edit.png): uppercase
 * field labels over filled surface inputs with unit suffixes, an app-bar + bottom Save. The form adapts
 * to the selected car's fuel type (litres/price vs kWh/price).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelLogScreen(
    onDone: () -> Unit,
    viewModel: FuelLogViewModel = hiltViewModel(),
) {
    val tokens = LocalDdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fuel_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.selectedCar != null) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = DdPrimary,
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
            verticalArrangement = Arrangement.spacedBy(tokens.spaceXl),
        ) {
            LabeledField(stringResource(R.string.fuel_car)) {
                CarSelector(state.cars, state.selectedCar, viewModel::selectCar)
            }

            if (state.isElectric) {
                LabeledField(stringResource(R.string.fuel_kwh)) {
                    FuelTextField(state.kwh, viewModel::onKwh, suffix = stringResource(R.string.fuel_unit_kwh))
                }
                LabeledField(stringResource(R.string.fuel_price_kwh)) {
                    FuelTextField(state.pricePerKwh, viewModel::onPricePerKwh)
                }
            } else {
                LabeledField(stringResource(R.string.fuel_liters)) {
                    FuelTextField(state.liters, viewModel::onLiters, suffix = stringResource(R.string.fuel_unit_l))
                }
                LabeledField(stringResource(R.string.fuel_price_liter)) {
                    FuelTextField(state.pricePerLiter, viewModel::onPricePerLiter)
                }
            }
            LabeledField(stringResource(R.string.fuel_total)) {
                FuelTextField(state.totalCost, viewModel::onTotalCost)
            }
            LabeledField(stringResource(R.string.fuel_odometer)) {
                FuelTextField(state.odometer, viewModel::onOdometer, suffix = stringResource(R.string.fuel_unit_km))
            }

            if (state.saved) {
                SavedSummary(state)
            }

            Button(
                onClick = viewModel::save,
                enabled = state.selectedCar != null,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
            ) {
                Text(stringResource(R.string.fuel_save), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SavedSummary(state: FuelLogUiState) {
    val tokens = LocalDdTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DdSurface, RoundedCornerShape(tokens.radiusCard))
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard))
            .padding(tokens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceSm),
    ) {
        Text(
            stringResource(R.string.fuel_saved_cost, state.totalCost),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.efficiency?.let {
            Text(
                stringResource(R.string.fuel_saved_efficiency, it),
                style = MaterialTheme.typography.bodyLarge,
                color = DdSuccess,
            )
        }
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    val tokens = LocalDdTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarSelector(cars: List<Car>, selected: Car?, onSelect: (Car) -> Unit) {
    val tokens = LocalDdTokens.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            readOnly = true,
            value = selected?.name ?: "",
            onValueChange = {},
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(tokens.radiusInput),
            colors = fieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cars.forEach { car ->
                DropdownMenuItem(text = { Text(car.name) }, onClick = { onSelect(car); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelTextField(value: String, onChange: (String) -> Unit, suffix: String? = null) {
    val tokens = LocalDdTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        suffix = suffix?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(tokens.radiusInput),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DdSurface,
    unfocusedContainerColor = DdSurface,
    unfocusedBorderColor = DdOutline,
    focusedBorderColor = DdPrimary,
)
