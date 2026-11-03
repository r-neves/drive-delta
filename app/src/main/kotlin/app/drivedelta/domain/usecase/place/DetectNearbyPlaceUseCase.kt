package app.drivedelta.domain.usecase.place

import app.drivedelta.core.util.GeoUtils
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Finds the saved place the driver is starting from, for the pre-ride sheet's origin (F5).
 *
 * A place's radius is its *arrival* geofence, sized so the ride auto-finishes on arrival — and that
 * is the wrong test for "where am I leaving from". Drivers park down the street: measured on real
 * drives, return trips started **160–230 m** from the place pin against radii of 50–175 m (Parents
 * Home 230 m / 50 m, Bubs Parents 206 m / 100 m), so the origin never filled in on the way back.
 *
 * So: a place whose radius contains the fix wins outright (closest if several overlap). Failing
 * that, the closest place within its radius plus [PARKING_SLACK_M]. Closest-first keeps two nearby
 * places apart — standing in one, the other is never nearer.
 */
class DetectNearbyPlaceUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(lat: Double, lng: Double): Place? {
        val byDistance = repository.observePlaces().first()
            .map { it to GeoUtils.haversineMeters(lat, lng, it.lat, it.lng) }
            .sortedBy { (_, distance) -> distance }
        return (
            byDistance.firstOrNull { (place, distance) -> distance <= place.radiusMeters }
                ?: byDistance.firstOrNull { (place, distance) -> distance <= place.radiusMeters + PARKING_SLACK_M }
            )?.first
    }

    companion object {
        /** How far outside a place's circle the car can be parked and still be "leaving from" it. */
        const val PARKING_SLACK_M = 250.0
    }
}
