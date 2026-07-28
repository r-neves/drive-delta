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
    val pairOptions: List<RoutePairOption> = emptyList(),
    val selectedCarId: String? = null,
    val selectedPair: RoutePair? = null,
    val dateRange: LongRange? = null,
    val query: String = "",
) {
    val isEmpty: Boolean get() = sections.isEmpty() && routes.isEmpty()
    /** How many of the vehicle/route/date filters are active (drives the filter-icon highlight). */
    val activeFilterCount: Int
        get() = listOf(selectedCarId != null, selectedPair != null, dateRange != null).count { it }
}

/** The four narrowing inputs, folded together so the ui-state [combine] stays within its arity. */
private data class Filters(
    val carId: String?,
    val pair: RoutePair?,
    val dateRange: LongRange?,
    val query: String,
)

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
    private val selectedPair = MutableStateFlow<RoutePair?>(null)
    private val dateRange = MutableStateFlow<LongRange?>(null)
    private val query = MutableStateFlow("")

    private val filters = combine(selectedCarId, selectedPair, dateRange, query) { carId, pair, range, q ->
        Filters(carId, pair, range, q)
    }

    val uiState: StateFlow<TripsUiState> = combine(
        tripRepository.observeTrips(),
        carRepository.observeCars(),
        placeRepository.observePlaces(),
        filters,
    ) { trips, cars, places, f ->
        val overview = TripsOverviewBuilder.build(
            allTrips = trips,
            cars = cars,
            places = places,
            selectedCarId = f.carId,
            query = f.query,
            epochDayOf = { millis -> Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay() },
            selectedPair = f.pair,
            dateRange = f.dateRange,
        )
        TripsUiState(
            stats = overview.stats,
            sections = overview.sections,
            routes = overview.routes,
            cars = cars,
            pairOptions = overview.pairOptions,
            selectedCarId = f.carId,
            selectedPair = f.pair,
            dateRange = f.dateRange,
            query = f.query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    fun selectCar(carId: String?) {
        selectedCarId.value = carId
    }

    fun selectPair(pair: RoutePair?) {
        selectedPair.value = pair
    }

    fun setDateRange(range: LongRange?) {
        dateRange.value = range
    }

    /** Clears vehicle, route and date filters (search is cleared separately with the search bar). */
    fun clearFilters() {
        selectedCarId.value = null
        selectedPair.value = null
        dateRange.value = null
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch { tripRepository.deleteTrip(tripId) }
    }
}
