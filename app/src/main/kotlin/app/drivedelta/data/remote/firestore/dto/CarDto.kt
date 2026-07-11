package app.drivedelta.data.remote.firestore.dto

import app.drivedelta.data.local.entity.CarEntity
import app.drivedelta.data.remote.firestore.bool
import app.drivedelta.data.remote.firestore.floatOrNull
import app.drivedelta.data.remote.firestore.long
import app.drivedelta.data.remote.firestore.string

/**
 * Firestore representation of a [CarEntity]. Written and read via explicit [Map] entries. Reading
 * `isDefault` / `isDeleted` by explicit key avoids the Firestore bean-naming gotcha where reflection
 * maps a Kotlin `isDefault` property to a `default` document field. The local-only `syncedAt` marker
 * is excluded from the wire format.
 */
data class CarDto(
    val id: String,
    val userId: String,
    val name: String,
    val licensePlate: String,
    val fuelType: String,
    val tankCapacityLiters: Float?,
    val batteryCapacityKwh: Float?,
    val defaultConsumption: Float?,
    val isDefault: Boolean,
    val isDeleted: Boolean,
    val createdAt: Long,
) {

    /** Serializes to a Firestore-friendly primitive map. Floats widen to [Double]; nullables stay null. */
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "name" to name,
        "licensePlate" to licensePlate,
        "fuelType" to fuelType,
        "tankCapacityLiters" to tankCapacityLiters?.toDouble(),
        "batteryCapacityKwh" to batteryCapacityKwh?.toDouble(),
        "defaultConsumption" to defaultConsumption?.toDouble(),
        "isDefault" to isDefault,
        "isDeleted" to isDeleted,
        "createdAt" to createdAt,
    )

    /** Rehydrates a [CarEntity], stamping [syncedAt] with the value from the pull. */
    fun toEntity(syncedAt: Long): CarEntity = CarEntity(
        id = id,
        userId = userId,
        name = name,
        licensePlate = licensePlate,
        fuelType = fuelType,
        tankCapacityLiters = tankCapacityLiters,
        batteryCapacityKwh = batteryCapacityKwh,
        defaultConsumption = defaultConsumption,
        isDefault = isDefault,
        isDeleted = isDeleted,
        createdAt = createdAt,
        syncedAt = syncedAt,
    )

    companion object {

        fun fromEntity(e: CarEntity): CarDto = CarDto(
            id = e.id,
            userId = e.userId,
            name = e.name,
            licensePlate = e.licensePlate,
            fuelType = e.fuelType,
            tankCapacityLiters = e.tankCapacityLiters,
            batteryCapacityKwh = e.batteryCapacityKwh,
            defaultConsumption = e.defaultConsumption,
            isDefault = e.isDefault,
            isDeleted = e.isDeleted,
            createdAt = e.createdAt,
        )

        fun fromMap(id: String, m: Map<String, Any?>): CarDto = CarDto(
            id = id,
            userId = m.string("userId"),
            name = m.string("name"),
            licensePlate = m.string("licensePlate"),
            fuelType = m.string("fuelType"),
            tankCapacityLiters = m.floatOrNull("tankCapacityLiters"),
            batteryCapacityKwh = m.floatOrNull("batteryCapacityKwh"),
            defaultConsumption = m.floatOrNull("defaultConsumption"),
            isDefault = m.bool("isDefault"),
            isDeleted = m.bool("isDeleted"),
            createdAt = m.long("createdAt"),
        )
    }
}
