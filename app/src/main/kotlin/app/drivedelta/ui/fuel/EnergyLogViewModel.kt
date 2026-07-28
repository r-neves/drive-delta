package app.drivedelta.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.ElectricTariff
import app.drivedelta.domain.model.EnergyPrices
import app.drivedelta.domain.model.FuelLog
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.EnergyPricesRepository
import app.drivedelta.domain.repository.PlaceRepository
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.domain.usecase.fuel.LogFuelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * State for the per-drive energy log sheet (design/mockups/Energy Logging-*.png). [amount] is kWh for
 * electric cars and litres otherwise; [unitPrice] comes from the user's [EnergyPrices] (electric uses
 * the selected [tariff]). Cost and per-100km are derived on read so the UI recomputes as the user types.
 */
data class EnergyLogUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val car: Car? = null,
    val originName: String? = null,
    val destName: String? = null,
    val startTime: Long = 0L,
    val distanceMeters: Float = 0f,
    val amount: String = "",
    val tariff: ElectricTariff = ElectricTariff.PUBLIC,
    val unitPrice: Float = 0f,
    val currencyCode: String = EnergyPrices.DEFAULT_CURRENCY,
    /** The car's average consumption (kWh/100km or L/100km); null when the car has no estimate. */
    val avgConsumption: Float? = null,
    val saved: Boolean = false,
) {
    val isElectric: Boolean get() = car?.fuelType?.isElectric == true
    val distanceKm: Float get() = distanceMeters / 1000f
    val amountValue: Float? get() = amount.toFloatOrNull()
    val cost: Float? get() = amountValue?.let { it * unitPrice }

    /** Consumption implied by the entered amount over this drive's distance, or null if not derivable. */
    val per100: Float? get() = amountValue?.takeIf { distanceKm > 0f }?.let { it / distanceKm * 100f }

    /** The amount implied by the car's average consumption over this drive — the "Use estimate" value. */
    val estimateAmount: Float? get() = avgConsumption?.takeIf { distanceKm > 0f }?.let { it * distanceKm / 100f }

    val currencySymbol: String
        get() = try {
            Currency.getInstance(currencyCode).symbol
        } catch (e: Exception) {
            "€"
        }
}

/**
 * Backs the per-drive energy log sheet. Loads the drive's car + the user's energy prices, resolves the
 * unit price (electric tariff switchable per drive), and on save writes a [FuelLog] linked to the trip
 * with the priced total baked in.
 */
@HiltViewModel
class EnergyLogViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val carRepository: CarRepository,
    private val placeRepository: PlaceRepository,
    private val energyPricesRepository: EnergyPricesRepository,
    private val logFuel: LogFuelUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnergyLogUiState())
    val uiState: StateFlow<EnergyLogUiState> = _uiState.asStateFlow()

    private var loadedTripId: String? = null
    private var loadedPrices: EnergyPrices? = null

    /** Loads the drive once; safe to call repeatedly (re-entrant per tripId). */
    fun load(tripId: String) {
        if (loadedTripId == tripId) return
        loadedTripId = tripId
        viewModelScope.launch {
            val trip = tripRepository.getTrip(tripId)
            val car = trip?.carId?.let { carRepository.getCar(it) }
            val prices = energyPricesRepository.getPrices()
            loadedPrices = prices
            val tariff = prices.defaultElectricTariff
            val unitPrice = car?.let { prices.unitPriceFor(it, tariff) } ?: 0f
            _uiState.update {
                it.copy(
                    loading = false,
                    tripId = tripId,
                    car = car,
                    originName = trip?.startPlaceId?.let { id -> placeRepository.getPlace(id)?.name },
                    destName = trip?.endPlaceId?.let { id -> placeRepository.getPlace(id)?.name },
                    startTime = trip?.startTime ?: 0L,
                    distanceMeters = trip?.distanceMeters ?: 0f,
                    tariff = tariff,
                    unitPrice = unitPrice,
                    currencyCode = prices.currencyCode,
                    avgConsumption = car?.defaultConsumption,
                )
            }
        }
    }

    fun onAmount(v: String) = _uiState.update { it.copy(amount = v) }

    fun useEstimate() = _uiState.update { state ->
        val est = state.estimateAmount ?: return@update state
        state.copy(amount = String.format(Locale.US, "%.1f", est))
    }

    /** Switches the electric tariff for this drive (Home ↔ Public) and re-prices. */
    fun toggleTariff() = _uiState.update { state ->
        val car = state.car ?: return@update state
        if (!state.isElectric) return@update state
        val next = if (state.tariff == ElectricTariff.PUBLIC) ElectricTariff.HOME else ElectricTariff.PUBLIC
        val unitPrice = loadedPrices?.unitPriceFor(car, next) ?: state.unitPrice
        state.copy(tariff = next, unitPrice = unitPrice)
    }

    fun save() {
        val state = _uiState.value
        val car = state.car ?: return
        val tripId = state.tripId ?: return
        val amount = state.amountValue ?: return
        viewModelScope.launch {
            val log = FuelLog(
                id = UUID.randomUUID().toString(),
                userId = "",
                tripId = tripId,
                carId = car.id,
                timestamp = System.currentTimeMillis(),
                liters = if (state.isElectric) null else amount,
                pricePerLiter = if (state.isElectric) null else state.unitPrice,
                kwhCharged = if (state.isElectric) amount else null,
                pricePerKwh = if (state.isElectric) state.unitPrice else null,
                totalCost = state.cost ?: 0f,
                odometerKm = null,
            )
            logFuel(log)
            _uiState.update { it.copy(saved = true) }
        }
    }
}
