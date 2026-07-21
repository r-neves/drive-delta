package app.drivedelta.domain.usecase.segment

import app.drivedelta.core.util.GeoUtils
import app.drivedelta.data.remote.roads.RoadsDataSource
import app.drivedelta.data.remote.roads.SnappedTimedPoint
import app.drivedelta.data.remote.roads.TimedPoint
import app.drivedelta.domain.repository.TripRepository
import javax.inject.Inject

/**
 * Snaps a completed trip's raw GPS trace to the road network (F7), post-ride only. Loads the trip's
 * non-interpolated points, thins them with RDP (ε = 10 m) to cut the Roads API call count, and snaps
 * via [RoadsDataSource]. Returns the snapped points, or null when the trip is too short or the Roads
 * API is unavailable — the caller then falls back to raw distance-based segmentation.
 */
class SnapRouteToRoadsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val roadsDataSource: RoadsDataSource,
) {
    suspend operator fun invoke(tripId: String): List<SnappedTimedPoint>? {
        val raw = tripRepository.getRoutePoints(tripId).filter { !it.isInterpolated }
        if (raw.size < 2) return null

        val keptIndices = GeoUtils.simplify(raw.map { it.lat to it.lng }, EPSILON_METERS)
        val thinned = keptIndices.map { i ->
            TimedPoint(raw[i].lat, raw[i].lng, raw[i].timestamp, raw[i].speedMps)
        }

        return runCatching { roadsDataSource.snapToRoads(thinned) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val EPSILON_METERS = 10.0
    }
}
