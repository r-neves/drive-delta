package app.drivedelta.ui.tripdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.TripDetail
import app.drivedelta.domain.usecase.segment.GetTripDetailUseCase
import app.drivedelta.domain.usecase.segment.MatchSegmentsUseCase
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
    val showFuelPrompt: Boolean = false,
    val replayFraction: Float = 0f,
    val isPlaying: Boolean = false,
    val replaySpeed: Int = 1,
)

/**
 * Backs the Trip Detail screen (F10): loads the [TripDetail], computes a "previous run on this route"
 * baseline for the splits toggle, gates the first-open fuel prompt, and drives the replay scrubber.
 */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTripDetail: GetTripDetailUseCase,
    private val matchSegments: MatchSegmentsUseCase,
    private val tripRepository: TripRepository,
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
            _uiState.update {
                it.copy(
                    detail = detail,
                    loading = false,
                    previousPerRoadKey = previous,
                    hasPreviousRun = previous.isNotEmpty(),
                    showFuelPrompt = detail?.let { d -> !d.fuelPromptDismissed && d.trip.carId != null } ?: false,
                )
            }
        }
    }

    fun setBaseline(baseline: CompareBaseline) = _uiState.update { it.copy(baseline = baseline) }

    fun dismissFuelPrompt() {
        _uiState.update { it.copy(showFuelPrompt = false) }
        viewModelScope.launch { tripRepository.markFuelPromptDismissed(tripId) }
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
