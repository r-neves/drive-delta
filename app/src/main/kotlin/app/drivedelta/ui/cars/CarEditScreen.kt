package app.drivedelta.ui.cars

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.FuelType
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdSegmentActive
import app.drivedelta.ui.theme.DdSurface
import app.drivedelta.ui.theme.DdSurfaceElevated
import app.drivedelta.ui.theme.LocalDdTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarEditScreen(
    onDone: () -> Unit,
    viewModel: CarEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = LocalDdTokens.current

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val title = if (state.isEditing) stringResource(R.string.car_edit_title_edit) else stringResource(R.string.car_edit_title_new)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save) {
                        Text(stringResource(R.string.action_save), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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
            PreviewCard(state.name, state.licensePlate, state.fuelType)

            LabeledField(stringResource(R.string.car_label_name)) {
                CarTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    isError = state.nameError,
                    error = if (state.nameError) stringResource(R.string.car_error_name_required) else null,
                )
            }

            LabeledField(stringResource(R.string.car_label_plate)) {
                CarTextField(value = state.licensePlate, onValueChange = viewModel::onLicensePlateChange)
            }

            LabeledField(stringResource(R.string.car_label_fuel)) {
                FuelTypeSelector(selected = state.fuelType, onSelect = viewModel::onFuelTypeChange)
            }

            val electric = state.fuelType.isElectric
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd)) {
                LabeledField(
                    stringResource(if (electric) R.string.car_label_battery else R.string.car_label_tank),
                    Modifier.weight(1f),
                ) {
                    CarTextField(
                        value = if (electric) state.batteryCapacity else state.tankCapacity,
                        onValueChange = if (electric) viewModel::onBatteryCapacityChange else viewModel::onTankCapacityChange,
                        numeric = true,
                        suffix = if (electric) "kWh" else "L",
                    )
                }
                LabeledField(stringResource(R.string.car_label_consumption), Modifier.weight(1f)) {
                    CarTextField(
                        value = state.consumption,
                        onValueChange = viewModel::onConsumptionChange,
                        numeric = true,
                        suffix = if (electric) "kWh/100km" else "L/100km",
                    )
                }
            }
            if (electric) {
                Text(
                    stringResource(R.string.car_battery_helper),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DefaultToggleCard(checked = state.isDefault, onCheckedChange = viewModel::onDefaultChange)

            Spacer(Modifier.height(tokens.spaceSm))

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
            ) {
                Text(stringResource(R.string.car_edit_save_changes), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PreviewCard(name: String, plate: String, fuelType: FuelType) {
    val tokens = LocalDdTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusCard))
            .background(DdSurface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard))
            .padding(tokens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceLg),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(tokens.radiusMd)).background(DdSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(
                name.ifBlank { stringResource(R.string.car_edit_title_new) },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (plate.isNotBlank()) {
                Text(plate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FuelBadge(fuelType)
    }
}

@Composable
private fun FuelBadge(fuelType: FuelType) {
    val tokens = LocalDdTokens.current
    val color = fuelType.badgeColor
    Row(
        Modifier
            .clip(RoundedCornerShape(tokens.radiusSm))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(tokens.radiusSm))
            .padding(horizontal = tokens.spaceMd, vertical = tokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs),
    ) {
        Icon(fuelIcon(fuelType), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(stringResource(fuelType.labelRes), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun FuelTypeSelector(selected: FuelType, onSelect: (FuelType) -> Unit) {
    val tokens = LocalDdTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusInput))
            .background(DdSurface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusInput))
            .padding(tokens.spaceXs),
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs),
    ) {
        FuelType.entries.forEach { type ->
            val active = type == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(tokens.radiusSm))
                    .background(if (active) DdSegmentActive else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(vertical = tokens.spaceMd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
            ) {
                Icon(fuelIcon(type), contentDescription = null, tint = type.badgeColor, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(type.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DefaultToggleCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val tokens = LocalDdTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMd))
            .background(DdSurface)
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusMd))
            .padding(tokens.spaceLg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.car_set_default), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.car_set_default_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalDdTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    error: String? = null,
    numeric: Boolean = false,
    suffix: String? = null,
) {
    val tokens = LocalDdTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        isError = isError,
        supportingText = if (error != null) ({ Text(error) }) else null,
        suffix = suffix?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        shape = RoundedCornerShape(tokens.radiusInput),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DdSurface,
            unfocusedContainerColor = DdSurface,
            errorContainerColor = DdSurface,
            unfocusedBorderColor = DdOutline,
            focusedBorderColor = DdPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun fuelIcon(type: FuelType): ImageVector = when (type) {
    FuelType.PETROL -> Icons.Filled.LocalGasStation
    FuelType.DIESEL -> Icons.Filled.WaterDrop
    FuelType.HYBRID -> Icons.Filled.Autorenew
    FuelType.ELECTRIC -> Icons.Filled.Bolt
    FuelType.LPG -> Icons.Filled.WaterDrop
}
