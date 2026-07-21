package app.drivedelta.data.remote.roads

import javax.inject.Inject
import kotlin.math.min

/**
 * Snaps a full route to roads by splitting it into ≤100-point chunks (the Roads API hard limit) with
 * a 10-point overlap so chunk boundaries stitch smoothly, then de-duplicating the overlap by
 * original point index. Any HTTP/parse failure propagates so the caller can fall back to raw
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
            val response = service.snapToRoads(path = path, interpolate = true)

            for (sp in response.snappedPoints) {
                val globalIndex = sp.originalIndex?.let { start + it }
                // Drop overlap duplicates: a real point already emitted from the previous chunk.
                if (globalIndex != null && globalIndex <= lastOriginalIndexAdded) continue
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

    private companion object {
        const val CHUNK_SIZE = 100
        const val OVERLAP = 10
    }
}
