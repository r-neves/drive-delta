package app.drivedelta.ui.cars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.FuelType
import app.drivedelta.ui.theme.DdTextDim
import app.drivedelta.ui.theme.LocalDdTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarsScreen(
    onAddCar: () -> Unit,
    onEditCar: (String) -> Unit,
    viewModel: CarsViewModel = hiltViewModel(),
) {
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tokens = LocalDdTokens.current

    val deletedMessage = stringResource(R.string.cars_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cars_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCar,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cars_add))
            }
        },
    ) { padding ->
        if (cars.isEmpty()) {
            CarsEmptyState(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = tokens.screenPadding,
                    end = tokens.screenPadding,
                    top = tokens.spaceMd,
                    bottom = 96.dp, // clear the FAB
                ),
                verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
            ) {
                items(cars, key = { it.id }) { car ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.Settled) return@rememberSwipeToDismissBoxState false
                            viewModel.deleteCar(car.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = deletedMessage,
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                            }
                            true
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { SwipeDeleteBackground() },
                    ) {
                        CarCard(car = car, onClick = { onEditCar(car.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CarCard(car: Car, onClick: () -> Unit) {
    val tokens = LocalDdTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(tokens.radiusCard))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(tokens.radiusCard))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = car.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (car.licensePlate.isNotBlank()) {
                    Text(
                        text = car.licensePlate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (car.isDefault) DefaultChip()
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
        ) {
            FuelBadge(fuelType = car.fuelType)
            car.defaultConsumption?.let { consumption ->
                val label = if (car.fuelType.isElectric) {
                    stringResource(R.string.car_consumption_electric, formatConsumption(consumption))
                } else {
                    stringResource(R.string.car_consumption_fuel, formatConsumption(consumption))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FuelBadge(fuelType: FuelType) {
    val color = fuelType.badgeColor
    Text(
        text = stringResource(fuelType.labelRes),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun DefaultChip() {
    val tokens = LocalDdTokens.current
    val color = MaterialTheme.colorScheme.primary
    Text(
        text = stringResource(R.string.cars_default_chip),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(tokens.radiusSm))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                RoundedCornerShape(LocalDdTokens.current.radiusCard),
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.car_delete_content_desc),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CarsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.DirectionsCar,
            contentDescription = null,
            tint = DdTextDim,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.cars_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.cars_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Trims a trailing ".0" so "6.0" shows as "6" while "6.4" stays "6.4". */
private fun formatConsumption(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
