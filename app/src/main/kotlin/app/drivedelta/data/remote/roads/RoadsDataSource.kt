package app.drivedelta.data.remote.roads

import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.math.min

/**
 * Snaps a full route to roads by splitting it into ≤100-point chunks (the Roads API hard limit) with
 * a 10-point overlap so chunk boundaries stitch smoothly, then de-duplicating the overlap by
 * original point index. Transient failures (quota/5xx/network) are retried with exponential backoff
 * (Checkpoint 10); if all attempts fail the error propagates so the caller falls back to raw
 * distance-based segmentation.
 */
class RoadsDataSource @Inject constructor(
    private val service: RoadsApiService,
) {

    suspend fun snapToRoads(points: List<TimedPoint>): List<SnappedTimedPoint> {
        if (points.size < 2) return emptyList()

        val snapped = mutableListOf<SnappedTimedPoint>()
        var start = 0
        var lastOriginalIndexAdded = -1

        while (start < points.size) {
            val end = min(start + CHUNK_SIZE, points.size)
            val chunk = points.subList(start, end)
            val path = chunk.joinToString("|") { "${it.lat},${it.lng}" }
            val response = snapChunkWithRetry(path)

            // Everything a chunk returns for ground already covered by the previous chunk is a
            // duplicate — including the points the API interpolated between them, which carry no
            // originalIndex to recognise them by. Skipping until the first genuinely new input point
            // discards the whole overlap in one go. Dropping only the *real* duplicates (which is
            // what this did) left the interpolated ones in, so after every chunk boundary the route
            // jumped back ten input points and re-drove them: on a 173 km drive that was five
            // backward jumps of up to 4.6 km and 33 km of geometry the car never covered.
            var covered = start > 0
            for (sp in response.snappedPoints) {
                val globalIndex = sp.originalIndex?.let { start + it }
                if (covered) {
                    if (globalIndex == null || globalIndex <= lastOriginalIndexAdded) continue
                    covered = false
                }
                val source = globalIndex?.let { points.getOrNull(it) }
                snapped += SnappedTimedPoint(
                    lat = sp.location.latitude,
                    lng = sp.location.longitude,
                    placeId = sp.placeId,
                    timestamp = source?.timestamp,
                    speedMps = source?.speedMps,
                )
                if (globalIndex != null) lastOriginalIndexAdded = globalIndex
            }

            if (end >= points.size) break
            start = end - OVERLAP // step back so chunks overlap
        }
        return snapped
    }

    /** Calls the Roads API for one chunk, retrying transient failures with exponential backoff. */
    private suspend fun snapChunkWithRetry(path: String): RoadsSnapResponse {
        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        while (true) {
            try {
                return service.snapToRoads(path = path, interpolate = true)
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) throw e
                delay(backoffMs)
                backoffMs *= 2
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 100
        const val OVERLAP = 10
        const val MAX_ATTEMPTS = 3
        const val INITIAL_BACKOFF_MS = 500L
    }
}
