package app.drivedelta.domain.usecase.fuel

import app.drivedelta.domain.model.FuelLog
import app.drivedelta.domain.repository.FuelLogRepository
import javax.inject.Inject

/** Saves a fuel/charging log (F12). */
class LogFuelUseCase @Inject constructor(
    private val repository: FuelLogRepository,
) {
    suspend operator fun invoke(log: FuelLog) = repository.saveFuelLog(log)
}
