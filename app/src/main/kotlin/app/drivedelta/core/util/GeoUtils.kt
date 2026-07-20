package app.drivedelta.core.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geospatial helpers. Checkpoint 4 needs only great-circle distance (for nearby-place detection);
 * bearing and interpolation are added in Checkpoint 5 with the tracking service.
 */
object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Haversine great-circle distance in metres between two lat/lng points. */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
