package app.drivedelta.ui.history

import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.FuelType
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.model.Trip

/** The two views of the Trips screen (design/mockups/trips-recent.png, trips-by-route.png). */
enum class TripTab { RECENT, BY_ROUTE }

/** Trend of a route's recent drive times: getting quicker, slower, or a fresh personal best. */
enum class RouteTrend { FASTER, SLOWER, BEST }

/** Header stat card totals for the Recent tab. */
data class TripsStats(val drives: Int, val distanceKm: Float, val newPbs: Int)

/**
 * One drive as the Recent list renders it. [deltaVsPrevMs] is this drive minus the previous drive on
 * the same route (null when it's the first, or the route has no hash); negative = quicker. [isNewPb]
 * is true when this drive beat every earlier drive on the route — a personal-best event.
 */
data class RecentTripItem(
    val tripId: String,
    val startTime: Long,
    val originName: String?,
    val destName: String?,
    val carName: String?,
    val fuelType: FuelType?,
    val durationMs: Long,
    val distanceMeters: Float,
    val deltaVsPrevMs: Long?,
    val isNewPb: Boolean,
)

/** A day header + its drives, newest first. [dayEpoch] is the local epoch-day (formatted by the UI). */
data class DaySection(val dayEpoch: Long, val items: List<RecentTripItem>)

/**
 * One route (a distinct origin→destination) as the By-route tab renders it: best time across all its
 * drives, a [series] of recent durations for the sparkline, and the overall [trend].
 */
data class RouteItem(
    val routeHash: String,
    val originName: String?,
    val destName: String?,
    val driveCount: Int,
    val distanceMeters: Float,
    val bestMs: Long,
    val trend: RouteTrend,
    val series: List<Long>,
    val openTripId: String,
)

/** Everything the Trips screen needs, computed once per data change. */
data class TripsOverview(
    val stats: TripsStats,
    val sections: List<DaySection>,
    val routes: List<RouteItem>,
)

/**
 * Pure builder for the Trips screen. Kept free of Android/formatting concerns so it can be unit
 * tested: day grouping is delegated to [epochDayOf] (a local-timezone epoch-day mapper) and time
 * strings are formatted by the UI. Per-route deltas and PB events are computed over the
 * vehicle-filtered set (so a route's history is intact) before the text search narrows the display.
 */
object TripsOverviewBuilder {

    const val MAX_SERIES = 12

    fun build(
        allTrips: List<Trip>,
        cars: List<Car>,
        places: List<Place>,
        selectedCarId: String?,
        query: String,
        epochDayOf: (Long) -> Long,
        maxSeries: Int = MAX_SERIES,
    ): TripsOverview {
        val carsById = cars.associateBy { it.id }
        val placesById = places.associateBy { it.id }
        fun placeName(id: String?): String? = id?.let { placesById[it]?.name }

        val completed = allTrips.filter { it.endTime != null }
        val vehicleFiltered =
            if (selectedCarId != null) completed.filter { it.carId == selectedCarId } else completed

        // Per-route history → delta vs previous drive, and PB-event flags.
        val byRoute = vehicleFiltered.filter { it.routeHash.isNotBlank() }.groupBy { it.routeHash }
        val deltaVsPrev = HashMap<String, Long?>()
        val pbEvent = HashMap<String, Boolean>()
        for ((_, group) in byRoute) {
            val asc = group.sortedBy { it.startTime }
            var best: Long? = null
            asc.forEachIndexed { i, trip ->
                deltaVsPrev[trip.id] = if (i > 0) trip.durationMs - asc[i - 1].durationMs else null
                pbEvent[trip.id] = best != null && trip.durationMs < best!!
                best = if (best == null) trip.durationMs else minOf(best!!, trip.durationMs)
            }
        }

        fun enrich(t: Trip) = RecentTripItem(
            tripId = t.id,
            startTime = t.startTime,
            originName = placeName(t.startPlaceId),
            destName = placeName(t.endPlaceId),
            carName = t.carId?.let { carsById[it]?.name },
            fuelType = t.carId?.let { carsById[it]?.fuelType },
            durationMs = t.durationMs,
            distanceMeters = t.distanceMeters,
            deltaVsPrevMs = deltaVsPrev[t.id],
            isNewPb = pbEvent[t.id] == true,
        )

        val enriched = vehicleFiltered.map(::enrich)
        val q = query.trim()
        val displayed =
            if (q.isEmpty()) enriched
            else enriched.filter { item ->
                listOfNotNull(item.originName, item.destName, item.carName)
                    .any { it.contains(q, ignoreCase = true) }
            }

        val sections = displayed
            .sortedByDescending { it.startTime }
            .groupBy { epochDayOf(it.startTime) }
            .map { (day, items) -> DaySection(day, items) }
            .sortedByDescending { it.dayEpoch }

        val stats = TripsStats(
            drives = displayed.size,
            distanceKm = displayed.map { it.distanceMeters }.sum() / 1000f,
            newPbs = displayed.count { it.isNewPb },
        )

        val routes = byRoute.map { (hash, group) ->
            val asc = group.sortedBy { it.startTime }
            val durations = asc.map { it.durationMs }
            val newest = asc.last()
            val newestIsPb = pbEvent[newest.id] == true
            val series = durations.takeLast(maxSeries)
            val trend = when {
                newestIsPb -> RouteTrend.BEST
                series.size >= 2 && series.last() <= series.first() -> RouteTrend.FASTER
                else -> RouteTrend.SLOWER
            }
            RouteItem(
                routeHash = hash,
                originName = placeName(newest.startPlaceId),
                destName = placeName(newest.endPlaceId),
                driveCount = group.size,
                distanceMeters = newest.distanceMeters,
                bestMs = durations.min(),
                trend = trend,
                series = series,
                openTripId = newest.id,
            )
        }.sortedWith(compareByDescending<RouteItem> { it.driveCount }.thenBy { it.bestMs })

        return TripsOverview(stats = stats, sections = sections, routes = routes)
    }
}
