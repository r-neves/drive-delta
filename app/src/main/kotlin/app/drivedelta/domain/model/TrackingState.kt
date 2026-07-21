package app.drivedelta.domain.model

import android.location.Location

/**
 * Live snapshot of an in-progress trip, streamed from [app.drivedelta.service.TrackingForegroundService]
 * and (from Checkpoint 6) collected by the tracking screen. The fields the HUD needs but that only
 * become meaningful once Roads snapping lands (`currentRoadName`, segment/best times) stay at their
 * defaults for Checkpoint 5.
 */
data class TrackingState(
    val isTracking: Boolean = false,
    val currentLocation: Location? = null,
    val elapsedMs: Long = 0L,
    val distanceMeters: Float = 0f,
    val currentSpeedKph: Float = 0f,
    val currentRoadName: String? = null,
    val currentSegmentElapsedMs: Long = 0L,
    val bestSegmentMs: Long? = null,
    val arrivalStatus: ArrivalStatus = ArrivalStatus.EN_ROUTE,
    val destinationName: String? = null,          // null when no destination set
    val distanceToDestinationMeters: Float? = null,
)

/** Where the driver is relative to the trip's destination geofence. */
enum class ArrivalStatus { EN_ROUTE, APPROACHING, ARRIVED }
