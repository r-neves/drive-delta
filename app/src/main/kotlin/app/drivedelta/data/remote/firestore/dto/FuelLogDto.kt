package app.drivedelta.data.remote.firestore.dto

import app.drivedelta.data.local.entity.FuelLogEntity
import app.drivedelta.data.remote.firestore.float
import app.drivedelta.data.remote.firestore.floatOrNull
import app.drivedelta.data.remote.firestore.long
import app.drivedelta.data.remote.firestore.string
import app.drivedelta.data.remote.firestore.stringOrNull

/**
 * Firestore representation of a [FuelLogEntity]. Fuel fields and energy fields are mutually
 * exclusive and stay null when not applicable. The local-only `syncedAt` marker is excluded from the
 * wire format.
 */
data class FuelLogDto(
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
) {

    /** Serializes to a Firestore-friendly primitive map. Floats widen to [Double]; nullables stay null. */
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "tripId" to tripId,
        "carId" to carId,
        "timestamp" to timestamp,
        "liters" to liters?.toDouble(),
        "pricePerLiter" to pricePerLiter?.toDouble(),
        "kwhCharged" to kwhCharged?.toDouble(),
        "pricePerKwh" to pricePerKwh?.toDouble(),
        "totalCost" to totalCost.toDouble(),
        "odometerKm" to odometerKm?.toDouble(),
    )

    /** Rehydrates a [FuelLogEntity], stamping [syncedAt] with the value from the pull. */
    fun toEntity(syncedAt: Long): FuelLogEntity = FuelLogEntity(
        id = id,
        userId = userId,
        tripId = tripId,
        carId = carId,
        timestamp = timestamp,
        liters = liters,
        pricePerLiter = pricePerLiter,
        kwhCharged = kwhCharged,
        pricePerKwh = pricePerKwh,
        totalCost = totalCost,
        odometerKm = odometerKm,
        syncedAt = syncedAt,
    )

    companion object {

        fun fromEntity(e: FuelLogEntity): FuelLogDto = FuelLogDto(
            id = e.id,
            userId = e.userId,
            tripId = e.tripId,
            carId = e.carId,
            timestamp = e.timestamp,
            liters = e.liters,
            pricePerLiter = e.pricePerLiter,
            kwhCharged = e.kwhCharged,
            pricePerKwh = e.pricePerKwh,
            totalCost = e.totalCost,
            odometerKm = e.odometerKm,
        )

        fun fromMap(id: String, m: Map<String, Any?>): FuelLogDto = FuelLogDto(
            id = id,
            userId = m.string("userId"),
            tripId = m.stringOrNull("tripId"),
            carId = m.stringOrNull("carId"),
            timestamp = m.long("timestamp"),
            liters = m.floatOrNull("liters"),
            pricePerLiter = m.floatOrNull("pricePerLiter"),
            kwhCharged = m.floatOrNull("kwhCharged"),
            pricePerKwh = m.floatOrNull("pricePerKwh"),
            totalCost = m.float("totalCost"),
            odometerKm = m.floatOrNull("odometerKm"),
        )
    }
}
