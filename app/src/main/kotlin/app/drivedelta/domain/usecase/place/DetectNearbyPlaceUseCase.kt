package app.drivedelta.domain.usecase.place

import app.drivedelta.core.util.GeoUtils
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Finds the saved place whose geofence currently contains ([lat], [lng]), if any — used by the
 * pre-ride sheet to suggest an origin/destination (F5). Returns the closest match when several
 * overlap. Not surfaced in the Places UI itself; wired into tracking from Checkpoint 6.
 */
class DetectNearbyPlaceUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(lat: Double, lng: Double): Place? =
        repository.observePlaces().first()
            .map { it to GeoUtils.haversineMeters(lat, lng, it.lat, it.lng) }
            .filter { (place, distance) -> distance <= place.radiusMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
}
