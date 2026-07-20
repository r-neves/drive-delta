package app.drivedelta.ui.cars

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.FuelType
import app.drivedelta.domain.usecase.car.GetCarsUseCase
import app.drivedelta.domain.usecase.car.SaveCarUseCase
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

/**
 * Form state for the add/edit screen. Numeric fields are held as raw strings so the text fields can
 * stay partially typed; they are parsed on save. [editingId] is null in add mode.
 */
data class CarEditUiState(
    val editingId: String? = null,
    val name: String = "",
    val licensePlate: String = "",
    val fuelType: FuelType = FuelType.PETROL,
    val tankCapacity: String = "",
    val batteryCapacity: String = "",
    val consumption: String = "",
    val isDefault: Boolean = false,
    val nameError: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null
}

@HiltViewModel
class CarEditViewModel @Inject constructor(
    private val saveCarUseCase: SaveCarUseCase,
    private val getCarsUseCase: GetCarsUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val carId: String? = savedStateHandle[NavArgs.CAR_ID]

    /** createdAt of the car under edit, preserved across a save. Set when an existing car loads. */
    private var createdAt: Long? = null

    private val _uiState = MutableStateFlow(CarEditUiState())
    val uiState: StateFlow<CarEditUiState> = _uiState.asStateFlow()

    init {
        if (carId != null) loadCar(carId)
    }

    private fun loadCar(id: String) {
        viewModelScope.launch {
            val car = getCarsUseCase().first().firstOrNull { it.id == id } ?: return@launch
            createdAt = car.createdAt
            _uiState.update {
                it.copy(
                    editingId = car.id,
                    name = car.name,
                    licensePlate = car.licensePlate,
                    fuelType = car.fuelType,
                    tankCapacity = car.tankCapacityLiters?.let(::trimFloat).orEmpty(),
                    batteryCapacity = car.batteryCapacityKwh?.let(::trimFloat).orEmpty(),
                    consumption = car.defaultConsumption?.let(::trimFloat).orEmpty(),
                    isDefault = car.isDefault,
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = false) }
    fun onLicensePlateChange(value: String) = _uiState.update { it.copy(licensePlate = value) }
    fun onFuelTypeChange(type: FuelType) = _uiState.update { it.copy(fuelType = type) }
    fun onTankCapacityChange(value: String) = _uiState.update { it.copy(tankCapacity = value) }
    fun onBatteryCapacityChange(value: String) = _uiState.update { it.copy(batteryCapacity = value) }
    fun onConsumptionChange(value: String) = _uiState.update { it.copy(consumption = value) }
    fun onDefaultChange(value: Boolean) = _uiState.update { it.copy(isDefault = value) }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        val electric = state.fuelType.isElectric
        val car = Car(
            id = state.editingId ?: UUID.randomUUID().toString(),
            userId = "", // stamped by the repository from the current session
            name = state.name.trim(),
            licensePlate = state.licensePlate.trim(),
            fuelType = state.fuelType,
            tankCapacityLiters = if (electric) null else state.tankCapacity.toFloatOrNull(),
            batteryCapacityKwh = if (electric) state.batteryCapacity.toFloatOrNull() else null,
            defaultConsumption = state.consumption.toFloatOrNull(),
            isDefault = state.isDefault,
            createdAt = createdAt ?: System.currentTimeMillis(),
        )
        viewModelScope.launch {
            saveCarUseCase(car)
            _uiState.update { it.copy(saved = true) }
        }
    }
}

/** Renders a float without a trailing ".0" for editing (e.g. 60.0 -> "60", 5.8 -> "5.8"). */
private fun trimFloat(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
