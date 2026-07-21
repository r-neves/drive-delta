package app.drivedelta.domain.usecase.segment

import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Finds the user's other completed trips comparable to [tripId] (F8) — i.e. the same route. A trip
 * matches if its `routeHash` is identical, OR it shares ≥ [MIN_OVERLAP] of its roadKey set (so
 * "best time on A1 Norte" surfaces even across trips with different start/end places). Ordered by
 * start time (newest first).
 */
class MatchSegmentsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend operator fun invoke(tripId: String): List<Trip> {
        val target = tripRepository.getTrip(tripId) ?: return emptyList()
        val targetKeys = tripRepository.getSegments(tripId).map { it.roadKey }.toSet()
        if (targetKeys.isEmpty()) return emptyList()

        val candidates = tripRepository.observeTrips().first()
            .filter { it.id != tripId && it.endTime != null }

        return candidates.filter { candidate ->
            if (candidate.routeHash.isNotBlank() && candidate.routeHash == target.routeHash) return@filter true
            val keys = tripRepository.getSegments(candidate.id).map { it.roadKey }.toSet()
            if (keys.isEmpty()) return@filter false
            val shared = keys.count { it in targetKeys }
            shared.toDouble() / keys.size >= MIN_OVERLAP
        }
    }

    private companion object {
        const val MIN_OVERLAP = 0.70
    }
}
