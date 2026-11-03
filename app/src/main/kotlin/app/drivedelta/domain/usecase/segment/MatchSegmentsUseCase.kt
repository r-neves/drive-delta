package app.drivedelta.domain.usecase.segment

import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Finds the user's other completed trips comparable to [tripId] (F8). A trip matches if it is on the
 * same route ([Trip.routeKey] — the same origin→destination places), OR it shares ≥ [MIN_OVERLAP] of
 * its roadKey set (so "best time on A1 Norte" surfaces even across trips with different start/end
 * places). Same-route trips come first, each group newest first.
 */
class MatchSegmentsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend operator fun invoke(tripId: String): List<Trip> {
        val target = tripRepository.getTrip(tripId) ?: return emptyList()
        val targetKeys = tripRepository.getSegments(tripId).map { it.roadKey }.toSet()

        val candidates = tripRepository.observeTrips().first()
            .filter { it.id != tripId && it.endTime != null }

        val sameRoute = target.routeKey
            ?.let { key -> candidates.filter { it.routeKey == key } }
            .orEmpty()
        val overlapping = if (targetKeys.isEmpty()) emptyList() else candidates.filter { candidate ->
            if (candidate in sameRoute) return@filter false
            val keys = tripRepository.getSegments(candidate.id).map { it.roadKey }.toSet()
            if (keys.isEmpty()) return@filter false
            val shared = keys.count { it in targetKeys }
            shared.toDouble() / keys.size >= MIN_OVERLAP
        }
        return sameRoute.sortedByDescending { it.startTime } + overlapping.sortedByDescending { it.startTime }
    }

    private companion object {
        const val MIN_OVERLAP = 0.70
    }
}
