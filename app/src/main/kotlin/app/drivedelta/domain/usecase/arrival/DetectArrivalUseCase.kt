package app.drivedelta.domain.usecase.arrival

import android.location.Location
import app.drivedelta.domain.model.ArrivalStatus
import app.drivedelta.domain.model.Place
import javax.inject.Inject

/**
 * Debounced geofence-arrival detector (F6-B). Owns a single trip's worth of state, so a fresh
 * instance is used per trip and [reset] is called when tracking (re-)starts. Arrival requires
 * [requiredTicks] consecutive fixes inside the destination radius (≈10 continuous seconds at the
 * service's ~2 s cadence), which rejects a brief pass-through near the destination.
 */
class DetectArrivalUseCase @Inject constructor() {

    private var consecutiveTicks = 0

    fun onLocationUpdate(location: Location, destination: Place): ArrivalStatus {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            destination.lat,
            destination.lng,
            results,
        )
        val distance = results[0]

        return if (distance <= destination.radiusMeters) {
            consecutiveTicks++
            if (consecutiveTicks >= REQUIRED_TICKS) ArrivalStatus.ARRIVED else ArrivalStatus.APPROACHING
        } else {
            consecutiveTicks = 0
            ArrivalStatus.EN_ROUTE
        }
    }

    fun reset() {
        consecutiveTicks = 0
    }

    private companion object {
        const val REQUIRED_TICKS = 5 // 5 × ~2 s = ~10 continuous seconds inside the radius
    }
}
