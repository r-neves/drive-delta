package app.drivedelta.data.repository

import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.core.sync.SyncTrigger
import app.drivedelta.data.local.dao.EnergyPricesDao
import app.drivedelta.data.local.entity.EnergyPricesEntity
import app.drivedelta.domain.model.ElectricTariff
import app.drivedelta.domain.model.EnergyPrices
import app.drivedelta.domain.repository.EnergyPricesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [EnergyPricesRepository], scoped to the signed-in user. Reads fall back to
 * [EnergyPrices.default] when nothing is stored yet; writes reset `syncedAt` and trigger a push.
 */
class EnergyPricesRepositoryImpl @Inject constructor(
    private val energyPricesDao: EnergyPricesDao,
    private val authRepository: AuthRepository,
    private val syncTrigger: SyncTrigger,
) : EnergyPricesRepository {

    override fun observePrices(): Flow<EnergyPrices> {
        val userId = authRepository.currentUserId ?: return flowOf(EnergyPrices.default(""))
        return energyPricesDao.getByUser(userId).map { row -> row?.toDomain() ?: EnergyPrices.default(userId) }
    }

    override suspend fun getPrices(): EnergyPrices {
        val userId = authRepository.currentUserId ?: return EnergyPrices.default("")
        return energyPricesDao.getOnce(userId)?.toDomain() ?: EnergyPrices.default(userId)
    }

    override suspend fun savePrices(prices: EnergyPrices) {
        val userId = authRepository.currentUserId ?: return
        energyPricesDao.insertOrReplace(prices.toEntity(userId))
        syncTrigger.requestSync()
    }
}

private fun EnergyPricesEntity.toDomain(): EnergyPrices = EnergyPrices(
    userId = userId,
    currencyCode = currencyCode,
    petrolPricePerL = petrolPricePerL,
    dieselPricePerL = dieselPricePerL,
    lpgPricePerL = lpgPricePerL,
    electricityHomePerKwh = electricityHomePerKwh,
    electricityPublicPerKwh = electricityPublicPerKwh,
    defaultElectricTariff = ElectricTariff.fromStorage(defaultElectricTariff),
    estimateWhenNotLogged = estimateWhenNotLogged,
    askAfterEveryDrive = askAfterEveryDrive,
)

private fun EnergyPrices.toEntity(currentUserId: String): EnergyPricesEntity = EnergyPricesEntity(
    userId = currentUserId,
    currencyCode = currencyCode,
    petrolPricePerL = petrolPricePerL,
    dieselPricePerL = dieselPricePerL,
    lpgPricePerL = lpgPricePerL,
    electricityHomePerKwh = electricityHomePerKwh,
    electricityPublicPerKwh = electricityPublicPerKwh,
    defaultElectricTariff = defaultElectricTariff.name,
    estimateWhenNotLogged = estimateWhenNotLogged,
    askAfterEveryDrive = askAfterEveryDrive,
    syncedAt = null,
)
