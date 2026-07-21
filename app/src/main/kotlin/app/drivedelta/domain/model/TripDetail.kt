package app.drivedelta.domain.model

/**
 * Everything the Trip Detail screen needs (F10): the trip, its ordered segments, the raw route
 * points (for the map + replay), and the best-ever duration per roadKey across the user's trips
 * (for the "vs best" split column). [fuelPromptDismissed] tracks whether the post-ride fuel prompt
 * was already dismissed for this trip.
 */
data class TripDetail(
    val trip: Trip,
    val segments: List<Segment>,
    val routePoints: List<RoutePoint>,
    val bestPerRoadKey: Map<String, Long>,
    val fuelPromptDismissed: Boolean,
)
