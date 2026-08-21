package app.drivedelta.ui.tracking

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.util.GeoUtils
import app.drivedelta.domain.model.TrackingState
import app.drivedelta.domain.usecase.trip.StopTripUseCase
import app.drivedelta.service.TrackingForegroundService
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Live Tracking screen (F9). Binds to the already-running [TrackingForegroundService] and
 * mirrors its [TrackingState], accumulating a live polyline from each distinct fix and a camera
 * target throttled to ~3 s so the map doesn't jitter. Emits [finishedTripId] once the service reports
 * the trip finished (manual STOP or geofence auto-finish) so the screen can open that drive.
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

    /**
     * Heading to point the map at, in degrees clockwise from north.
     *
     * Derived from the movement between consecutive fixes rather than read off `Location.bearing`:
     * plenty of providers never populate it (the Android emulator reports `bear=0.0 vel=0.0` for
     * every injected fix, so a bearing-based map would simply never rotate), and computing it from
     * positions we already trust works the same everywhere.
     *
     * Only recomputed once the driver has actually moved [MIN_BEARING_DISTANCE_M]; below that the
     * heading between two fixes is GPS noise and the map would pirouette at traffic lights.
     */
    private val _cameraBearing = MutableStateFlow(0f)
    val cameraBearing: StateFlow<Float> = _cameraBearing.asStateFlow()

    /** Non-null once the ride has finished: the id of the drive that just ended. */
    private val _finishedTripId = MutableStateFlow<String?>(null)
    val finishedTripId: StateFlow<String?> = _finishedTripId.asStateFlow()

    /** True from the moment STOP is requested, so the UI can acknowledge the tap immediately. */
    private val _finishing = MutableStateFlow(false)
    val finishing: StateFlow<Boolean> = _finishing.asStateFlow()

    /** Last fix the heading was measured from; only advances once the driver has really moved. */
    private var bearingAnchor: LatLng? = null

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
            bearingAnchor?.let { previous ->
                val moved = GeoUtils.haversineMeters(
                    previous.latitude, previous.longitude, point.latitude, point.longitude,
                )
                if (moved >= MIN_BEARING_DISTANCE_M) {
                    _cameraBearing.value = GeoUtils.bearingDegrees(
                        previous.latitude, previous.longitude, point.latitude, point.longitude,
                    ).toFloat()
                    bearingAnchor = point
                }
            } ?: run { bearingAnchor = point }
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
            _finishedTripId.value = newState.finishedTripId ?: ""
        }
        wasTracking = wasTracking || newState.isTracking
    }

    /**
     * Finish the trip. [trigger] is [TrackingForegroundService.TRIGGER_MANUAL] or `TRIGGER_GEOFENCE`.
     *
     * Flips [finishing] straight away: the underlying call only fires an intent at the service and
     * writes no state, so without this the composition after the tap was byte-for-byte identical and
     * the UI looked dead. Guarded so a second tap (or the arrival countdown expiring on a sheet the
     * user already confirmed) can't deliver a duplicate STOP.
     */
    fun stop(trigger: String) {
        if (_finishing.value) return
        _finishing.value = true
        stopTripUseCase(trigger)
        // Safety net. The service confirms by flipping isTracking true → false, but that transition
        // never arrives if it wasn't tracking in the first place — binding with BIND_AUTO_CREATE
        // instantiates the service without ever delivering ACTION_START, so its first emission is a
        // default TrackingState(isTracking = false), `wasTracking` stays false, and no later `false`
        // counts as a finish. Observed on the emulator: the sheet sat on "Finishing…" indefinitely.
        // Leaving the driver stranded on a dead tracking screen is the worst outcome here, so give
        // the service a moment and then leave anyway.
        viewModelScope.launch {
            delay(STOP_CONFIRM_TIMEOUT_MS)
            if (_finishedTripId.value == null) _finishedTripId.value = ""
        }
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

        /** How long to wait for the service to confirm a stop before navigating away regardless. */
        const val STOP_CONFIRM_TIMEOUT_MS = 8_000L

        /** Below this the heading between two fixes is noise, so the map holds its last heading. */
        const val MIN_BEARING_DISTANCE_M = 15.0
    }
}
