package app.drivedelta.ui.fuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.FuelLog
import app.drivedelta.domain.repository.FuelLogRepository
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.domain.usecase.car.GetCarsUseCase
import app.drivedelta.domain.usecase.fuel.LogFuelUseCase
import app.drivedelta.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class FuelLogUiState(
    val cars: List<Car> = emptyList(),
    val selectedCar: Car? = null,
    val liters: String = "",
    val pricePerLiter: String = "",
    val kwh: String = "",
    val pricePerKwh: String = "",
    val totalCost: String = "",
    val odometer: String = "",
    val saved: Boolean = false,
    val efficiency: String? = null,
) {
    val isElectric: Boolean get() = selectedCar?.fuelType?.isElectric == true
}

/**
 * Backs the Fuel Log screen (F12). Adapts the form to the selected car's fuel type, auto-calculates
 * the total (litres × price / kWh × price) until the user overrides it, and on save reports this
 * fill-up's cost and, when a trip is linked, its efficiency. A [NavArgs.TRIP_ID] arg (from the
 * post-ride prompt) pre-links the trip and pre-selects its car.
 */
@HiltViewModel
class FuelLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getCars: GetCarsUseCase,
    private val tripRepository: TripRepository,
    private val logFuel: LogFuelUseCase,
) : ViewModel() {

    private val tripId: String? = savedStateHandle[NavArgs.TRIP_ID]
    private var totalEdited = false

    private val _uiState = MutableStateFlow(FuelLogUiState())
    val uiState: StateFlow<FuelLogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val cars = getCars().first()
            val linkedCarId = tripId?.let { tripRepository.getTrip(it)?.carId }
            val preselected = cars.firstOrNull { it.id == linkedCarId }
                ?: cars.firstOrNull { it.isDefault }
                ?: cars.firstOrNull()
            _uiState.update { it.copy(cars = cars, selectedCar = preselected) }
        }
    }

    fun selectCar(car: Car) = _uiState.update { it.copy(selectedCar = car) }

    fun onLiters(v: String) = _uiState.update { recalc(it.copy(liters = v)) }
    fun onPricePerLiter(v: String) = _uiState.update { recalc(it.copy(pricePerLiter = v)) }
    fun onKwh(v: String) = _uiState.update { recalc(it.copy(kwh = v)) }
    fun onPricePerKwh(v: String) = _uiState.update { recalc(it.copy(pricePerKwh = v)) }
    fun onOdometer(v: String) = _uiState.update { it.copy(odometer = v) }
    fun onTotalCost(v: String) {
        totalEdited = true
        _uiState.update { it.copy(totalCost = v) }
    }

    private fun recalc(state: FuelLogUiState): FuelLogUiState {
        if (totalEdited) return state
        val total = if (state.isElectric) {
            state.kwh.toFloatOrNull()?.let { k -> state.pricePerKwh.toFloatOrNull()?.let { p -> k * p } }
        } else {
            state.liters.toFloatOrNull()?.let { l -> state.pricePerLiter.toFloatOrNull()?.let { p -> l * p } }
        }
        return if (total != null) state.copy(totalCost = String.format(java.util.Locale.US, "%.2f", total)) else state
    }

    fun save() {
        val state = _uiState.value
        val car = state.selectedCar ?: return
        viewModelScope.launch {
            val trip = tripId?.let { tripRepository.getTrip(it) }
            val log = FuelLog(
                id = UUID.randomUUID().toString(),
                userId = "",
                tripId = tripId,
                carId = car.id,
                timestamp = System.currentTimeMillis(),
                liters = if (state.isElectric) null else state.liters.toFloatOrNull(),
                pricePerLiter = if (state.isElectric) null else state.pricePerLiter.toFloatOrNull(),
                kwhCharged = if (state.isElectric) state.kwh.toFloatOrNull() else null,
                pricePerKwh = if (state.isElectric) state.pricePerKwh.toFloatOrNull() else null,
                totalCost = state.totalCost.toFloatOrNull() ?: 0f,
                odometerKm = state.odometer.toFloatOrNull(),
            )
            logFuel(log)

            // Post-save efficiency when a trip with distance is linked.
            val distanceKm = (trip?.distanceMeters ?: 0f) / 1000f
            val amount = if (state.isElectric) state.kwh.toFloatOrNull() else state.liters.toFloatOrNull()
            val efficiency = if (distanceKm > 0f && amount != null && amount > 0f) {
                val per100 = amount / distanceKm * 100f
                val unit = if (state.isElectric) "kWh/100km" else "L/100km"
                String.format(java.util.Locale.US, "%.1f %s", per100, unit)
            } else {
                null
            }
            _uiState.update { it.copy(saved = true, efficiency = efficiency) }
        }
    }
}
