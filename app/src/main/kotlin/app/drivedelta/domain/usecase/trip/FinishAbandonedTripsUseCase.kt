package app.drivedelta.domain.usecase.trip

import app.drivedelta.core.postride.PostRideTrigger
import app.drivedelta.core.util.GeoUtils
import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.repository.TripRepository
import javax.inject.Inject

/**
 * Closes out rides that were started and never finished.
 *
 * A ride is only meant to be open while it is being recorded. If the process is killed mid-drive —
 * the service is `START_NOT_STICKY`, so Android is free to do that on a long drive — or the app is
 * force-stopped, or a ride is started by accident and abandoned, the trip keeps `endTime = null`
 * forever. Nothing then finishes it: the Trips list shows only completed rides, so the trip is
 * **invisible and undeletable from inside the app**, and its route points sit on the device
 * counting for nothing. One such ride had to be reached by editing Room by hand.
 *
 * The recording already happened, so a ride worth keeping is salvaged rather than discarded: it is
 * closed at its last fix and handed to the normal post-ride pipeline, and turns up in Trips like any
 * other drive. A ride with nothing recorded is deleted — starting a ride by accident should not
 * leave a 0.0 km entry in your history.
 *
 * Runs on cold start, which is the only moment the answer is unambiguous: our tracking service is
 * `START_NOT_STICKY` and never survives process death, so nothing can be recording yet. Staleness is
 * still required on top of that, so a live ride can never be closed underneath the service by some
 * future caller.
 */
class FinishAbandonedTripsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val postRideTrigger: PostRideTrigger,
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()) {
        for (trip in tripRepository.getUnfinishedTrips()) {
            val points = tripRepository.getRoutePoints(trip.id)
            val lastSeen = points.lastOrNull()?.timestamp ?: trip.startTime
            // Recent activity means this could still be a live recording. Leave it alone.
            if (now - lastSeen < STALE_AFTER_MS) continue

            val distance = pathMeters(points)
            if (points.size < 2 || distance < MIN_SALVAGEABLE_METERS) {
                tripRepository.deleteTrip(trip.id)
                continue
            }

            val last = points.last()
            tripRepository.finishTrip(
                tripId = trip.id,
                endTime = last.timestamp,
                endLat = last.lat,
                endLng = last.lng,
                distanceMeters = distance.toFloat(),
                // Measured from the trip's own start, as a normal finish does, so an abandoned ride's
                // duration means the same thing as every other ride's.
                durationMs = (last.timestamp - trip.startTime).coerceAtLeast(0L),
                stopTrigger = ABANDONED,
            )
            // Snap and segment it like any other drive; it is a real ride, it just ended badly.
            postRideTrigger.requestProcessing(trip.id)
        }
    }

    private fun pathMeters(points: List<RoutePoint>): Double {
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += GeoUtils.haversineMeters(
                points[i - 1].lat, points[i - 1].lng, points[i].lat, points[i].lng,
            )
        }
        return distance
    }

    private companion object {
        /** Comfortably longer than any gap the tracking service tolerates while recording. */
        const val STALE_AFTER_MS = 15 * 60 * 1000L

        /** Below this there is no drive to keep — a tap on Start Ride, and a change of mind. */
        const val MIN_SALVAGEABLE_METERS = 100.0

        const val ABANDONED = "ABANDONED"
    }
}
