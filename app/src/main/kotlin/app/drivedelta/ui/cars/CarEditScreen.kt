package app.drivedelta.ui.cars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.FuelType
import app.drivedelta.ui.theme.LocalDdTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarEditScreen(
    onDone: () -> Unit,
    viewModel: CarEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = LocalDdTokens.current

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    val title = if (state.isEditing) {
        stringResource(R.string.car_edit_title_edit)
    } else {
        stringResource(R.string.car_edit_title_new)
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
                label = { Text(stringResource(R.string.car_field_name)) },
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.car_error_name_required)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(tokens.radiusInput),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.licensePlate,
                onValueChange = viewModel::onLicensePlateChange,
                label = { Text(stringResource(R.string.car_field_license_plate)) },
                singleLine = true,
                shape = RoundedCornerShape(tokens.radiusInput),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.car_field_fuel_type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FuelTypeSelector(
                selected = state.fuelType,
                onSelect = viewModel::onFuelTypeChange,
            )

            if (state.fuelType.isElectric) {
                NumericField(
                    value = state.batteryCapacity,
                    onValueChange = viewModel::onBatteryCapacityChange,
                    label = stringResource(R.string.car_field_battery_capacity),
                    radius = tokens.radiusInput,
                )
                NumericField(
                    value = state.consumption,
                    onValueChange = viewModel::onConsumptionChange,
                    label = stringResource(R.string.car_field_consumption_electric),
                    radius = tokens.radiusInput,
                )
            } else {
                NumericField(
                    value = state.tankCapacity,
                    onValueChange = viewModel::onTankCapacityChange,
                    label = stringResource(R.string.car_field_tank_capacity),
                    radius = tokens.radiusInput,
                )
                NumericField(
                    value = state.consumption,
                    onValueChange = viewModel::onConsumptionChange,
                    label = stringResource(R.string.car_field_consumption_fuel),
                    radius = tokens.radiusInput,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.car_set_default),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = state.isDefault,
                    onCheckedChange = viewModel::onDefaultChange,
                )
            }

            Spacer(Modifier.height(tokens.spaceSm))

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelTypeSelector(
    selected: FuelType,
    onSelect: (FuelType) -> Unit,
) {
    val options = FuelType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(type.labelRes))
            }
        }
    }
}

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    radius: androidx.compose.ui.unit.Dp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(radius),
        modifier = Modifier.fillMaxWidth(),
    )
}
