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
 * A distinct origin→destination pairing, used by the Route filter. A null place id means the trip had
 * no linked place on that end ("Unknown"). [key] is a stable string id for selection/equality.
 */
data class RoutePair(val originPlaceId: String?, val destPlaceId: String?) {
    val key: String get() = "${originPlaceId ?: ""}|${destPlaceId ?: ""}"
}

/** One selectable Route-filter option: the pair, its resolved names, and how many drives it covers. */
data class RoutePairOption(
    val pair: RoutePair,
    val originName: String?,
    val destName: String?,
    val count: Int,
)

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
    /** Origin→destination pairs available to the Route filter, over the vehicle-filtered set. */
    val pairOptions: List<RoutePairOption> = emptyList(),
)

/**
 * Pure builder for the Trips screen. Kept free of Android/formatting concerns so it can be unit
 * tested: day grouping is delegated to [epochDayOf] (a local-timezone epoch-day mapper) and time
 * strings are formatted by the UI. Per-route deltas and PB events are computed over the
 * vehicle-filtered set (so a route's history is intact) before the display filters (route
 * [selectedPair], [dateRange] and text [query]) narrow what's shown.
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
        selectedPair: RoutePair? = null,
        dateRange: LongRange? = null,
        maxSeries: Int = MAX_SERIES,
    ): TripsOverview {
        val carsById = cars.associateBy { it.id }
        val placesById = places.associateBy { it.id }
        fun placeName(id: String?): String? = id?.let { placesById[it]?.name }

        val completed = allTrips.filter { it.endTime != null }
        val vehicleFiltered =
            if (selectedCarId != null) completed.filter { it.carId == selectedCarId } else completed

        // Route-filter options: distinct origin→destination pairs over the vehicle-filtered set.
        val pairOptions = vehicleFiltered
            .filter { it.startPlaceId != null || it.endPlaceId != null }
            .groupBy { RoutePair(it.startPlaceId, it.endPlaceId) }
            .map { (pair, group) -> RoutePairOption(pair, placeName(pair.originPlaceId), placeName(pair.destPlaceId), group.size) }
            .sortedWith(compareByDescending<RoutePairOption> { it.count }.thenBy { it.originName ?: "" })

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

        val q = query.trim()
        fun matchesPair(t: Trip) =
            selectedPair == null || (t.startPlaceId == selectedPair.originPlaceId && t.endPlaceId == selectedPair.destPlaceId)
        fun matchesDate(t: Trip) = dateRange == null || t.startTime in dateRange
        fun matchesQuery(t: Trip): Boolean {
            if (q.isEmpty()) return true
            return listOfNotNull(placeName(t.startPlaceId), placeName(t.endPlaceId), t.carId?.let { carsById[it]?.name })
                .any { it.contains(q, ignoreCase = true) }
        }

        val displayed = vehicleFiltered
            .filter { matchesPair(it) && matchesDate(it) && matchesQuery(it) }
            .map(::enrich)

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

        val routes = byRoute
            .filter { (_, group) -> selectedPair == null || matchesPair(group.maxByOrNull { it.startTime }!!) }
            .map { (hash, group) ->
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

        return TripsOverview(stats = stats, sections = sections, routes = routes, pairOptions = pairOptions)
    }
}
