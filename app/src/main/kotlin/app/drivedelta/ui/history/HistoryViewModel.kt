package app.drivedelta.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Backs the History screen (F11): the user's completed trips grouped by month header (newest first).
 * Filter chips / date range from the plan are deferred; grouped list + delete are the core.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    /** Ordered (monthLabel -> trips) for that month, newest month first. */
    val groupedTrips: StateFlow<List<Pair<String, List<Trip>>>> = tripRepository.observeTrips()
        .map { trips ->
            trips.filter { it.endTime != null }
                .groupBy { monthFormat.format(Date(it.startTime)) }
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteTrip(tripId: String) {
        viewModelScope.launch { tripRepository.deleteTrip(tripId) }
    }
}
