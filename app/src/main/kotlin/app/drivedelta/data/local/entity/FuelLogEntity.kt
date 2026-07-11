package app.drivedelta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A fuel fill-up or energy charge. May be standalone or linked to a [tripId]. Fuel fields (liters,
 * pricePerLiter) and energy fields (kwhCharged, pricePerKwh) are mutually exclusive by fuel type.
 */
@Entity(tableName = "fuel_logs")
data class FuelLogEntity(
    @PrimaryKey val id: String,           // UUID
    val userId: String,
    val tripId: String?,                  // nullable — standalone log or linked to trip
    val carId: String?,
    val timestamp: Long,
    val liters: Float?,                   // null for electric logs
    val pricePerLiter: Float?,
    val kwhCharged: Float?,               // null for fuel logs
    val pricePerKwh: Float?,
    val totalCost: Float,
    val odometerKm: Float?,
    val syncedAt: Long? = null,
)
