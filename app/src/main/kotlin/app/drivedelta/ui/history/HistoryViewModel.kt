package app.drivedelta.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.domain.usecase.car.GetCarsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HistoryUiState(
    val groups: List<Pair<String, List<Trip>>> = emptyList(),
    val cars: List<Car> = emptyList(),
    val selectedCarId: String? = null,
)

/**
 * Backs the History screen (F11): completed trips grouped by month (newest first), with a
 * by-vehicle filter (chips). Place-pair / date-range filters from the plan are deferred.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    getCars: GetCarsUseCase,
) : ViewModel() {

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val selectedCarId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        tripRepository.observeTrips(),
        getCars(),
        selectedCarId,
    ) { trips, cars, carId ->
        val groups = trips
            .filter { it.endTime != null }
            .filter { carId == null || it.carId == carId }
            .groupBy { monthFormat.format(Date(it.startTime)) }
            .toList()
        HistoryUiState(groups = groups, cars = cars, selectedCarId = carId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectCar(carId: String?) {
        selectedCarId.value = carId
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch { tripRepository.deleteTrip(tripId) }
    }
}
