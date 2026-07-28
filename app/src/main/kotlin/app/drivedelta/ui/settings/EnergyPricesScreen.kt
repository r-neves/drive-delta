package app.drivedelta.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.ElectricTariff
import app.drivedelta.domain.model.EnergyPrices
import app.drivedelta.ui.theme.DdFuelDiesel
import app.drivedelta.ui.theme.DdFuelElectric
import app.drivedelta.ui.theme.DdFuelLpg
import app.drivedelta.ui.theme.DdFuelPetrol
import app.drivedelta.ui.theme.DdOutline
import app.drivedelta.ui.theme.DdSurface
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.util.Currency
import java.util.Locale

/** A price the user is currently editing in the dialog. [apply] persists the parsed new value. */
private data class PriceEdit(
    @StringRes val titleRes: Int,
    val current: Float,
    val unit: String,
    val apply: (Float) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyPricesScreen(
    onBack: () -> Unit,
    viewModel: EnergyPricesViewModel = hiltViewModel(),
) {
    val tokens = LocalDdTokens.current
    val prices by viewModel.prices.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PriceEdit?>(null) }
    val symbol = currencySymbol(prices.currencyCode)
    val perKwh = stringResource(R.string.energy_price_per_kwh)
    val perL = stringResource(R.string.energy_log_unit_l)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.energy_prices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.screenPadding)
                .padding(bottom = tokens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceLg),
        ) {
            CurrencyRow(prices.currencyCode) { code -> viewModel.update { it.copy(currencyCode = code) } }

            SectionLabel(stringResource(R.string.energy_section_electricity))
            SettingsCard {
                SettingRow(
                    icon = Icons.Outlined.Home,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.energy_price_home),
                    subtitle = if (prices.defaultElectricTariff == ElectricTariff.HOME) stringResource(R.string.energy_default_tariff) else null,
                    leading = {
                        RadioButton(
                            selected = prices.defaultElectricTariff == ElectricTariff.HOME,
                            onClick = { viewModel.update { it.copy(defaultElectricTariff = ElectricTariff.HOME) } },
                        )
                    },
                    valueText = "$symbol${money(prices.electricityHomePerKwh)}",
                    valueUnit = perKwh,
                    onClick = {
                        editing = PriceEdit(R.string.energy_price_home, prices.electricityHomePerKwh, perKwh) { v ->
                            viewModel.update { it.copy(electricityHomePerKwh = v) }
                        }
                    },
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Filled.Bolt,
                    iconTint = DdFuelElectric,
                    title = stringResource(R.string.energy_price_public),
                    subtitle = if (prices.defaultElectricTariff == ElectricTariff.PUBLIC) stringResource(R.string.energy_default_tariff) else null,
                    leading = {
                        RadioButton(
                            selected = prices.defaultElectricTariff == ElectricTariff.PUBLIC,
                            onClick = { viewModel.update { it.copy(defaultElectricTariff = ElectricTariff.PUBLIC) } },
                        )
                    },
                    valueText = "$symbol${money(prices.electricityPublicPerKwh)}",
                    valueUnit = perKwh,
                    onClick = {
                        editing = PriceEdit(R.string.energy_price_public, prices.electricityPublicPerKwh, perKwh) { v ->
                            viewModel.update { it.copy(electricityPublicPerKwh = v) }
                        }
                    },
                )
            }
            Text(
                stringResource(R.string.energy_tariff_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel(stringResource(R.string.energy_section_liquid))
            SettingsCard {
                SettingRow(
                    icon = Icons.Filled.LocalGasStation,
                    iconTint = DdFuelPetrol,
                    title = stringResource(R.string.fuel_petrol),
                    valueText = "$symbol${money(prices.petrolPricePerL)}",
                    valueUnit = perL,
                    onClick = {
                        editing = PriceEdit(R.string.fuel_petrol, prices.petrolPricePerL, perL) { v ->
                            viewModel.update { it.copy(petrolPricePerL = v) }
                        }
                    },
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Outlined.WaterDrop,
                    iconTint = DdFuelDiesel,
                    title = stringResource(R.string.fuel_diesel),
                    valueText = "$symbol${money(prices.dieselPricePerL)}",
                    valueUnit = perL,
                    onClick = {
                        editing = PriceEdit(R.string.fuel_diesel, prices.dieselPricePerL, perL) { v ->
                            viewModel.update { it.copy(dieselPricePerL = v) }
                        }
                    },
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Outlined.WaterDrop,
                    iconTint = DdFuelLpg,
                    title = stringResource(R.string.fuel_lpg),
                    valueText = "$symbol${money(prices.lpgPricePerL)}",
                    valueUnit = perL,
                    onClick = {
                        editing = PriceEdit(R.string.fuel_lpg, prices.lpgPricePerL, perL) { v ->
                            viewModel.update { it.copy(lpgPricePerL = v) }
                        }
                    },
                )
            }

            SectionLabel(stringResource(R.string.energy_section_automation))
            SettingsCard {
                ToggleRow(
                    title = stringResource(R.string.energy_estimate_title),
                    subtitle = stringResource(R.string.energy_estimate_body),
                    checked = prices.estimateWhenNotLogged,
                    onChecked = { c -> viewModel.update { it.copy(estimateWhenNotLogged = c) } },
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.energy_ask_title),
                    subtitle = stringResource(R.string.energy_ask_body),
                    checked = prices.askAfterEveryDrive,
                    onChecked = { c -> viewModel.update { it.copy(askAfterEveryDrive = c) } },
                )
            }
        }
    }

    editing?.let { target ->
        PriceEditDialog(
            target = target,
            currencySymbol = symbol,
            onDismiss = { editing = null },
            onConfirm = { value -> target.apply(value); editing = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val tokens = LocalDdTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(DdSurface, RoundedCornerShape(tokens.radiusCard))
            .border(1.dp, DdOutline, RoundedCornerShape(tokens.radiusCard)),
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DdOutline))
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    valueText: String,
    valueUnit: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spaceLg, vertical = tokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(valueText, style = ddType.numericMono, color = MaterialTheme.colorScheme.onSurface)
        Text(valueUnit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (leading != null) leading()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val tokens = LocalDdTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.spaceLg, vertical = tokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyRow(currencyCode: String, onSelect: (String) -> Unit) {
    val tokens = LocalDdTokens.current
    var expanded by remember { mutableStateOf(false) }
    SettingsCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = tokens.spaceLg, vertical = tokens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.energy_currency), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text("$currencyCode · ${currencySymbol(currencyCode)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CURRENCIES.forEach { code ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("$code · ${currencySymbol(code)}") },
                    onClick = { onSelect(code); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun PriceEditDialog(
    target: PriceEdit,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember(target) { mutableStateOf(money(target.current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(target.titleRes)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                prefix = { Text(currencySymbol) },
                suffix = { Text(target.unit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { text.replace(',', '.').toFloatOrNull()?.let(onConfirm) },
                enabled = text.replace(',', '.').toFloatOrNull() != null,
            ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val CURRENCIES = listOf("EUR", "USD", "GBP", "BRL", "CHF")

private fun money(v: Float): String = String.format(Locale.US, "%.2f", v)

private fun currencySymbol(code: String): String = try {
    Currency.getInstance(code).symbol
} catch (e: Exception) {
    code
}
