package app.drivedelta.domain.model

/**
 * A single GPS fix within a trip, as the domain sees it. Route points are stored in Room only and
 * are never synced to Firestore for MVP. [isInterpolated] marks a point synthesised to bridge a GPS
 * gap rather than reported by the device.
 */
data class RoutePoint(
    val tripId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val altitudeMeters: Double,
    val isInterpolated: Boolean,
)
