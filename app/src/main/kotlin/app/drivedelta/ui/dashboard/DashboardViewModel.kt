package app.drivedelta.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Dashboard state. Exposes recent completed trips as an entry point into Trip Detail (a lightweight
 * stand-in until the full F13 dashboard — recent rides, personal bests, weekly stats — lands in
 * Checkpoint 9). Sign-out stays here until a Settings surface exists.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    tripRepository: TripRepository,
) : ViewModel() {

    val recentTrips: StateFlow<List<Trip>> = tripRepository.observeTrips()
        .map { trips -> trips.filter { it.endTime != null }.take(RECENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun signOut() {
        authRepository.signOut()
    }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
