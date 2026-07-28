package app.drivedelta.ui.fuel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.ElectricTariff
import app.drivedelta.ui.cars.badgeColor
import app.drivedelta.ui.cars.labelRes
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdPrimary
import app.drivedelta.ui.theme.DdSurfaceElevated
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Per-drive energy log bottom sheet (design/mockups/Energy Logging-after-drive-electric.png &
 * -fill-in-later-petrol.png). Adapts to the drive's car: kWh + a switchable electricity tariff for
 * electric cars, litres otherwise. The unit price comes from the user's Energy Prices and the drive
 * cost is computed live. Tapping the rate row switches tariff (electric) or opens Energy Prices (liquid).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyLogSheet(
    tripId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onOpenPrices: () -> Unit,
    viewModel: EnergyLogViewModel = hiltViewModel(),
) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(tripId) { viewModel.load(tripId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        val car = state.car
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.screenPadding)
                .padding(bottom = tokens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
        ) {
            // Title + optional badge.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (state.isElectric) R.string.energy_log_title_electric else R.string.energy_log_title_fuel),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                OptionalBadge()
            }
            Text(
                headerSubtitle(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (car != null) {
                // Car card.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(DdSurfaceElevated, RoundedCornerShape(tokens.radiusInput))
                        .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusInput))
                        .padding(horizontal = tokens.spaceLg, vertical = tokens.spaceMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
                ) {
                    Icon(
                        if (state.isElectric) Icons.Filled.Bolt else Icons.Filled.LocalGasStation,
                        contentDescription = null,
                        tint = car.fuelType.badgeColor,
                    )
                    Text(car.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(stringResource(car.fuelType.labelRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Amount input.
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
                    Text(
                        stringResource(if (state.isElectric) R.string.energy_log_energy_used else R.string.energy_log_fuel_used).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val unit = stringResource(if (state.isElectric) R.string.fuel_unit_kwh else R.string.energy_log_litres)
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = viewModel::onAmount,
                        singleLine = true,
                        textStyle = ddType.numericMono.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                        suffix = { Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(tokens.radiusInput),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = DdOutline,
                            focusedBorderColor = DdPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Helper + "Use estimate".
                val unitPer100 = stringResource(if (state.isElectric) R.string.energy_log_unit_kwh_per_100 else R.string.energy_log_unit_l_per_100)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val helper = when {
                        state.per100 != null -> stringResource(R.string.energy_log_works_out, String.format(Locale.US, "%.1f", state.per100), unitPer100)
                        state.avgConsumption != null -> stringResource(R.string.energy_log_estimated_from, String.format(Locale.US, "%.1f", state.avgConsumption), unitPer100)
                        else -> ""
                    }
                    Text(helper, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (state.estimateAmount != null) {
                        OutlinedButton(onClick = viewModel::useEstimate) {
                            Text(stringResource(R.string.energy_log_use_estimate))
                        }
                    }
                }

                // Rate + cost card.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(DdSurfaceElevated, RoundedCornerShape(tokens.radiusInput))
                        .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusInput)),
                ) {
                    val onRateClick = if (state.isElectric) viewModel::toggleTariff else onOpenPrices
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onRateClick)
                            .padding(horizontal = tokens.spaceLg, vertical = tokens.spaceMd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(rateLabel(state), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text(
                            "${state.currencySymbol}${String.format(Locale.US, "%.2f", state.unitPrice)}/${stringResource(if (state.isElectric) R.string.fuel_unit_kwh else R.string.energy_log_unit_l)}",
                            style = ddType.numericMono,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DdOutline))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = tokens.spaceLg, vertical = tokens.spaceMd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.energy_log_cost_for_drive), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(
                            state.cost?.let { "${state.currencySymbol}${String.format(Locale.US, "%.2f", it)}" } ?: "—",
                            style = ddType.statValue,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Actions.
            Button(
                onClick = viewModel::save,
                enabled = state.cost != null,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(tokens.radiusMd),
            ) {
                Text(
                    stringResource(if (state.isElectric) R.string.energy_log_save_energy else R.string.energy_log_save_fuel),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(if (state.isElectric) R.string.energy_log_skip else R.string.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OptionalBadge() {
    val tokens = LocalDdTokens.current
    Text(
        stringResource(R.string.energy_log_optional),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusSm))
            .padding(horizontal = tokens.spaceMd, vertical = tokens.spaceXs),
    )
}

@Composable
private fun rateLabel(state: EnergyLogUiState): String = if (state.isElectric) {
    val tariff = stringResource(
        if (state.tariff == ElectricTariff.HOME) R.string.energy_price_home else R.string.energy_price_public,
    )
    stringResource(R.string.energy_log_rate_tariff, tariff)
} else {
    stringResource(R.string.energy_log_rate_fuel, stringResource(state.car!!.fuelType.labelRes))
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** "Home → Office · 12 June · 31.2 km" (route omitted when the drive has no linked places). */
private fun headerSubtitle(state: EnergyLogUiState): String {
    val parts = mutableListOf<String>()
    if (state.originName != null || state.destName != null) {
        parts += "${state.originName ?: "—"} → ${state.destName ?: "—"}"
    }
    if (state.startTime > 0L) {
        parts += Instant.ofEpochMilli(state.startTime).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
    }
    parts += String.format(Locale.US, "%.1f km", state.distanceKm)
    return parts.joinToString(" · ")
}
