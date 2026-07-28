package app.drivedelta.ui.tripdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.TripDetail
import app.drivedelta.domain.usecase.fuel.GetTripCostChartUseCase
import app.drivedelta.domain.usecase.fuel.TripCostChart
import app.drivedelta.domain.usecase.segment.GetTripDetailUseCase
import app.drivedelta.domain.usecase.segment.MatchSegmentsUseCase
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.EnergyPricesRepository
import app.drivedelta.domain.repository.PlaceRepository
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CompareBaseline { BEST, PREVIOUS }

data class TripDetailUiState(
    val detail: TripDetail? = null,
    val loading: Boolean = true,
    val baseline: CompareBaseline = CompareBaseline.BEST,
    val previousPerRoadKey: Map<String, Long> = emptyMap(),
    val hasPreviousRun: Boolean = false,
    val costChart: TripCostChart? = null,
    val showEnergyLog: Boolean = false,
    val replayFraction: Float = 0f,
    val isPlaying: Boolean = false,
    val replaySpeed: Int = 1,
    // Display strings for the app-bar title/subtitle (resolved from the trip's linked place/car ids).
    val originName: String? = null,
    val destName: String? = null,
    val carName: String? = null,
)

/**
 * Backs the Trip Detail screen (F10): loads the [TripDetail], computes a "previous run on this route"
 * baseline for the splits toggle, gates the first-open fuel prompt, and drives the replay scrubber.
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
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle[NavArgs.TRIP_ID])

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private var replayJob: Job? = null

    init {
        viewModelScope.launch {
            val detail = getTripDetail(tripId)
            // The most recent other trip on this route → the "vs previous" baseline.
            val previousTrip = matchSegments(tripId).firstOrNull()
            val previous = previousTrip
                ?.let { tripRepository.getSegments(it.id).associate { s -> s.roadKey to s.durationMs } }
                ?: emptyMap()
            val trip = detail?.trip
            val originName = trip?.startPlaceId?.let { placeRepository.getPlace(it)?.name }
            val destName = trip?.endPlaceId?.let { placeRepository.getPlace(it)?.name }
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
                    previousPerRoadKey = previous,
                    hasPreviousRun = previous.isNotEmpty(),
                    costChart = costChart,
                    showEnergyLog = autoAsk,
                    originName = originName,
                    destName = destName,
                    carName = carName,
                )
            }
        }
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

    // --- Replay ---------------------------------------------------------------------------------

    fun togglePlay() {
        if (_uiState.value.isPlaying) pause() else play()
    }

    fun setReplayFraction(fraction: Float) {
        _uiState.update { it.copy(replayFraction = fraction.coerceIn(0f, 1f)) }
    }

    fun cycleSpeed() = _uiState.update { it.copy(replaySpeed = if (it.replaySpeed == 1) 2 else 1) }

    private fun play() {
        val durationMs = _uiState.value.detail?.trip?.durationMs ?: return
        if (durationMs <= 0) return
        // Restart from the beginning if we're at the end.
        if (_uiState.value.replayFraction >= 1f) _uiState.update { it.copy(replayFraction = 0f) }
        _uiState.update { it.copy(isPlaying = true) }
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            while (isActive && _uiState.value.replayFraction < 1f) {
                delay(TICK_MS)
                val step = TICK_MS.toFloat() * _uiState.value.replaySpeed / durationMs
                _uiState.update { it.copy(replayFraction = (it.replayFraction + step).coerceAtMost(1f)) }
            }
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    private fun pause() {
        replayJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    private companion object {
        const val TICK_MS = 50L
    }
}
