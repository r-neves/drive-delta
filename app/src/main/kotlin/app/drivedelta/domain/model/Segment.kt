package app.drivedelta.domain.model

/**
 * A stretch of road within a trip, computed post-ride from the Roads API snap (or raw fallback).
 * [roadKey] is the stable cross-trip identifier ("RoadName|startLat4dp,startLng4dp|endLat4dp,
 * endLng4dp"); speeds are stored in m/s to match the entity and are converted for display.
 */
data class Segment(
    val tripId: String,
    val segmentIndex: Int,
    val roadKey: String,
    val roadName: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceMeters: Float,
    val durationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
)
