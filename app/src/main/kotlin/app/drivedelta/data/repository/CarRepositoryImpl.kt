package app.drivedelta.data.repository

import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.core.sync.SyncTrigger
import app.drivedelta.data.local.dao.CarDao
import app.drivedelta.data.local.entity.CarEntity
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.FuelType
import app.drivedelta.domain.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [CarRepository]. Every read/write is scoped to [AuthRepository.currentUserId]; when
 * signed out reads emit empty and writes are ignored, so a caller can never touch another user's
 * rows. Writes stamp `syncedAt = null` (via [Car.toEntity]) for the periodic Firestore push.
 */
class CarRepositoryImpl @Inject constructor(
    private val carDao: CarDao,
    private val authRepository: AuthRepository,
    private val syncTrigger: SyncTrigger,
) : CarRepository {

    override fun observeCars(): Flow<List<Car>> {
        val userId = authRepository.currentUserId ?: return flowOf(emptyList())
        return carDao.getByUser(userId).map { rows -> rows.map(CarEntity::toDomain) }
    }

    override suspend fun getCar(id: String): Car? {
        val userId = authRepository.currentUserId ?: return null
        return carDao.getById(id)?.takeIf { it.userId == userId }?.toDomain()
    }

    override suspend fun saveCar(car: Car) {
        val userId = authRepository.currentUserId ?: return
        val entity = car.toEntity(userId)
        carDao.insertOrReplace(entity)
        if (entity.isDefault) {
            carDao.clearDefaultExcept(userId, entity.id)
        }
        syncTrigger.requestSync()
    }

    override suspend fun deleteCar(carId: String) {
        carDao.softDelete(carId)
        syncTrigger.requestSync()
    }
}

/** Room row → domain. */
private fun CarEntity.toDomain(): Car = Car(
    id = id,
    userId = userId,
    name = name,
    licensePlate = licensePlate,
    fuelType = FuelType.fromStorage(fuelType),
    tankCapacityLiters = tankCapacityLiters,
    batteryCapacityKwh = batteryCapacityKwh,
    defaultConsumption = defaultConsumption,
    isDefault = isDefault,
    createdAt = createdAt,
)

/**
 * Domain → Room row. [userId] is forced to the current session's value and [syncedAt] is reset to
 * null so the row is pushed on the next sync. A soft-deleted car re-saved this way is un-deleted,
 * which is exactly what the undo path wants.
 */
private fun Car.toEntity(currentUserId: String): CarEntity = CarEntity(
    id = id,
    userId = currentUserId,
    name = name,
    licensePlate = licensePlate,
    fuelType = fuelType.name,
    tankCapacityLiters = tankCapacityLiters,
    batteryCapacityKwh = batteryCapacityKwh,
    defaultConsumption = defaultConsumption,
    isDefault = isDefault,
    isDeleted = false,
    createdAt = createdAt,
    syncedAt = null,
)
