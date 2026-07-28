package app.drivedelta.domain.usecase.fuel

import app.drivedelta.domain.model.EnergyPrices
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.EnergyPricesRepository
import app.drivedelta.domain.repository.FuelLogRepository
import app.drivedelta.domain.repository.TripRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** One drive on the Trip Detail speed-vs-cost chart. [cost] is null for a drive that isn't logged yet. */
data class TripCostPoint(
    val tripId: String,
    val speedKph: Float,
    val cost: Float?,
    val isThisDrive: Boolean,
    val isFastest: Boolean,
    val isCheapest: Boolean,
    val isEstimated: Boolean,
)

/**
 * The fuel/energy cost context for one drive's Trip Detail screen (design/mockups/Energy
 * Logging-saved-drive-not-logged.png): this drive's logged cost (null when not logged yet) and the
 * speed-vs-cost scatter across every drive on the same route.
 *
 * The focused drive is always shown as-is — its own cost when logged, or a "no cost yet" marker on the
 * speed axis when not. Other drives on the route contribute their logged cost; when
 * [EnergyPrices.estimateWhenNotLogged] is on, an unlogged other drive is gap-filled with an estimate
 * from its car's average consumption so the curve still forms.
 */
data class TripCostChart(
    val loggedCost: Float?,
    val currencyCode: String,
    val driveCount: Int,
    val points: List<TripCostPoint>,
)

class GetTripCostChartUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val carRepository: CarRepository,
    private val fuelLogRepository: FuelLogRepository,
    private val energyPricesRepository: EnergyPricesRepository,
) {
    suspend operator fun invoke(tripId: String): TripCostChart? {
        val trip = tripRepository.getTrip(tripId) ?: return null
        if (trip.endTime == null) return null
        val prices = energyPricesRepository.getPrices()

        val allEnded = tripRepository.observeTrips().first().filter { it.endTime != null }
        val group = if (trip.routeHash.isNotBlank()) allEnded.filter { it.routeHash == trip.routeHash } else listOf(trip)

        // Real logged cost per drive (sum of its linked fuel logs).
        val costByTrip = fuelLogRepository.observeFuelLogs().first()
            .filter { it.tripId != null }
            .groupBy { it.tripId!! }
            .mapValues { (_, logs) -> logs.sumOf { it.totalCost.toDouble() }.toFloat() }

        val fastestId = group.minByOrNull { it.durationMs }?.id
        val cheapestId = group.filter { costByTrip[it.id] != null }.minByOrNull { costByTrip[it.id]!! }?.id

        val points = group.mapNotNull { t ->
            val speed = avgSpeedKph(t)
            if (speed <= 0f) return@mapNotNull null
            val realCost = costByTrip[t.id]
            when {
                t.id == trip.id -> TripCostPoint(t.id, speed, realCost, isThisDrive = true, isFastest = t.id == fastestId, isCheapest = t.id == cheapestId, isEstimated = false)
                realCost != null -> TripCostPoint(t.id, speed, realCost, isThisDrive = false, isFastest = t.id == fastestId, isCheapest = t.id == cheapestId, isEstimated = false)
                prices.estimateWhenNotLogged -> {
                    val est = estimatedCost(t, prices) ?: return@mapNotNull null
                    TripCostPoint(t.id, speed, est, isThisDrive = false, isFastest = false, isCheapest = false, isEstimated = true)
                }
                else -> null
            }
        }

        return TripCostChart(
            loggedCost = costByTrip[trip.id],
            currencyCode = prices.currencyCode,
            driveCount = group.size,
            points = points,
        )
    }

    private suspend fun estimatedCost(trip: Trip, prices: EnergyPrices): Float? {
        val car = trip.carId?.let { carRepository.getCar(it) } ?: return null
        val consumption = car.defaultConsumption ?: return null
        val distanceKm = trip.distanceMeters / 1000f
        if (distanceKm <= 0f) return null
        val amount = consumption * distanceKm / 100f
        return amount * prices.unitPriceFor(car)
    }

    private fun avgSpeedKph(trip: Trip): Float {
        if (trip.durationMs <= 0L) return 0f
        return (trip.distanceMeters / 1000f) / (trip.durationMs / 3_600_000f)
    }
}
