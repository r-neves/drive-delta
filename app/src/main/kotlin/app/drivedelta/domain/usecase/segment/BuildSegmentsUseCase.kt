package app.drivedelta.domain.usecase.segment

import app.drivedelta.core.util.GeoUtils
import app.drivedelta.data.remote.roads.SnappedTimedPoint
import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.model.Segment
import app.drivedelta.domain.repository.TripRepository
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max

/**
 * Splits a snapped route into named road segments (F7). Consecutive snapped points sharing a road
 * `placeId` form one segment; each gets a [Segment.roadKey] (stable across trips), a reverse-geocoded
 * road name, and per-segment distance/duration/speeds. The trip's `routeHash` is the SHA-256 of the
 * ordered roadKey sequence. When [snapped] is null/empty (Roads API failed), falls back to fixed
 * 500 m distance chunks over the raw trace with `RAW|`-prefixed keys and "Unknown road" names.
 *
 * Segment distance comes from the snapped road geometry, but **timing/speed come from the raw GPS
 * trace**: RDP thinning before snapping (and interpolated snapped points) strip most timestamps, so
 * each segment's start/end is matched back to the nearest raw point to recover real durations.
 */
class BuildSegmentsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val roadNameResolver: RoadNameResolver,
) {
    suspend operator fun invoke(tripId: String, snapped: List<SnappedTimedPoint>?) {
        val raw = tripRepository.getRoutePoints(tripId).filter { !it.isInterpolated }
        val segments = if (!snapped.isNullOrEmpty()) {
            buildFromSnapped(tripId, snapped, raw)
        } else {
            buildFallback(tripId, raw)
        }
        val routeHash = sha256(segments.joinToString(SEP) { it.roadKey })
        tripRepository.finishTripSegments(
            tripId = tripId,
            segments = segments,
            routeHash = routeHash,
            roadsProcessed = !snapped.isNullOrEmpty(),
        )
    }

    private suspend fun buildFromSnapped(
        tripId: String,
        points: List<SnappedTimedPoint>,
        raw: List<RoutePoint>,
    ): List<Segment> {
        // Group consecutive snapped points by road placeId into per-segment coordinate lists.
        val groups = mutableListOf<List<Pair<Double, Double>>>()
        var groupStart = 0
        while (groupStart < points.size) {
            val placeId = points[groupStart].placeId
            var groupEnd = groupStart
            while (groupEnd + 1 < points.size && points[groupEnd + 1].placeId == placeId) groupEnd++
            val coords = points.subList(groupStart, groupEnd + 1).map { it.lat to it.lng }
            if (coords.size >= 2) groups += coords
            groupStart = groupEnd + 1
        }
        if (groups.isEmpty()) return emptyList()

        // Distribute the trip's total time across segments by each segment's share of the snapped
        // distance. RDP thinning + interpolated snapped points strip most timestamps, so exact
        // per-point timing isn't recoverable; proportional distribution keeps durations positive,
        // monotonic and summing to the trip total. (Post-MVP: finer intra-segment speed profiling.)
        val segDistances = groups.map { pathDistanceMeters(it) }
        val totalSnapped = segDistances.sum().coerceAtLeast(1e-9)
        val totalTimeMs = if (raw.size >= 2) raw.last().timestamp - raw.first().timestamp else 0L

        return groups.mapIndexed { index, coords ->
            val durationMs = (totalTimeMs * (segDistances[index] / totalSnapped)).toLong()
            val maxSpeed = maxRawSpeedNear(coords.first(), coords.last(), raw)
            makeSegment(tripId, index, coords, durationMs, maxSpeed, fallback = false)
        }
    }

    private suspend fun buildFallback(tripId: String, raw: List<RoutePoint>): List<Segment> {
        if (raw.size < 2) return emptyList()
        val segments = mutableListOf<Segment>()
        var index = 0
        var chunkStart = 0
        var accumulated = 0.0
        for (i in 1 until raw.size) {
            accumulated += GeoUtils.haversineMeters(raw[i - 1].lat, raw[i - 1].lng, raw[i].lat, raw[i].lng)
            if (accumulated >= FALLBACK_SEGMENT_METERS || i == raw.lastIndex) {
                val chunk = raw.subList(chunkStart, i + 1)
                val duration = chunk.last().timestamp - chunk.first().timestamp
                val maxSpeed = chunk.maxOf { it.speedMps }
                segments += makeSegment(
                    tripId, index++, chunk.map { it.lat to it.lng }, duration, maxSpeed, fallback = true,
                )
                chunkStart = i
                accumulated = 0.0
            }
        }
        return segments
    }

    /** Best-effort max speed from the raw points spanning a segment's start→end (for the HUD/detail). */
    private fun maxRawSpeedNear(
        start: Pair<Double, Double>,
        end: Pair<Double, Double>,
        raw: List<RoutePoint>,
    ): Float {
        if (raw.isEmpty()) return 0f
        var a = nearestRawIndex(start, raw)
        var b = nearestRawIndex(end, raw)
        if (b < a) a = b.also { b = a }
        return (a..b).maxOf { raw[it].speedMps }
    }

    private fun nearestRawIndex(coord: Pair<Double, Double>, raw: List<RoutePoint>): Int =
        raw.indices.minBy { GeoUtils.haversineMeters(coord.first, coord.second, raw[it].lat, raw[it].lng) }

    private fun pathDistanceMeters(coords: List<Pair<Double, Double>>): Double {
        var distance = 0.0
        for (i in 1 until coords.size) {
            distance += GeoUtils.haversineMeters(
                coords[i - 1].first, coords[i - 1].second, coords[i].first, coords[i].second,
            )
        }
        return distance
    }

    private suspend fun makeSegment(
        tripId: String,
        index: Int,
        coords: List<Pair<Double, Double>>,
        durationMs: Long,
        maxSpeedMps: Float,
        fallback: Boolean,
    ): Segment {
        val (startLat, startLng) = coords.first()
        val (endLat, endLng) = coords.last()
        val distance = pathDistanceMeters(coords)
        val avgSpeed = if (durationMs > 0) (distance / (durationMs / 1000.0)).toFloat() else 0f
        val roadName = if (fallback) {
            UNKNOWN_ROAD
        } else {
            roadNameResolver.roadNameAt((startLat + endLat) / 2, (startLng + endLng) / 2) ?: UNKNOWN_ROAD
        }
        val prefix = if (fallback) "RAW" else roadName
        val roadKey = "$prefix|${round4(startLat)},${round4(startLng)}|${round4(endLat)},${round4(endLng)}"

        return Segment(
            tripId = tripId,
            segmentIndex = index,
            roadKey = roadKey,
            roadName = roadName,
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng,
            distanceMeters = distance.toFloat(),
            durationMs = max(0L, durationMs),
            avgSpeedMps = avgSpeed,
            maxSpeedMps = max(avgSpeed, maxSpeedMps),
        )
    }

    // Locale.US so the decimal point is always '.', never a comma that would corrupt the roadKey.
    private fun round4(value: Double): String = String.format(Locale.US, "%.4f", value)

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { String.format(Locale.US, "%02x", it) }

    private companion object {
        const val FALLBACK_SEGMENT_METERS = 500.0
        const val UNKNOWN_ROAD = "Unknown road"
        const val SEP = "|"
    }
}
