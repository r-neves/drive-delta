package app.drivedelta.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.domain.usecase.trip.StartTripUseCase
import app.drivedelta.domain.usecase.trip.StopTripUseCase
import app.drivedelta.service.TrackingForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Checkpoint 1 stub, extended in Checkpoint 5 with a temporary tracking test harness (Start/Stop
 * test trip + a recorded-points summary) so the GPS service can be exercised without the Live
 * Tracking screen. The real Start Ride flow and dashboard content arrive in Checkpoints 6 and 9;
 * this control block is removed then.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val startTripUseCase: StartTripUseCase,
    private val stopTripUseCase: StopTripUseCase,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    /** Recap of the last recorded trip, shown after a test trip stops (formatted by the screen). */
    private val _lastTripSummary = MutableStateFlow<SummaryData?>(null)
    val lastTripSummary: StateFlow<SummaryData?> = _lastTripSummary.asStateFlow()

    private var currentTripId: String? = null

    fun startTestTrip() {
        if (_isTracking.value) return
        viewModelScope.launch {
            val tripId = startTripUseCase() ?: return@launch
            currentTripId = tripId
            _lastTripSummary.value = null
            _isTracking.value = true
        }
    }

    fun stopTestTrip() {
        if (!_isTracking.value) return
        stopTripUseCase(TrackingForegroundService.TRIGGER_MANUAL)
        _isTracking.value = false
        val tripId = currentTripId ?: return
        viewModelScope.launch { loadSummary(tripId) }
    }

    /**
     * Polls until the service has finalised the trip (end time stamped, buffer flushed), then
     * summarises the recorded points. The service finishes asynchronously after the STOP intent, so
     * a short poll avoids reading a half-written trip.
     */
    private suspend fun loadSummary(tripId: String) {
        var trip = tripRepository.getTrip(tripId)
        var attempts = 0
        while (trip?.endTime == null && attempts < MAX_SUMMARY_POLLS) {
            delay(SUMMARY_POLL_MS)
            trip = tripRepository.getTrip(tripId)
            attempts++
        }
        val points = tripRepository.getRoutePoints(tripId)
        val interpolated = points.count { it.isInterpolated }
        val km = (trip?.distanceMeters ?: 0f) / 1000f
        _lastTripSummary.value = SummaryData(points.size, interpolated, km)
    }

    /** Signs out of Firebase. Room clearing is added with the full sign-out flow in Checkpoint 9. */
    fun signOut() {
        authRepository.signOut()
    }

    /** Carries the numbers the screen formats via a string resource. */
    data class SummaryData(val pointCount: Int, val interpolatedCount: Int, val distanceKm: Float)

    private companion object {
        const val SUMMARY_POLL_MS = 300L
        const val MAX_SUMMARY_POLLS = 20 // ~6 s ceiling waiting for the service to finalise
    }
}
