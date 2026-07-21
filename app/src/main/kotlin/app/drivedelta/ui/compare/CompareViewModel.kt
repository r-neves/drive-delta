package app.drivedelta.ui.compare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.SegmentComparison
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.usecase.segment.CompareSegmentsUseCase
import app.drivedelta.domain.usecase.segment.MatchSegmentsUseCase
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompareUiState(
    val loading: Boolean = true,
    val tripA: Trip? = null,
    val candidates: List<Trip> = emptyList(),
    val selectedB: Trip? = null,
    val comparisons: List<SegmentComparison> = emptyList(),
)

/**
 * Backs the Compare screen (F8): trip A comes from the nav arg, candidate trips are the matching
 * runs on the same route, and picking trip B produces the per-segment comparison used by the chart
 * and table. Auto-selects the most recent matching trip so the screen opens with a comparison.
 */
@HiltViewModel
class CompareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val matchSegments: MatchSegmentsUseCase,
    private val compareSegments: CompareSegmentsUseCase,
) : ViewModel() {

    private val tripAId: String = checkNotNull(savedStateHandle[NavArgs.TRIP_ID])

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tripA = tripRepository.getTrip(tripAId)
            val candidates = matchSegments(tripAId)
            _uiState.update { it.copy(loading = false, tripA = tripA, candidates = candidates) }
            candidates.firstOrNull()?.let { selectB(it) }
        }
    }

    fun selectB(trip: Trip) {
        viewModelScope.launch {
            val comparisons = compareSegments(tripAId, trip.id)
            _uiState.update { it.copy(selectedB = trip, comparisons = comparisons) }
        }
    }
}
