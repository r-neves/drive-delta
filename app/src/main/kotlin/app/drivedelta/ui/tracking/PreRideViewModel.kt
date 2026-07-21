package app.drivedelta.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.location.LocationProvider
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.usecase.car.GetCarsUseCase
import app.drivedelta.domain.usecase.place.DetectNearbyPlaceUseCase
import app.drivedelta.domain.usecase.place.GetPlacesUseCase
import app.drivedelta.domain.usecase.trip.StartTripUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the pre-ride bottom sheet (F5): streams the user's cars and places, suggests a nearby place
 * as origin from the current GPS, and starts the ride. [startedTripId] emits the new trip id once
 * [startRide] has created the trip and launched the tracking service, so the host can navigate to
 * the Live Tracking screen.
 */
@HiltViewModel
class PreRideViewModel @Inject constructor(
    getCarsUseCase: GetCarsUseCase,
    getPlacesUseCase: GetPlacesUseCase,
    private val detectNearbyPlaceUseCase: DetectNearbyPlaceUseCase,
    private val locationProvider: LocationProvider,
    private val startTripUseCase: StartTripUseCase,
) : ViewModel() {

    val cars: StateFlow<List<Car>> = getCarsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val places: StateFlow<List<Place>> = getPlacesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _nearbyPlace = MutableStateFlow<Place?>(null)
    val nearbyPlace: StateFlow<Place?> = _nearbyPlace.asStateFlow()

    private val _startedTripId = MutableStateFlow<String?>(null)
    val startedTripId: StateFlow<String?> = _startedTripId.asStateFlow()

    init {
        viewModelScope.launch {
            val location = locationProvider.lastLocation() ?: return@launch
            _nearbyPlace.value = detectNearbyPlaceUseCase(location.latitude, location.longitude)
        }
    }

    fun startRide(carId: String?, originPlaceId: String?, destinationPlaceId: String?) {
        viewModelScope.launch {
            _startedTripId.value = startTripUseCase(
                carId = carId,
                startPlaceId = originPlaceId,
                destinationPlaceId = destinationPlaceId,
            )
        }
    }
}
