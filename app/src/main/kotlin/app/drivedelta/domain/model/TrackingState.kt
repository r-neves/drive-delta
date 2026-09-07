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
    // The saved places the ride runs between, when the driver picked them. Whole places rather than
    // just the destination's name: the live map draws the origin's own icon and the destination's
    // real geofence circle, so it needs their coordinates, radius and emoji too.
    val originPlace: Place? = null,
    val destinationPlace: Place? = null,
    val distanceToDestinationMeters: Float? = null,
    // Set once the trip has been finalised in Room, alongside isTracking = false. Lets the tracking
    // screen open the drive that just finished instead of dropping the user on the dashboard.
    val finishedTripId: String? = null,
) {
    val destinationName: String? get() = destinationPlace?.name
}

/** Where the driver is relative to the trip's destination geofence. */
enum class ArrivalStatus { EN_ROUTE, APPROACHING, ARRIVED }
