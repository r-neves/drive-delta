package app.drivedelta.core.sync

import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.data.local.dao.CarDao
import app.drivedelta.data.local.dao.FuelLogDao
import app.drivedelta.data.local.dao.PlaceDao
import app.drivedelta.data.local.dao.TripDao
import app.drivedelta.data.remote.firestore.FirestoreDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bidirectional Room ↔ Firestore sync. Room is the source of truth; Firestore is a remote mirror.
 *
 * Push: every row with syncedAt == null is pushed, then stamped syncedAt = now.
 * Pull: on a fresh device, all remote docs are merged into Room (INSERT OR REPLACE by UUID).
 *
 * Route points are local-only and never synced. Segment sync is trip-driven and arrives in
 * Checkpoint 7, so it is not part of the pending-push loop here.
 */
@Singleton
class SyncManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val remote: FirestoreDataSource,
    private val tripDao: TripDao,
    private val placeDao: PlaceDao,
    private val carDao: CarDao,
    private val fuelLogDao: FuelLogDao,
) {

    /** Pushes all locally-pending rows to Firestore. No-op when signed out. */
    suspend fun pushPending(): Result<Unit> {
        val userId = authRepository.currentUserId ?: return Result.success(Unit)
        return runCatching {
            val now = System.currentTimeMillis()

            tripDao.getPendingSync(userId).forEach { trip ->
                remote.pushTrip(trip)
                tripDao.update(trip.copy(syncedAt = now))
            }
            placeDao.getPendingSync(userId).forEach { place ->
                remote.pushPlace(place)
                placeDao.insertOrReplace(place.copy(syncedAt = now))
            }
            carDao.getPendingSync(userId).forEach { car ->
                remote.pushCar(car)
                carDao.insertOrReplace(car.copy(syncedAt = now))
            }
            fuelLogDao.getPendingSync(userId).forEach { log ->
                remote.pushFuelLog(log)
                fuelLogDao.insertOrReplace(log.copy(syncedAt = now))
            }
        }
    }

    /** Pulls all remote docs into Room. Called once after sign-in on a new device. */
    suspend fun pullAll(): Result<Unit> {
        val userId = authRepository.currentUserId ?: return Result.success(Unit)
        return runCatching {
            val snapshot = remote.pullAll(userId)
            snapshot.trips.forEach { tripDao.insertOrReplace(it) }
            snapshot.places.forEach { placeDao.insertOrReplace(it) }
            snapshot.cars.forEach { carDao.insertOrReplace(it) }
            snapshot.fuelLogs.forEach { fuelLogDao.insertOrReplace(it) }
        }
    }
}
