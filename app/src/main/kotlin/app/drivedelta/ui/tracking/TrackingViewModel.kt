package app.drivedelta.ui.tracking

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.TrackingState
import app.drivedelta.domain.usecase.trip.StopTripUseCase
import app.drivedelta.service.TrackingForegroundService
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Live Tracking screen (F9). Binds to the already-running [TrackingForegroundService] and
 * mirrors its [TrackingState], accumulating a live polyline from each distinct fix and a camera
 * target throttled to ~3 s so the map doesn't jitter. Emits [tripEnded] once the service reports the
 * trip finished (manual STOP or geofence auto-finish) so the screen can navigate away.
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stopTripUseCase: StopTripUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _routePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val routePoints: StateFlow<List<LatLng>> = _routePoints.asStateFlow()

    private val _cameraTarget = MutableStateFlow<LatLng?>(null)
    val cameraTarget: StateFlow<LatLng?> = _cameraTarget.asStateFlow()

    private val _tripEnded = MutableStateFlow(false)
    val tripEnded: StateFlow<Boolean> = _tripEnded.asStateFlow()

    private var collectJob: Job? = null
    private var wasTracking = false
    private var lastCameraMoveMs = 0L
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? TrackingForegroundService.TrackingBinder)?.service() ?: return
            collectJob?.cancel()
            collectJob = viewModelScope.launch {
                service.trackingState.collect { onState(it) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            collectJob?.cancel()
        }
    }

    init {
        bound = context.bindService(
            Intent(context, TrackingForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun onState(newState: TrackingState) {
        _state.value = newState

        newState.currentLocation?.let { location ->
            val point = LatLng(location.latitude, location.longitude)
            val current = _routePoints.value
            if (current.isEmpty() || current.last() != point) {
                _routePoints.value = current + point
            }
            val now = System.currentTimeMillis()
            if (_cameraTarget.value == null || now - lastCameraMoveMs >= CAMERA_THROTTLE_MS) {
                _cameraTarget.value = point
                lastCameraMoveMs = now
            }
        }

        if (wasTracking && !newState.isTracking) {
            _tripEnded.value = true
        }
        wasTracking = wasTracking || newState.isTracking
    }

    /** Finish the trip. [trigger] is [TrackingForegroundService.TRIGGER_MANUAL] or `TRIGGER_GEOFENCE`. */
    fun stop(trigger: String) {
        stopTripUseCase(trigger)
    }

    override fun onCleared() {
        collectJob?.cancel()
        if (bound) {
            runCatching { context.unbindService(connection) }
            bound = false
        }
        super.onCleared()
    }

    private companion object {
        const val CAMERA_THROTTLE_MS = 3_000L
    }
}
