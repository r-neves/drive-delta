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
import kotlinx.coroutines.Job
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

    /**
     * The id of the trip [startRide] just created, or null. A one-shot event, not a fact: this
     * ViewModel is scoped to the Dashboard's back-stack entry, not to the sheet, so it outlives
     * every open/close of the sheet. Leaving the id set meant the *next* time the sheet opened, its
     * "did we start?" effect fired immediately on the previous ride's id and skipped the whole
     * sheet, dropping the driver onto a tracking screen with no ride behind it. Call
     * [onStartHandled] once the host has acted on it.
     */
    private val _startedTripId = MutableStateFlow<String?>(null)
    val startedTripId: StateFlow<String?> = _startedTripId.asStateFlow()

    private var nearbyJob: Job? = null

    /** Held from the Start Ride tap until the host has navigated away; blocks a second ride. */
    private var starting = false

    init {
        refreshNearbyPlace()
    }

    /**
     * Re-detects the saved place the driver is standing in, which the sheet uses to fill the origin.
     *
     * Called every time the sheet opens rather than once at construction: this ViewModel is scoped
     * to the Dashboard's back-stack entry, so it outlives the sheet, and a suggestion detected on
     * this morning's drive would otherwise still be sitting there this evening.
     */
    fun refreshNearbyPlace() {
        nearbyJob?.cancel()
        nearbyJob = viewModelScope.launch {
            // Only ever publish a definite answer, and always publish it — including "no place".
            //
            // Both halves matter. Publishing null *eagerly*, before the lookup, empties a correct
            // origin for as long as the fix takes: `currentLocation()` falls back to a fresh
            // high-accuracy request whenever the cache is stale, and a driver who opens the sheet
            // and taps Start Ride a second later would save the ride with no origin at all.
            // Publishing only non-null answers is the mirror failure: the suggestion then outlives
            // the place it was detected in, and a ride gets saved as starting somewhere it didn't.
            //
            // A location we couldn't get is not evidence of anything, so it clears too — "we don't
            // know where you are" must not read as "you are still where you were this morning".
            val location = locationProvider.currentLocation()
            _nearbyPlace.value = location?.let {
                detectNearbyPlaceUseCase(it.latitude, it.longitude)
            }
        }
    }

    /**
     * Consumes the [startedTripId] event so re-opening the sheet can't replay it, and reopens the
     * door to starting another ride. Releasing [starting] here rather than when the trip is created
     * is the point: the sheet is still on screen for a frame or two after the trip exists, and a tap
     * landing in that window started a *second* ride — measured, four rapid taps left an extra trip
     * behind with no end time.
     */
    fun onStartHandled() {
        _startedTripId.value = null
        starting = false
    }

    /**
     * Creates the trip and starts recording. Guarded from the first tap until the host has
     * navigated away ([onStartHandled]): [StartTripUseCase] suspends on a location lookup before it
     * writes anything, and the sheet stays on screen for a frame or two after the trip exists, so a
     * second tap anywhere in that window minted a second trip and started the service twice —
     * leaving the first stranded with no end time, invisible until the next cold start's
     * abandoned-ride sweep.
     */
    fun startRide(carId: String?, originPlaceId: String?, destinationPlaceId: String?) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            val newTripId = startTripUseCase(
                carId = carId,
                startPlaceId = originPlaceId,
                destinationPlaceId = destinationPlaceId,
            )
            // Nothing was started (no signed-in user) — release the guard so the driver can retry.
            // On success it stays held until the host has navigated away, in onStartHandled.
            if (newTripId == null) starting = false
            _startedTripId.value = newTripId
        }
    }
}
