package app.drivedelta.data.repository

import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.core.sync.SyncTrigger
import app.drivedelta.data.local.dao.FuelLogDao
import app.drivedelta.data.local.entity.FuelLogEntity
import app.drivedelta.domain.model.FuelLog
import app.drivedelta.domain.repository.FuelLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Room-backed [FuelLogRepository], scoped to the signed-in user; writes reset `syncedAt` + sync. */
class FuelLogRepositoryImpl @Inject constructor(
    private val fuelLogDao: FuelLogDao,
    private val authRepository: AuthRepository,
    private val syncTrigger: SyncTrigger,
) : FuelLogRepository {

    override fun observeFuelLogs(): Flow<List<FuelLog>> {
        val userId = authRepository.currentUserId ?: return flowOf(emptyList())
        return fuelLogDao.getByUser(userId).map { rows -> rows.map(FuelLogEntity::toDomain) }
    }

    override suspend fun saveFuelLog(log: FuelLog) {
        val userId = authRepository.currentUserId ?: return
        fuelLogDao.insertOrReplace(log.toEntity(userId))
        syncTrigger.requestSync()
    }
}

private fun FuelLogEntity.toDomain(): FuelLog = FuelLog(
    id = id, userId = userId, tripId = tripId, carId = carId, timestamp = timestamp,
    liters = liters, pricePerLiter = pricePerLiter, kwhCharged = kwhCharged,
    pricePerKwh = pricePerKwh, totalCost = totalCost, odometerKm = odometerKm,
)

private fun FuelLog.toEntity(currentUserId: String): FuelLogEntity = FuelLogEntity(
    id = id, userId = currentUserId, tripId = tripId, carId = carId, timestamp = timestamp,
    liters = liters, pricePerLiter = pricePerLiter, kwhCharged = kwhCharged,
    pricePerKwh = pricePerKwh, totalCost = totalCost, odometerKm = odometerKm, syncedAt = null,
)
