package app.drivedelta.domain.usecase.segment

import app.drivedelta.core.debug.SegmentationRecorder
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
 * Splits a snapped route into named road segments (F7). A segment is **one continuous stretch of
 * one named road**: snapped points are grouped by road `placeId`, each group is named, and adjacent
 * groups sharing a name are merged. The trip's `routeHash` is the SHA-256 of the ordered roadKey
 * sequence. When [snapped] is null/empty (Roads API failed), falls back to fixed 500 m distance
 * chunks over the raw trace with `RAW|`-prefixed keys and "Unknown road" names.
 *
 * Segment distance comes from the snapped road geometry; **timing and speed come from the raw GPS
 * trace**, by matching each segment's endpoints back to the nearest raw fix. Durations used to be
 * *distributed* across segments in proportion to distance, which meant a fast stretch and a slow
 * stretch of equal length were reported as taking exactly as long as each other — so no split time,
 * and no personal best derived from one, was ever a real measurement.
 */
class BuildSegmentsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val roadNameResolver: RoadNameResolver,
    // Debug-only; a no-op in release. Captures the geocoder's answers so the grouping algorithm can
    // be iterated offline against a real drive instead of against a service that answers differently
    // each time. Nullable so unit tests can construct the use case without it.
    private val recorder: SegmentationRecorder? = null,
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
        // 1. Runs of consecutive snapped points sharing a road placeId.
        val runs = mutableListOf<Run>()
        var runStart = 0
        while (runStart < points.size) {
            val placeId = points[runStart].placeId
            var runEnd = runStart
            while (runEnd + 1 < points.size && points[runEnd + 1].placeId == placeId) runEnd++
            val coords = points.subList(runStart, runEnd + 1).map { it.lat to it.lng }
            if (coords.size >= 2) runs += Run(placeId, coords)
            runStart = runEnd + 1
        }
        if (runs.isEmpty()) return emptyList()

        // 2. Name each run. A placeId is one road feature, so the same id resolves to the same name
        //    — cache it, or a long drive costs hundreds of identical geocoder round-trips.
        val nameCache = mutableMapOf<String, String?>()
        for (run in runs) {
            run.name = nameCache.getOrPut(run.placeId) {
                val (midLat, midLng) = midpointOf(run.coords)
                roadNameResolver.roadNameAt(midLat, midLng)
            }
        }

        recorder?.record(tripId, raw, points, nameCache)

        // 3. Carry a known name across runs the geocoder couldn't name, rather than letting them
        //    become "Unknown road" and cut a continuous road in two.
        var lastKnown: String? = null
        for (run in runs) {
            if (run.name != null) lastKnown = run.name else run.name = lastKnown
        }
        lastKnown = null
        for (run in runs.asReversed()) {
            if (run.name != null) lastKnown = run.name else run.name = lastKnown
        }

        // 4. A continuous stretch of one road is ONE segment. Google splits a motorway into dozens
        //    of placeId features, which is why a 173 km drive produced 853 segments averaging six
        //    seconds each. Merging by name is boundary-stable: the cut falls where the road changes,
        //    which is the same place on every drive — unlike accumulating to a distance or time
        //    budget, where traffic would move the boundaries and nothing would compare across drives.
        val merged = mutableListOf<Run>()
        for (run in runs) {
            val previous = merged.lastOrNull()
            if (previous != null && previous.name == run.name) {
                previous.coords = previous.coords + run.coords
                previous.endPlaceId = run.placeId
            } else {
                merged += run
            }
        }

        // 5. Real per-segment durations, from the raw trace's timestamps. Each segment starts at
        //    the raw fix nearest its first coordinate and runs until the next segment starts, so the
        //    segments tile the drive exactly and their durations sum to the trip's. (Ending each
        //    segment at its own last coordinate instead would drop the interval spent crossing every
        //    boundary — about 1% of a long drive, and unattributable to anything.) The cursor only
        //    moves forward, so the walk is linear in the number of fixes rather than rescanning the
        //    whole trace per segment.
        var cursor = 0
        val starts = merged.map { run ->
            cursor = advanceToNearest(run.coords.first(), raw, cursor)
            cursor
        }

        return merged.mapIndexed { index, run ->
            val from = starts[index]
            val to = if (index + 1 < starts.size) starts[index + 1] else raw.lastIndex
            val durationMs = if (raw.isEmpty()) 0L else raw[to].timestamp - raw[from].timestamp
            val maxSpeed = if (raw.isEmpty()) 0f else (minOf(from, to)..maxOf(from, to)).maxOf { raw[it].speedMps }
            makeSegment(
                tripId = tripId,
                index = index,
                coords = run.coords,
                durationMs = durationMs,
                maxSpeedMps = maxSpeed,
                roadName = run.name ?: UNKNOWN_ROAD,
                // Keyed on the road plus the Google feature ids it starts and ends on. Those are
                // stable identifiers for physical stretches of road, so the same drive keys the same
                // way every time — the old key used raw coordinates at ~11 m precision, so GPS noise
                // alone gave a stretch a different key on every drive and it never compared against
                // itself. That is why almost every split showed up as a personal best.
                roadKey = "${run.name ?: UNKNOWN_ROAD}$SEP${run.placeId}$SEP${run.endPlaceId ?: run.placeId}",
            )
        }
    }

    /** Nearest raw fix to [coord], searching forward from [from] (the trace is walked in order). */
    private fun advanceToNearest(coord: Pair<Double, Double>, raw: List<RoutePoint>, from: Int): Int {
        if (raw.isEmpty()) return 0
        var best = from.coerceIn(raw.indices)
        var bestDistance = Double.MAX_VALUE
        for (i in best until raw.size) {
            val d = GeoUtils.haversineMeters(coord.first, coord.second, raw[i].lat, raw[i].lng)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    private fun midpointOf(coords: List<Pair<Double, Double>>): Pair<Double, Double> =
        coords[coords.size / 2]

    /** One run of road: the snapped geometry between two road-name changes. */
    private class Run(
        val placeId: String,
        var coords: List<Pair<Double, Double>>,
        var name: String? = null,
        var endPlaceId: String? = null,
    )

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
                val coords = chunk.map { it.lat to it.lng }
                segments += makeSegment(
                    tripId = tripId,
                    index = index++,
                    coords = coords,
                    durationMs = duration,
                    maxSpeedMps = maxSpeed,
                    roadName = UNKNOWN_ROAD,
                    // No Roads API result here, so there are no feature ids to key on; fall back to
                    // coordinates. These trips can be reprocessed once Roads is reachable again.
                    roadKey = "RAW$SEP${round4(coords.first().first)},${round4(coords.first().second)}" +
                        "$SEP${round4(coords.last().first)},${round4(coords.last().second)}",
                )
                chunkStart = i
                accumulated = 0.0
            }
        }
        return segments
    }

    private fun pathDistanceMeters(coords: List<Pair<Double, Double>>): Double {
        var distance = 0.0
        for (i in 1 until coords.size) {
            distance += GeoUtils.haversineMeters(
                coords[i - 1].first, coords[i - 1].second, coords[i].first, coords[i].second,
            )
        }
        return distance
    }

    private fun makeSegment(
        tripId: String,
        index: Int,
        coords: List<Pair<Double, Double>>,
        durationMs: Long,
        maxSpeedMps: Float,
        roadName: String,
        roadKey: String,
    ): Segment {
        val (startLat, startLng) = coords.first()
        val (endLat, endLng) = coords.last()
        val distance = pathDistanceMeters(coords)
        val avgSpeed = if (durationMs > 0) (distance / (durationMs / 1000.0)).toFloat() else 0f

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
