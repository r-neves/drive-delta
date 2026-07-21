package app.drivedelta.core.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geospatial helpers. Checkpoint 4 needs only great-circle distance (for nearby-place detection);
 * bearing and interpolation are added in Checkpoint 5 for the tracking service's GPS-gap fill.
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

    /**
     * Initial great-circle bearing from point 1 to point 2, in degrees clockwise from north
     * (0..360). Used to describe the heading of an interpolated GPS-gap segment.
     */
    fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val dλ = Math.toRadians(lng2 - lng1)
        val y = sin(dλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(dλ)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Linearly interpolates [fraction] (0..1) of the way from point 1 to point 2, returned as
     * (lat, lng). Over the short distances of a GPS gap (< ~1 km) treating lat/lng as planar is
     * accurate to well within GPS noise, so a straight linear blend is sufficient.
     */
    fun interpolate(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
        fraction: Double,
    ): Pair<Double, Double> {
        val f = fraction.coerceIn(0.0, 1.0)
        return (lat1 + (lat2 - lat1) * f) to (lng1 + (lng2 - lng1) * f)
    }

    /**
     * Ramer–Douglas–Peucker line simplification. Returns the sorted indices of [points]
     * (each a lat/lng pair) to keep so no dropped point lies more than [epsilonMeters] from the
     * retained polyline — used to thin a GPS trace before the Roads API snap (fewer points → fewer
     * chunks → lower cost). Endpoints are always kept. Iterative (explicit stack) to avoid deep
     * recursion on long routes.
     */
    fun simplify(points: List<Pair<Double, Double>>, epsilonMeters: Double): List<Int> {
        if (points.size < 3) return points.indices.toList()
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.lastIndex)
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            var maxDist = 0.0
            var index = -1
            for (i in first + 1 until last) {
                val d = perpendicularDistanceMeters(
                    points[i].first, points[i].second,
                    points[first].first, points[first].second,
                    points[last].first, points[last].second,
                )
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (maxDist > epsilonMeters && index != -1) {
                keep[index] = true
                stack.addLast(first to index)
                stack.addLast(index to last)
            }
        }
        return points.indices.filter { keep[it] }
    }

    /** Perpendicular distance (metres) from point P to segment A→B, via a local planar projection. */
    private fun perpendicularDistanceMeters(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double,
    ): Double {
        val mPerDegLat = 111_320.0
        val mPerDegLng = 111_320.0 * cos(Math.toRadians(aLat))
        val px = (pLng - aLng) * mPerDegLng
        val py = (pLat - aLat) * mPerDegLat
        val bx = (bLng - aLng) * mPerDegLng
        val by = (bLat - aLat) * mPerDegLat
        val segLen2 = bx * bx + by * by
        if (segLen2 == 0.0) return sqrt(px * px + py * py)
        val t = ((px * bx + py * by) / segLen2).coerceIn(0.0, 1.0)
        val projX = t * bx
        val projY = t * by
        val dx = px - projX
        val dy = py - projY
        return sqrt(dx * dx + dy * dy)
    }
}
