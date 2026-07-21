package app.drivedelta.domain.model

/**
 * A fuel fill-up or charging session (F12). Fuel logs use [liters]/[pricePerLiter]; electric logs
 * use [kwhCharged]/[pricePerKwh]; the unused pair is null. [tripId]/[carId] are optional links.
 * [userId] is stamped by the repository from the signed-in user on save.
 */
data class FuelLog(
    val id: String,
    val userId: String,
    val tripId: String?,
    val carId: String?,
    val timestamp: Long,
    val liters: Float?,
    val pricePerLiter: Float?,
    val kwhCharged: Float?,
    val pricePerKwh: Float?,
    val totalCost: Float,
    val odometerKm: Float?,
)
