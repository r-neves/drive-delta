package app.drivedelta.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.drivedelta.MainActivity
import app.drivedelta.core.location.LocationProvider
import app.drivedelta.core.util.GeoUtils
import app.drivedelta.domain.model.ArrivalStatus
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.model.TrackingState
import app.drivedelta.domain.repository.PlaceRepository
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.domain.usecase.arrival.DetectArrivalUseCase
import app.drivedelta.domain.usecase.segment.BuildSegmentsUseCase
import app.drivedelta.domain.usecase.segment.SnapRouteToRoadsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Foreground service that records a trip's GPS trace (F4). It owns all live recording state — the
 * point buffer, running distance, elapsed time and arrival status — and streams a [TrackingState]
 * that the tracking screen binds to (Checkpoint 6). Started/stopped via [start]/[stop]; the trip row
 * must already exist in Room (created by StartTripUseCase) before START is delivered.
 *
 * Pipeline per accepted fix: warm-up filter (drop the first 10 s) → accuracy filter (drop > 25 m) →
 * GPS-gap fill (interpolate when a fix is > 8 s late) → append to an in-memory buffer flushed to
 * Room every 30 s. Timestamps stored on points use wall-clock epoch ms; gap/warm-up timing uses the
 * monotonic [SystemClock.elapsedRealtime] so a wall-clock adjustment can't corrupt it.
 */
@AndroidEntryPoint
class TrackingForegroundService : Service() {

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var placeRepository: PlaceRepository
    @Inject lateinit var detectArrival: DetectArrivalUseCase
    @Inject lateinit var snapRouteToRoads: SnapRouteToRoadsUseCase
    @Inject lateinit var buildSegments: BuildSegmentsUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val binder = TrackingBinder()

    // Recording state. Reset on each START, then written only from the single location coroutine.
    @Volatile private var tripId: String? = null
    @Volatile private var destination: Place? = null
    @Volatile private var recordingStartEpoch = 0L
    @Volatile private var warmupUntilElapsed = 0L
    @Volatile private var totalDistanceM = 0f
    @Volatile private var startBackfilled = false

    private var lastValidLocation: Location? = null
    private var lastValidElapsed = 0L
    private var lastValidEpoch = 0L

    private val buffer = mutableListOf<RoutePoint>()
    private val bufferMutex = Mutex()

    private var locationJob: Job? = null
    private var flushJob: Job? = null
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class TrackingBinder : Binder() {
        fun service(): TrackingForegroundService = this@TrackingForegroundService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Promote to foreground immediately to avoid the start-timeout ANR, then record.
                startForegroundCompat(buildNotification(getString(app.drivedelta.R.string.tracking_notification_starting)))
                val id = intent.getStringExtra(EXTRA_TRIP_ID)
                if (id == null) {
                    stopSelfCleanly()
                } else {
                    startTracking(id, intent.getStringExtra(EXTRA_DEST_PLACE_ID))
                }
            }

            ACTION_STOP -> stopTracking(intent.getStringExtra(EXTRA_TRIGGER) ?: TRIGGER_MANUAL)

            else -> stopSelfCleanly()
        }
        // Don't auto-restart with a stale intent: mid-trip process death is out of scope for the POC.
        return START_NOT_STICKY
    }

    private fun startTracking(tripId: String, destinationPlaceId: String?) {
        this.tripId = tripId
        recordingStartEpoch = System.currentTimeMillis()
        warmupUntilElapsed = SystemClock.elapsedRealtime() + WARMUP_MS
        totalDistanceM = 0f
        startBackfilled = false
        lastValidLocation = null
        destination = null
        detectArrival.reset()
        _trackingState.value = TrackingState(isTracking = true)

        if (destinationPlaceId != null) {
            serviceScope.launch {
                val place = placeRepository.getPlace(destinationPlaceId)
                destination = place
                if (place != null) {
                    _trackingState.update { it.copy(destinationName = place.name) }
                }
            }
        }

        locationJob = serviceScope.launch {
            locationProvider.locationUpdates().collect { onLocation(it) }
        }
        flushJob = serviceScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushBuffer()
            }
        }
        notificationJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(NOTIFICATION_INTERVAL_MS)
            }
        }
    }

    private suspend fun onLocation(location: Location) {
        val nowElapsed = SystemClock.elapsedRealtime()
        // Warm-up: discard everything for the first 10 s so the GPS can settle.
        if (nowElapsed < warmupUntilElapsed) return
        // Accuracy filter: drop fixes with no accuracy or worse than 25 m.
        if (!location.hasAccuracy() || location.accuracy > MAX_ACCURACY_M) return

        val nowEpoch = System.currentTimeMillis()
        val id = tripId ?: return

        val previous = lastValidLocation
        if (previous != null) {
            val gapMs = nowElapsed - lastValidElapsed
            if (gapMs > GAP_THRESHOLD_MS) {
                fillGap(id, previous, lastValidEpoch, location, gapMs)
            }
            totalDistanceM += previous.distanceTo(location)
        }

        addPoint(
            RoutePoint(
                tripId = id,
                timestamp = nowEpoch,
                lat = location.latitude,
                lng = location.longitude,
                accuracyMeters = location.accuracy,
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                altitudeMeters = if (location.hasAltitude()) location.altitude else 0.0,
                isInterpolated = false,
            ),
        )

        // The last-known snapshot in StartTripUseCase can be stale or absent; the first real fix is
        // the truer start, so backfill the trip's start coordinates once.
        if (!startBackfilled) {
            startBackfilled = true
            tripRepository.updateStartLocation(id, location.latitude, location.longitude)
        }

        lastValidLocation = location
        lastValidElapsed = nowElapsed
        lastValidEpoch = nowEpoch

        destination?.let { dest ->
            val status = detectArrival.onLocationUpdate(location, dest)
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, dest.lat, dest.lng, results)
            _trackingState.update {
                it.copy(arrivalStatus = status, distanceToDestinationMeters = results[0])
            }
        }

        _trackingState.update {
            it.copy(
                isTracking = true,
                currentLocation = location,
                elapsedMs = nowEpoch - recordingStartEpoch,
                distanceMeters = totalDistanceM,
                currentSpeedKph = if (location.hasSpeed()) location.speed * MPS_TO_KPH else 0f,
            )
        }
    }

    /**
     * Bridges a GPS gap (> 8 s since the last valid fix) between [previous] and [next]. For a gap up
     * to 30 s, inserts interpolated points at ~2 s spacing marked [RoutePoint.isInterpolated]; for a
     * longer gap, inserts a single interpolated gap-marker at the midpoint and lets the next real fix
     * resume the trace (F4).
     */
    private suspend fun fillGap(
        tripId: String,
        previous: Location,
        previousEpoch: Long,
        next: Location,
        gapMs: Long,
    ) {
        if (gapMs > MAX_INTERPOLATION_MS) {
            val (lat, lng) = GeoUtils.interpolate(
                previous.latitude, previous.longitude, next.latitude, next.longitude, 0.5,
            )
            addPoint(
                RoutePoint(
                    tripId = tripId,
                    timestamp = previousEpoch + gapMs / 2,
                    lat = lat,
                    lng = lng,
                    accuracyMeters = next.accuracy,
                    speedMps = 0f,
                    altitudeMeters = if (next.hasAltitude()) next.altitude else 0.0,
                    isInterpolated = true,
                ),
            )
            return
        }

        val steps = (gapMs / INTERPOLATION_STEP_MS).toInt()
        val prevSpeed = if (previous.hasSpeed()) previous.speed else 0f
        val nextSpeed = if (next.hasSpeed()) next.speed else 0f
        val interpolatedSpeed = (prevSpeed + nextSpeed) / 2f
        for (i in 1 until steps) {
            val fraction = i.toDouble() / steps
            val (lat, lng) = GeoUtils.interpolate(
                previous.latitude, previous.longitude, next.latitude, next.longitude, fraction,
            )
            addPoint(
                RoutePoint(
                    tripId = tripId,
                    timestamp = previousEpoch + gapMs * i / steps,
                    lat = lat,
                    lng = lng,
                    accuracyMeters = next.accuracy,
                    speedMps = interpolatedSpeed,
                    altitudeMeters = if (next.hasAltitude()) next.altitude else 0.0,
                    isInterpolated = true,
                ),
            )
        }
    }

    private suspend fun addPoint(point: RoutePoint) {
        bufferMutex.withLock { buffer.add(point) }
    }

    private suspend fun flushBuffer() {
        val batch = bufferMutex.withLock {
            if (buffer.isEmpty()) emptyList() else buffer.toList().also { buffer.clear() }
        }
        if (batch.isNotEmpty()) tripRepository.appendRoutePoints(batch)
    }

    private fun stopTracking(trigger: String) {
        val id = tripId
        locationJob?.cancel()
        flushJob?.cancel()
        notificationJob?.cancel()

        serviceScope.launch {
            flushBuffer()
            if (id != null) {
                val end = lastValidLocation
                val endEpoch = System.currentTimeMillis()
                tripRepository.finishTrip(
                    tripId = id,
                    endTime = endEpoch,
                    endLat = end?.latitude ?: 0.0,
                    endLng = end?.longitude ?: 0.0,
                    distanceMeters = totalDistanceM,
                    durationMs = endEpoch - recordingStartEpoch,
                    stopTrigger = trigger,
                )
                // Post-ride (F7): snap to roads, then build named segments. On Roads API failure the
                // snap returns null and segment building falls back to raw 500 m chunks. Best-effort:
                // a failure here must not block the service from stopping.
                runCatching {
                    val snapped = snapRouteToRoads(id)
                    buildSegments(id, snapped)
                }
            }
            _trackingState.update { it.copy(isTracking = false) }
            stopSelfCleanly()
        }
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Notification ---------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(app.drivedelta.R.string.tracking_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val state = _trackingState.value
        val text = getString(
            app.drivedelta.R.string.tracking_notification_body,
            formatElapsed(state.elapsedMs),
            state.currentSpeedKph.roundToInt(),
            state.distanceMeters / 1000f,
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(contentText: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(app.drivedelta.R.string.tracking_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    @SuppressLint("InlinedApi") // FOREGROUND_SERVICE_TYPE_LOCATION is ignored by ServiceCompat < API 29.
    private fun startForegroundCompat(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    companion object {
        private const val CHANNEL_ID = "drivedelta_tracking"
        private const val NOTIFICATION_ID = 42

        private const val ACTION_START = "app.drivedelta.action.START_TRACKING"
        private const val ACTION_STOP = "app.drivedelta.action.STOP_TRACKING"
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val EXTRA_DEST_PLACE_ID = "dest_place_id"
        private const val EXTRA_TRIGGER = "trigger"

        const val TRIGGER_MANUAL = "MANUAL"
        const val TRIGGER_GEOFENCE = "GEOFENCE"

        private const val WARMUP_MS = 10_000L
        private const val MAX_ACCURACY_M = 25f
        private const val GAP_THRESHOLD_MS = 8_000L
        private const val MAX_INTERPOLATION_MS = 30_000L
        private const val INTERPOLATION_STEP_MS = 2_000L
        private const val FLUSH_INTERVAL_MS = 30_000L
        private const val NOTIFICATION_INTERVAL_MS = 5_000L
        private const val MPS_TO_KPH = 3.6f

        /** Starts recording [tripId]; the trip row must already exist in Room. */
        fun start(context: Context, tripId: String, destinationPlaceId: String?) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
                putExtra(EXTRA_DEST_PLACE_ID, destinationPlaceId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Signals the running service to finalise the trip with [trigger] ("MANUAL"/"GEOFENCE"). */
        fun stop(context: Context, trigger: String) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_TRIGGER, trigger)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
