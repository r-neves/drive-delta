package app.drivedelta.data.repository

import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.core.sync.SyncTrigger
import app.drivedelta.data.local.dao.RoutePointDao
import app.drivedelta.data.local.dao.TripDao
import app.drivedelta.data.local.entity.RoutePointEntity
import app.drivedelta.data.local.entity.TripEntity
import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [TripRepository]. Trip rows are scoped to [AuthRepository.currentUserId] and pushed to
 * Firestore on write (via [SyncTrigger]); route points stay local-only for MVP so
 * [appendRoutePoints] never triggers a sync. Writes that reset `syncedAt = null` re-queue the trip.
 */
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
    private val routePointDao: RoutePointDao,
    private val authRepository: AuthRepository,
    private val syncTrigger: SyncTrigger,
) : TripRepository {

    override fun observeTrips(): Flow<List<Trip>> {
        val userId = authRepository.currentUserId ?: return flowOf(emptyList())
        return tripDao.getByUser(userId).map { rows -> rows.map(TripEntity::toDomain) }
    }

    override suspend fun getTrip(id: String): Trip? {
        val userId = authRepository.currentUserId ?: return null
        return tripDao.getById(id)?.takeIf { it.userId == userId }?.toDomain()
    }

    override suspend fun startTrip(trip: Trip) {
        val userId = authRepository.currentUserId ?: return
        tripDao.insertOrReplace(trip.toEntity(userId))
        syncTrigger.requestSync()
    }

    override suspend fun updateStartLocation(tripId: String, lat: Double, lng: Double) {
        val current = tripDao.getById(tripId) ?: return
        tripDao.update(current.copy(startLat = lat, startLng = lng, syncedAt = null))
    }

    override suspend fun appendRoutePoints(points: List<RoutePoint>) {
        if (points.isEmpty()) return
        routePointDao.insertAll(points.map(RoutePoint::toEntity))
    }

    override suspend fun getRoutePoints(tripId: String): List<RoutePoint> =
        routePointDao.getByTrip(tripId).map(RoutePointEntity::toDomain)

    override suspend fun finishTrip(
        tripId: String,
        endTime: Long,
        endLat: Double,
        endLng: Double,
        distanceMeters: Float,
        durationMs: Long,
        stopTrigger: String,
    ) {
        val current = tripDao.getById(tripId) ?: return
        tripDao.update(
            current.copy(
                endTime = endTime,
                endLat = endLat,
                endLng = endLng,
                distanceMeters = distanceMeters,
                durationMs = durationMs,
                stopTrigger = stopTrigger,
                syncedAt = null,
            ),
        )
        syncTrigger.requestSync()
    }
}

/** Room row → domain. */
private fun TripEntity.toDomain(): Trip = Trip(
    id = id,
    userId = userId,
    startTime = startTime,
    endTime = endTime,
    startLat = startLat,
    startLng = startLng,
    endLat = endLat,
    endLng = endLng,
    startPlaceId = startPlaceId,
    endPlaceId = endPlaceId,
    carId = carId,
    distanceMeters = distanceMeters,
    durationMs = durationMs,
    routeHash = routeHash,
    stopTrigger = stopTrigger,
    roadsProcessed = roadsProcessed,
    notes = notes,
)

/** Domain → Room row. [userId] is forced to the current session; [syncedAt] reset for re-push. */
private fun Trip.toEntity(currentUserId: String): TripEntity = TripEntity(
    id = id,
    userId = currentUserId,
    startTime = startTime,
    endTime = endTime,
    startLat = startLat,
    startLng = startLng,
    endLat = endLat,
    endLng = endLng,
    startPlaceId = startPlaceId,
    endPlaceId = endPlaceId,
    carId = carId,
    distanceMeters = distanceMeters,
    durationMs = durationMs,
    routeHash = routeHash,
    stopTrigger = stopTrigger,
    roadsProcessed = roadsProcessed,
    syncedAt = null,
    notes = notes,
)

private fun RoutePointEntity.toDomain(): RoutePoint = RoutePoint(
    tripId = tripId,
    timestamp = timestamp,
    lat = lat,
    lng = lng,
    accuracyMeters = accuracyMeters,
    speedMps = speedMps,
    altitudeMeters = altitudeMeters,
    isInterpolated = isInterpolated,
)

/** Domain → Room row. [id] is left at 0 so Room autogenerates the primary key on insert. */
private fun RoutePoint.toEntity(): RoutePointEntity = RoutePointEntity(
    tripId = tripId,
    timestamp = timestamp,
    lat = lat,
    lng = lng,
    accuracyMeters = accuracyMeters,
    speedMps = speedMps,
    altitudeMeters = altitudeMeters,
    isInterpolated = isInterpolated,
)
