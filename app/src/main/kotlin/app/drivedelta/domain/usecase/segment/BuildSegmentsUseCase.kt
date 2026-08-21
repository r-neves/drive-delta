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
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Splits a snapped route into named road segments (F7). A segment is **one continuous stretch of
 * one named road, at least [MIN_SEGMENT_METERS] long**: snapped points are grouped by road
 * `placeId`, each group is named, adjacent groups sharing a name are merged, and anything still too
 * short is folded into its longer neighbour. The trip's `routeHash` is the SHA-256 of the ordered
 * roadKey sequence. When [invoke]'s `snapped` is null/empty (Roads API failed), falls back to fixed
 * 500 m distance chunks over the raw trace with `RAW|`-prefixed keys and "Unknown road" names.
 *
 * **Which source answers which question** matters here, and getting it wrong is what made split
 * times meaningless before:
 *
 * - *Where the roads change* comes from the snapped geometry. Boundaries then fall in the same
 *   place on every drive, which is what lets a stretch be compared against itself.
 * - *When each boundary was crossed* comes from the snapped points that came from a real GPS fix
 *   and therefore carry its timestamp, with the points between them timed by their distance along
 *   the road (see [timeline]).
 * - *How far and how fast* comes from the raw trace between those two times. The snapped path is
 *   the road's shape, not the car's: locally it doubles back between parallel carriageways, and
 *   measuring distance along it reported stretches driven at 130 km/h as 250 km/h. Taking distance
 *   from the same trace the trip's own `distanceMeters` is measured from also makes the segments
 *   sum to the drive instead of disagreeing with it.
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
        snapped: List<SnappedTimedPoint>,
        raw: List<RoutePoint>,
    ): List<Segment> {
        if (raw.size < 2) return emptyList()
        val points = withoutImpossibleInterpolation(snapped)
        if (points.size < 2) return emptyList()

        val alongRoad = cumulativeMeters(points)
        val time = timeline(points, alongRoad, raw)
        val trace = RawTrace(raw)

        // 1. Runs of consecutive snapped points sharing a road placeId.
        val runs = mutableListOf<Run>()
        var index = 0
        while (index < points.size) {
            val placeId = points[index].placeId
            var end = index
            while (end + 1 < points.size && points[end + 1].placeId == placeId) end++
            runs += Run(startIndex = index, endIndex = end, placeId = placeId, endPlaceId = placeId)
            index = end + 1
        }

        // 2. Name each run. A placeId is one road feature, so the same id resolves to the same name
        //    — cache it, or a long drive costs hundreds of identical geocoder round-trips.
        val nameCache = mutableMapOf<String, String?>()
        for (run in runs) {
            run.name = nameCache.getOrPut(run.placeId) {
                val middle = points[(run.startIndex + run.endIndex) / 2]
                roadNameResolver.roadNameAt(middle.lat, middle.lng)
            }
        }

        recorder?.record(tripId, raw, snapped, nameCache)

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
        val merged = absorbShortSegments(mergeAdjacentSameRoad(runs), points.lastIndex, time, trace)

        // 5. Segments tile the drive: each runs until the next one starts, so no distance and no
        //    time falls between two of them and both sum to the trip's own totals. (Ending each at
        //    its own last point instead would drop every boundary crossing — about 1% of a long
        //    drive, unattributable to anything.)
        return merged.mapIndexed { position, run ->
            val from = run.startIndex
            val to = if (position + 1 < merged.size) merged[position + 1].startIndex else points.lastIndex
            val firstFix = trace.indexAt(time[from])
            val lastFix = trace.indexAt(time[to])
            makeSegment(
                tripId = tripId,
                index = position,
                start = points[from].lat to points[from].lng,
                end = points[to].lat to points[to].lng,
                distanceMeters = trace.metersBetween(firstFix, lastFix),
                durationMs = time[to] - time[from],
                maxSpeedMps = trace.maxSpeedMps(firstFix, lastFix),
                roadName = run.name ?: UNKNOWN_ROAD,
                // Keyed on the road plus the Google feature ids it starts and ends on. Those are
                // stable identifiers for physical stretches of road, so the same drive keys the same
                // way every time — the old key used raw coordinates at ~11 m precision, so GPS noise
                // alone gave a stretch a different key on every drive and it never compared against
                // itself. That is why almost every split showed up as a personal best.
                roadKey = "${run.name ?: UNKNOWN_ROAD}$SEP${run.placeId}$SEP${run.endPlaceId}",
            )
        }
    }

    /**
     * Drops interpolated geometry the Roads API cannot have meant. Between two snapped points that
     * came from real fixes, everything in between is the API's guess at the road's shape; where that
     * guess is both far longer than a straight line and longer than any car could cover in the time
     * between those two fixes, it is an artefact rather than a road.
     *
     * On the reference drive this removed 33 km of phantom geometry — the route jumped backwards and
     * re-drove each chunk overlap, since only the *real* duplicate points were being de-duplicated
     * (fixed in `RoadsDataSource`, but recorded drives and any future snapping artefact still land
     * here). That phantom distance was enough to put 25 of 154 segments at an impossible speed, one
     * of them at 1,427 km/h, and to repeat roads the car had already left.
     */
    private fun withoutImpossibleInterpolation(
        points: List<SnappedTimedPoint>,
    ): List<SnappedTimedPoint> {
        val anchors = points.indices.filter { points[it].timestamp != null }
        if (anchors.size < 2) return points

        val drop = HashSet<Int>()
        for (k in 0 until anchors.size - 1) {
            val from = anchors[k]
            val to = anchors[k + 1]
            if (to <= from + 1) continue
            var path = 0.0
            for (i in from + 1..to) path += metersBetween(points[i - 1], points[i])
            val chord = metersBetween(points[from], points[to])
            val seconds = (points[to].timestamp!! - points[from].timestamp!!) / 1000.0
            val detour = path > chord * DETOUR_FACTOR + DETOUR_SLACK_METERS
            if (detour && path > seconds * MAX_PLAUSIBLE_MPS) {
                for (i in from + 1 until to) drop += i
            }
        }
        return if (drop.isEmpty()) points else points.filterIndexed { i, _ -> i !in drop }
    }

    /**
     * A timestamp for every snapped point. A point that came from a real fix keeps that fix's time
     * and is exact; the points between two of them are timed by how far along the road they sit,
     * which is monotone and pinned at both ends by a time that is not a guess.
     *
     * Most points are estimates — on the reference drive only 509 of 4,864 carry a timestamp,
     * because RDP thinning before the snap plus `interpolate=true` after it leave real fixes in the
     * minority — but an estimate is never more than one anchor interval (~260 m) from an exact time.
     *
     * The alternative, matching each boundary to its nearest raw fix, is what this replaced: a
     * nearest-fix search over a trace that passes near itself picks a fix from the wrong part of the
     * drive, so boundaries jump ahead and swallow their neighbours. It gave a town street at
     * 160 km/h and seven segments with no time at all.
     */
    private fun timeline(
        points: List<SnappedTimedPoint>,
        alongRoad: DoubleArray,
        raw: List<RoutePoint>,
    ): LongArray {
        val anchorIndex = mutableListOf<Int>()
        val anchorTime = mutableListOf<Long>()
        var previous = Long.MIN_VALUE
        for (i in points.indices) {
            val timestamp = points[i].timestamp ?: continue
            // A snapped point can only anchor time if it moves time forward; the API occasionally
            // repeats a position, and a non-increasing anchor would make a segment run backwards.
            if (timestamp <= previous) continue
            anchorIndex += i
            anchorTime += timestamp
            previous = timestamp
        }

        // The drive's own start and end anchor the ends, so the segments sum to the trip's duration
        // rather than to the span between the first and last snapped fix.
        val startedAt = raw.first().timestamp
        val endedAt = raw.last().timestamp
        if (anchorIndex.firstOrNull() != 0 && startedAt <= (anchorTime.firstOrNull() ?: Long.MAX_VALUE)) {
            anchorIndex.add(0, 0)
            anchorTime.add(0, startedAt)
        }
        if (anchorIndex.last() != points.lastIndex && endedAt >= anchorTime.last()) {
            anchorIndex += points.lastIndex
            anchorTime += endedAt
        }

        val times = LongArray(points.size)
        for (i in 0 until anchorIndex.first()) times[i] = anchorTime.first()
        for (i in anchorIndex.last() until points.size) times[i] = anchorTime.last()
        for (k in 0 until anchorIndex.size - 1) {
            val from = anchorIndex[k]
            val to = anchorIndex[k + 1]
            val startTime = anchorTime[k]
            val span = alongRoad[to] - alongRoad[from]
            times[from] = startTime
            times[to] = anchorTime[k + 1]
            for (i in from + 1 until to) {
                times[i] = if (span <= 0.0) {
                    startTime
                } else {
                    startTime + ((anchorTime[k + 1] - startTime) * (alongRoad[i] - alongRoad[from]) / span)
                        .roundToLong()
                }
            }
        }
        return times
    }

    /** Adjacent runs naming the same road are one stretch of road, whatever Google's feature ids say. */
    private fun mergeAdjacentSameRoad(runs: List<Run>): MutableList<Run> {
        val merged = mutableListOf<Run>()
        for (run in runs) {
            val previous = merged.lastOrNull()
            if (previous != null && previous.name == run.name) {
                previous.endIndex = run.endIndex
                previous.endPlaceId = run.endPlaceId
            } else {
                merged += run.copy()
            }
        }
        return merged
    }

    /**
     * Folds every segment shorter than [MIN_SEGMENT_METERS] into its longer neighbour, then
     * re-merges neighbours that end up naming the same road.
     *
     * A 60 m split is not a stretch of road anyone drives as a unit — it is a slip road, a
     * roundabout exit, or the geocoder naming one point after the side street it happened to be
     * nearest. Half the segments on the reference drive were under 250 m, which both buried the real
     * splits and made the times too short to mean anything: a segment that takes four seconds is
     * decided by where a GPS fix landed, not by how it was driven. Absorbing rather than dropping
     * keeps every metre and second of the drive accounted for.
     */
    private fun absorbShortSegments(
        runs: MutableList<Run>,
        lastIndex: Int,
        time: LongArray,
        trace: RawTrace,
    ): MutableList<Run> {
        var current = runs
        while (current.size > 1) {
            val lengths = DoubleArray(current.size) { metersOf(current, it, lastIndex, time, trace) }
            val shortest = lengths.indices.minByOrNull { lengths[it] } ?: break
            if (lengths[shortest] >= MIN_SEGMENT_METERS) break

            val into = when {
                shortest == 0 -> 1
                shortest == current.size - 1 -> shortest - 1
                lengths[shortest - 1] >= lengths[shortest + 1] -> shortest - 1
                else -> shortest + 1
            }
            val low = min(shortest, into)
            val high = max(shortest, into)
            // The surviving name is the neighbour's: the longer stretch is the one being driven.
            val kept = current[into].copy(
                startIndex = current[low].startIndex,
                endIndex = current[high].endIndex,
                placeId = current[low].placeId,
                endPlaceId = current[high].endPlaceId,
            )
            current = (current.subList(0, low) + kept + current.subList(high + 1, current.size))
                .toMutableList()
            current = mergeAdjacentSameRoad(current)
        }
        return current
    }

    /** How far the car actually travelled over the stretch run [position] covers. */
    private fun metersOf(
        runs: List<Run>,
        position: Int,
        lastIndex: Int,
        time: LongArray,
        trace: RawTrace,
    ): Double {
        val from = runs[position].startIndex
        val to = if (position + 1 < runs.size) runs[position + 1].startIndex else lastIndex
        return trace.metersBetween(trace.indexAt(time[from]), trace.indexAt(time[to]))
    }

    /** One stretch of road, as a range of snapped points. */
    private data class Run(
        var startIndex: Int,
        var endIndex: Int,
        var placeId: String,
        var endPlaceId: String,
        var name: String? = null,
    )

    /**
     * The raw GPS trace, addressed by time: where the car was and how fast, as opposed to where the
     * road is. Cumulative distance is computed once so a segment costs a binary search rather than a
     * walk.
     */
    private class RawTrace(private val fixes: List<RoutePoint>) {
        private val meters = DoubleArray(fixes.size)

        init {
            for (i in 1 until fixes.size) {
                meters[i] = meters[i - 1] + GeoUtils.haversineMeters(
                    fixes[i - 1].lat, fixes[i - 1].lng, fixes[i].lat, fixes[i].lng,
                )
            }
        }

        /** The first fix at or after [timestamp] (the last fix, once time runs past the drive). */
        fun indexAt(timestamp: Long): Int {
            var low = 0
            var high = fixes.lastIndex
            while (low < high) {
                val middle = (low + high) / 2
                if (fixes[middle].timestamp < timestamp) low = middle + 1 else high = middle
            }
            return low
        }

        fun metersBetween(fromIndex: Int, toIndex: Int): Double =
            (meters[max(fromIndex, toIndex)] - meters[min(fromIndex, toIndex)]).coerceAtLeast(0.0)

        fun maxSpeedMps(fromIndex: Int, toIndex: Int): Float {
            var fastest = 0f
            for (i in min(fromIndex, toIndex)..max(fromIndex, toIndex)) {
                fastest = max(fastest, fixes[i].speedMps)
            }
            return fastest
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
                val coords = chunk.map { it.lat to it.lng }
                segments += makeSegment(
                    tripId = tripId,
                    index = index++,
                    start = coords.first(),
                    end = coords.last(),
                    distanceMeters = pathDistanceMeters(coords),
                    durationMs = chunk.last().timestamp - chunk.first().timestamp,
                    maxSpeedMps = chunk.maxOf { it.speedMps },
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

    private fun metersBetween(a: SnappedTimedPoint, b: SnappedTimedPoint): Double =
        GeoUtils.haversineMeters(a.lat, a.lng, b.lat, b.lng)

    private fun cumulativeMeters(points: List<SnappedTimedPoint>): DoubleArray {
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + metersBetween(points[i - 1], points[i])
        }
        return cumulative
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
        start: Pair<Double, Double>,
        end: Pair<Double, Double>,
        distanceMeters: Double,
        durationMs: Long,
        maxSpeedMps: Float,
        roadName: String,
        roadKey: String,
    ): Segment {
        val avgSpeed = if (durationMs > 0) (distanceMeters / (durationMs / 1000.0)).toFloat() else 0f

        return Segment(
            tripId = tripId,
            segmentIndex = index,
            roadKey = roadKey,
            roadName = roadName,
            startLat = start.first,
            startLng = start.second,
            endLat = end.first,
            endLng = end.second,
            distanceMeters = distanceMeters.toFloat(),
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

        /**
         * The floor a segment has to clear to stand on its own. 250 m is roughly a city block or a
         * motorway slip road: short enough that a genuine change of road survives as its own split,
         * long enough that GPS noise and geocoder slips do not. On the reference drive it took the
         * count from 159 to 72 — a 2.4 km mean, which is a stretch a driver would recognise.
         */
        const val MIN_SEGMENT_METERS = 250.0

        /** ~216 km/h: faster than any car on this app's roads, by enough to be an artefact. */
        const val MAX_PLAUSIBLE_MPS = 60.0

        /** How much longer than a straight line the road between two fixes may plausibly run. */
        const val DETOUR_FACTOR = 1.5
        const val DETOUR_SLACK_METERS = 50.0

        const val UNKNOWN_ROAD = "Unknown road"
        const val SEP = "|"
    }
}
