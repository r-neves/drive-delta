package app.drivedelta.domain.model

/**
 * Route-level analytics for one place-pair A→B, aggregating every ride the user has driven on that
 * route (grouped by [Trip.routeHash]). Assembled by
 * [app.drivedelta.domain.usecase.segment.RouteSummaryUseCase] for the Route Summary screen: it
 * centres on one drive ([tripId] — the ride just finished, or the one opened from the dashboard) and
 * compares it against the rest of the route's history.
 *
 * Place names / car name are nullable so the UI can substitute a localized fallback. Times are in ms.
 */
data class RouteSummary(
    val tripId: String,
    val originName: String?,
    val destinationName: String?,
    val carName: String?,
    val startTime: Long,
    val driveCount: Int,              // total completed rides on this route (incl. this one)
    val segmentsTimed: Int,           // segments recorded on this drive
    val newPersonalBests: Int,        // == purpleCount (segments where this drive set a new record)
    val totalTimeMs: Long,            // this drive's total time
    val bestTotalMs: Long,            // best (min) total across the OTHER rides, or this drive if alone
    val deltaVsBestMs: Long,          // totalTimeMs − bestTotalMs (negative = this drive beat the record)
    val distanceMeters: Float,
    val avgSpeedKph: Float,
    val energyCost: Float?,           // linked FuelLog total for this drive, or null if none
    val fasterCount: Int,             // this drive's segments quicker than the previous run
    val slowerCount: Int,             // this drive's segments slower than the previous run
    val purpleCount: Int,             // this drive's segments that are a new personal best
    val scatter: List<RouteDrivePoint>,
)

/**
 * One dot in the "speed vs. cost" scatter: a past ride on the route that has both a computable average
 * speed and a linked energy cost. [isFastest] is the quickest ride by total time (the motorsport
 * "purple" point); [isCheapest] is the lowest-cost ride (the U-curve's sweet spot).
 */
data class RouteDrivePoint(
    val tripId: String,
    val avgSpeedKph: Float,
    val energyCost: Float,
    val isThisDrive: Boolean,
    val isFastest: Boolean,
    val isCheapest: Boolean,
)
