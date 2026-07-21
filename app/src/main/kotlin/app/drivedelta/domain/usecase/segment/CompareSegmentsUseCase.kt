package app.drivedelta.domain.usecase.segment

import app.drivedelta.domain.model.ComparisonWinner
import app.drivedelta.domain.model.SegmentComparison
import app.drivedelta.domain.repository.TripRepository
import javax.inject.Inject

/**
 * Aligns two trips' segments by roadKey and produces per-segment comparisons (F8). The ordering
 * follows trip A's segments, then any roadKeys unique to trip B. A segment missing from one trip has
 * a null duration there (and the pair is a TIE). [SegmentComparison.bestEverMs] is the min for that
 * roadKey across all the user's trips.
 */
class CompareSegmentsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend operator fun invoke(tripAId: String, tripBId: String): List<SegmentComparison> {
        val a = tripRepository.getSegments(tripAId)
        val b = tripRepository.getSegments(tripBId)
        val aByKey = a.associateBy { it.roadKey }
        val bByKey = b.associateBy { it.roadKey }
        val orderedKeys = (a.map { it.roadKey } + b.map { it.roadKey }).distinct()

        return orderedKeys.map { key ->
            val sa = aByKey[key]
            val sb = bByKey[key]
            val da = sa?.durationMs
            val db = sb?.durationMs
            SegmentComparison(
                roadKey = key,
                roadName = sa?.roadName ?: sb?.roadName ?: key.substringBefore('|'),
                tripADurationMs = da,
                tripBDurationMs = db,
                bestEverMs = tripRepository.bestSegmentDuration(key),
                deltaMs = if (da != null && db != null) da - db else null,
                winner = when {
                    da == null || db == null -> ComparisonWinner.TIE
                    da < db -> ComparisonWinner.A
                    da > db -> ComparisonWinner.B
                    else -> ComparisonWinner.TIE
                },
            )
        }
    }
}
