package app.drivedelta.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.data.local.AppDatabase
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Dashboard state. Exposes recent completed trips as an entry point into Trip Detail (a lightweight
 * stand-in until the full F13 dashboard — recent rides, personal bests, weekly stats — lands in
 * Checkpoint 9). Sign-out stays here until a Settings surface exists.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val database: AppDatabase,
    tripRepository: TripRepository,
) : ViewModel() {

    val recentTrips: StateFlow<List<Trip>> = tripRepository.observeTrips()
        .map { trips -> trips.filter { it.endTime != null }.take(RECENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Signs out of Firebase and clears the local cache so the next user starts clean (F1). Single
     * user per install for the POC, so clearing all tables is equivalent to deleting this user's rows.
     */
    fun signOut() {
        authRepository.signOut()
        viewModelScope.launch { withContext(Dispatchers.IO) { database.clearAllTables() } }
    }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
