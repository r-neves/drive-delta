package app.drivedelta.domain.usecase.segment

import app.drivedelta.domain.model.TripDetail
import app.drivedelta.domain.repository.TripRepository
import javax.inject.Inject

/**
 * Assembles the Trip Detail payload (F10): the trip, its segments, raw route points, and the
 * best-ever duration per roadKey across the user's trips (for the "vs best" split column). Returns
 * null if the trip is missing / not owned by the current user.
 */
class GetTripDetailUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend operator fun invoke(tripId: String): TripDetail? {
        val trip = tripRepository.getTrip(tripId) ?: return null
        val segments = tripRepository.getSegments(tripId)
        val routePoints = tripRepository.getRoutePoints(tripId)
        val bestPerRoadKey = segments.associate { segment ->
            segment.roadKey to (tripRepository.bestSegmentDuration(segment.roadKey) ?: segment.durationMs)
        }
        return TripDetail(
            trip = trip,
            segments = segments,
            routePoints = routePoints,
            bestPerRoadKey = bestPerRoadKey,
            fuelPromptDismissed = trip.notes.contains(FUEL_DISMISSED_FLAG),
        )
    }

    private companion object {
        const val FUEL_DISMISSED_FLAG = "fuel_dismissed"
    }
}
