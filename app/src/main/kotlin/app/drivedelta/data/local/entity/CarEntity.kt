package app.drivedelta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user's vehicle. [isDeleted] is a soft-delete flag that is synced to Firestore before the row is
 * hard-deleted from Room. [syncedAt] is null while the row is pending a Firestore push.
 */
@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey val id: String,           // UUID
    val userId: String,
    val name: String,
    val licensePlate: String = "",
    // fuelType stored as String: "PETROL" | "DIESEL" | "HYBRID" | "ELECTRIC" | "LPG"
    val fuelType: String,
    val tankCapacityLiters: Float?,       // null for electric
    val batteryCapacityKwh: Float?,       // null for non-electric
    val defaultConsumption: Float?,       // L/100km or kWh/100km, user's estimate
    val isDefault: Boolean = false,
    val isDeleted: Boolean = false,       // soft delete; synced to Firestore before removal
    val createdAt: Long,
    val syncedAt: Long? = null,
)
