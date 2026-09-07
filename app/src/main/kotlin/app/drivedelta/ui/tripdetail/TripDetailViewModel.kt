package app.drivedelta.ui.tripdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.postride.PostRideTrigger
import app.drivedelta.domain.model.TripDetail
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.EnergyPricesRepository
import app.drivedelta.domain.repository.PlaceRepository
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.domain.usecase.fuel.GetTripCostChartUseCase
import app.drivedelta.domain.usecase.fuel.TripCostChart
import app.drivedelta.domain.usecase.segment.GetTripDetailUseCase
import app.drivedelta.domain.usecase.segment.MatchSegmentsUseCase
import app.drivedelta.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CompareBaseline { BEST, PREVIOUS }

data class TripDetailUiState(
    val detail: TripDetail? = null,
    val loading: Boolean = true,
    val baseline: CompareBaseline = CompareBaseline.BEST,
    val previousPerRoadKey: Map<String, Long> = emptyMap(),
    val hasPreviousRun: Boolean = false,
    val costChart: TripCostChart? = null,
    val showEnergyLog: Boolean = false,
    /** Which segment the Segments tab is showing (index into `detail.segments`). */
    val selectedSegment: Int = 0,
    // Display strings for the app-bar title/subtitle (resolved from the trip's linked place/car ids).
    val originName: String? = null,
    val destName: String? = null,
    val carName: String? = null,
    // The emoji those places carry, so the map can mark the drive's ends with the icons the user
    // chose for them rather than with two identical pins.
    val originEmoji: String? = null,
    val destEmoji: String? = null,
    /** Post-ride snapping still running: the drive is open but its segments don't exist yet. */
    val processing: Boolean = false,
)

/**
 * Backs the Trip Detail screen (F10): loads the [TripDetail], computes a "previous run on this route"
 * baseline for the splits toggle, gates the first-open fuel prompt, and tracks which segment the
 * Segments tab is stepped to.
 */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTripDetail: GetTripDetailUseCase,
    private val getTripCostChart: GetTripCostChartUseCase,
    private val matchSegments: MatchSegmentsUseCase,
    private val tripRepository: TripRepository,
    private val placeRepository: PlaceRepository,
    private val carRepository: CarRepository,
    private val energyPricesRepository: EnergyPricesRepository,
    private val postRideTrigger: PostRideTrigger,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle[NavArgs.TRIP_ID])

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    init {
        load()
        // Post-ride snapping runs asynchronously in PostRideWorker, so a drive opened straight after
        // it finishes has no segments yet. Reload when they land instead of showing an empty Splits
        // tab forever. Guarded on the count so the reload's own write can't re-trigger this.
        viewModelScope.launch {
            tripRepository.observeSegments(tripId).collect { segments ->
                if (segments.size != _uiState.value.detail?.segments?.size) load()
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val detail = getTripDetail(tripId)
            // The most recent other trip on this route → the "vs previous" baseline.
            val previousTrip = matchSegments(tripId).firstOrNull()
            val previous = previousTrip
                ?.let { tripRepository.getSegments(it.id).associate { s -> s.roadKey to s.durationMs } }
                ?: emptyMap()
            val trip = detail?.trip
            val originPlace = trip?.startPlaceId?.let { placeRepository.getPlace(it) }
            val destPlace = trip?.endPlaceId?.let { placeRepository.getPlace(it) }
            val carName = trip?.carId?.let { carRepository.getCar(it)?.name }
            val costChart = getTripCostChart(tripId)
            val askAfterEveryDrive = energyPricesRepository.getPrices().askAfterEveryDrive
            // Auto-open the energy log sheet once per drive when it hasn't been logged, the "ask after
            // every drive" setting is on, and the drive has a car to attribute the fuel to.
            val autoAsk = detail != null &&
                !detail.fuelPromptDismissed &&
                detail.trip.carId != null &&
                costChart?.loggedCost == null &&
                askAfterEveryDrive
            _uiState.update {
                it.copy(
                    detail = detail,
                    loading = false,
                    processing = detail != null && detail.segments.isEmpty() && !detail.trip.roadsProcessed,
                    previousPerRoadKey = previous,
                    hasPreviousRun = previous.isNotEmpty(),
                    costChart = costChart,
                    showEnergyLog = it.showEnergyLog || autoAsk,
                    originName = originPlace?.name,
                    destName = destPlace?.name,
                    carName = carName,
                    originEmoji = originPlace?.iconEmoji,
                    destEmoji = destPlace?.iconEmoji,
                )
            }
        }
    }

    /**
     * Re-runs snapping and segment building for this drive. Segments are derived data, so a drive
     * recorded under an older segmentation algorithm keeps its old splits forever otherwise — this
     * is how an existing drive picks up improvements to how segments are cut, named and timed.
     * The screen already reloads when segments change, so there is nothing to await here.
     */
    fun recalculateSegments() {
        _uiState.update { it.copy(processing = true) }
        postRideTrigger.requestProcessing(tripId, replaceExisting = true)
    }

    fun setBaseline(baseline: CompareBaseline) = _uiState.update { it.copy(baseline = baseline) }

    /** Deletes this ride (soft-delete + Firestore sync + Room removal); caller navigates back. */
    fun deleteTrip(onDeleted: () -> Unit) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
            onDeleted()
        }
    }

    /** Opens the energy log sheet from the "Fuel not logged" banner (Add). */
    fun openEnergyLog() = _uiState.update { it.copy(showEnergyLog = true) }

    /** Dismisses the sheet without saving; won't auto-reopen for this drive again. */
    fun dismissEnergyLog() {
        _uiState.update { it.copy(showEnergyLog = false) }
        viewModelScope.launch { tripRepository.markFuelPromptDismissed(tripId) }
    }

    /** Called after a fuel log is saved: hide the sheet and reload this drive's cost + chart. */
    fun onEnergyLogged() {
        viewModelScope.launch {
            val costChart = getTripCostChart(tripId)
            _uiState.update { it.copy(showEnergyLog = false, costChart = costChart) }
        }
    }

    // --- Segments -------------------------------------------------------------------------------

    /** Selects a segment to light on the map and describe in the panel. Clamped to what exists. */
    fun selectSegment(index: Int) = _uiState.update {
        val last = (it.detail?.segments?.size ?: 0) - 1
        it.copy(selectedSegment = index.coerceIn(0, maxOf(0, last)))
    }

    fun stepSegment(by: Int) = selectSegment(_uiState.value.selectedSegment + by)
}
