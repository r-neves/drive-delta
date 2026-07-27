package app.drivedelta.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.PlaceRepository
import app.drivedelta.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class TripsUiState(
    val stats: TripsStats = TripsStats(0, 0f, 0),
    val sections: List<DaySection> = emptyList(),
    val routes: List<RouteItem> = emptyList(),
    val cars: List<Car> = emptyList(),
    val selectedCarId: String? = null,
    val query: String = "",
) {
    val isEmpty: Boolean get() = sections.isEmpty() && routes.isEmpty()
}

/**
 * Backs the Trips screen (design/mockups/trips-recent.png, trips-by-route.png; F11). Streams the
 * user's completed trips, cars and places, and folds them into the Recent + By-route views via
 * [TripsOverviewBuilder]. A by-vehicle filter and a text search narrow the set; the Recent/By-route
 * tab and the search field's visibility are UI state owned by the screen.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    carRepository: CarRepository,
    placeRepository: PlaceRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val selectedCarId = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<TripsUiState> = combine(
        tripRepository.observeTrips(),
        carRepository.observeCars(),
        placeRepository.observePlaces(),
        selectedCarId,
        query,
    ) { trips, cars, places, carId, q ->
        val overview = TripsOverviewBuilder.build(
            allTrips = trips,
            cars = cars,
            places = places,
            selectedCarId = carId,
            query = q,
            epochDayOf = { millis -> Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay() },
        )
        TripsUiState(
            stats = overview.stats,
            sections = overview.sections,
            routes = overview.routes,
            cars = cars,
            selectedCarId = carId,
            query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    fun selectCar(carId: String?) {
        selectedCarId.value = carId
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch { tripRepository.deleteTrip(tripId) }
    }
}
