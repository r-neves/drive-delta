package app.drivedelta.data.remote.roads

/** A GPS fix reduced to what snapping needs, plus the timing used later for per-segment durations. */
data class TimedPoint(
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    val speedMps: Float,
)

/**
 * A snapped point with its road [placeId] and, when it corresponds to a real input point, the
 * [timestamp]/[speedMps] carried over from that input (interpolated points have null timing).
 */
data class SnappedTimedPoint(
    val lat: Double,
    val lng: Double,
    val placeId: String,
    val timestamp: Long?,
    val speedMps: Float?,
)
