package app.drivedelta.domain.usecase.segment

import app.drivedelta.domain.model.RouteDrivePoint
import app.drivedelta.domain.model.RouteSummary
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.FuelLogRepository
import app.drivedelta.domain.repository.PlaceRepository
import app.drivedelta.domain.repository.TripRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Assembles the Route Summary payload for one drive [tripId]: every completed ride sharing the same
 * route (by `routeHash`) is gathered, then this drive is compared against the rest of that history.
 *
 * Reuses the segment-comparison logic: per-segment classification uses the same two baselines as the
 * Trip Detail splits — a segment is "purple" when this drive beats the all-time record for that road
 * (a new personal best), otherwise "faster" or "slower" relative to the previous run. The three counts
 * partition this drive's segments. The scatter reuses the fuel-efficiency link (linked FuelLog cost)
 * against each ride's average speed to trace the cost-vs-pace U-curve.
 *
 * Returns null if the trip is missing / not owned / still in progress.
 */
class RouteSummaryUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val placeRepository: PlaceRepository,
    private val carRepository: CarRepository,
    private val fuelLogRepository: FuelLogRepository,
) {
    suspend operator fun invoke(tripId: String): RouteSummary? {
        val trip = tripRepository.getTrip(tripId) ?: return null
        if (trip.endTime == null) return null

        // All completed rides on this route (same routeHash). Blank hash → this drive stands alone.
        val allEnded = tripRepository.observeTrips().first().filter { it.endTime != null }
        val group = if (trip.routeHash.isNotBlank()) {
            allEnded.filter { it.routeHash == trip.routeHash }
        } else {
            listOf(trip)
        }
        val others = group.filter { it.id != trip.id }

        // Energy cost per ride = sum of its linked fuel logs (usually one).
        val costByTrip = fuelLogRepository.observeFuelLogs().first()
            .filter { it.tripId != null }
            .groupBy { it.tripId!! }
            .mapValues { (_, logs) -> logs.sumOf { it.totalCost.toDouble() }.toFloat() }

        // Segment-level baselines.
        val thisSegments = tripRepository.getSegments(trip.id)
        val previousTrip = others.filter { it.startTime < trip.startTime }.maxByOrNull { it.startTime }
            ?: others.maxByOrNull { it.startTime }
        val previousByRoadKey = previousTrip
            ?.let { tripRepository.getSegments(it.id).associate { s -> s.roadKey to s.durationMs } }
            ?: emptyMap()
        // Standing record per road across the OTHER rides (excludes this drive).
        val recordByRoadKey = HashMap<String, Long>()
        for (other in others) {
            for (s in tripRepository.getSegments(other.id)) {
                val prev = recordByRoadKey[s.roadKey]
                if (prev == null || s.durationMs < prev) recordByRoadKey[s.roadKey] = s.durationMs
            }
        }

        var faster = 0
        var slower = 0
        var purple = 0
        for (s in thisSegments) {
            val record = recordByRoadKey[s.roadKey]
            val previous = previousByRoadKey[s.roadKey]
            when {
                record == null || s.durationMs < record -> purple++          // new / first personal best
                previous != null && s.durationMs < previous -> faster++      // quicker than the last run
                else -> slower++
            }
        }

        // Totals: use each ride's wall-clock durationMs (always present, unlike segment sums).
        val totalMs = trip.durationMs
        val bestOtherMs = others.minOfOrNull { it.durationMs }
        val bestTotalMs = bestOtherMs ?: totalMs

        // Scatter: rides with a linked cost and a computable average speed.
        val fastestId = group.minByOrNull { it.durationMs }?.id
        val cheapestId = group.filter { costByTrip[it.id] != null }.minByOrNull { costByTrip[it.id]!! }?.id
        val scatter = group.mapNotNull { t ->
            val cost = costByTrip[t.id] ?: return@mapNotNull null
            val speed = avgSpeedKph(t)
            if (speed <= 0f) return@mapNotNull null
            RouteDrivePoint(
                tripId = t.id,
                avgSpeedKph = speed,
                energyCost = cost,
                isThisDrive = t.id == trip.id,
                isFastest = t.id == fastestId,
                isCheapest = t.id == cheapestId,
            )
        }.sortedBy { it.avgSpeedKph }

        return RouteSummary(
            tripId = trip.id,
            originName = trip.startPlaceId?.let { placeRepository.getPlace(it)?.name },
            destinationName = trip.endPlaceId?.let { placeRepository.getPlace(it)?.name },
            carName = trip.carId?.let { carRepository.getCar(it)?.name },
            startTime = trip.startTime,
            driveCount = group.size,
            segmentsTimed = thisSegments.size,
            newPersonalBests = purple,
            totalTimeMs = totalMs,
            bestTotalMs = bestTotalMs,
            deltaVsBestMs = totalMs - bestTotalMs,
            distanceMeters = trip.distanceMeters,
            avgSpeedKph = avgSpeedKph(trip),
            energyCost = costByTrip[trip.id],
            fasterCount = faster,
            slowerCount = slower,
            purpleCount = purple,
            scatter = scatter,
        )
    }

    private fun avgSpeedKph(trip: Trip): Float {
        if (trip.durationMs <= 0L) return 0f
        return (trip.distanceMeters / 1000f) / (trip.durationMs / 3_600_000f)
    }
}
