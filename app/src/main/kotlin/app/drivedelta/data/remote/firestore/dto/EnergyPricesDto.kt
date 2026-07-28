package app.drivedelta.data.remote.firestore.dto

import app.drivedelta.data.local.entity.EnergyPricesEntity
import app.drivedelta.data.remote.firestore.bool
import app.drivedelta.data.remote.firestore.float
import app.drivedelta.data.remote.firestore.string

/**
 * Firestore representation of an [EnergyPricesEntity] (the user's global pricing settings). Stored as
 * a single document `energy_prices` under the user's `settings` collection. The local-only `syncedAt`
 * marker is excluded from the wire format.
 */
data class EnergyPricesDto(
    val userId: String,
    val currencyCode: String,
    val petrolPricePerL: Float,
    val dieselPricePerL: Float,
    val lpgPricePerL: Float,
    val electricityHomePerKwh: Float,
    val electricityPublicPerKwh: Float,
    val defaultElectricTariff: String,
    val estimateWhenNotLogged: Boolean,
    val askAfterEveryDrive: Boolean,
) {

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "currencyCode" to currencyCode,
        "petrolPricePerL" to petrolPricePerL.toDouble(),
        "dieselPricePerL" to dieselPricePerL.toDouble(),
        "lpgPricePerL" to lpgPricePerL.toDouble(),
        "electricityHomePerKwh" to electricityHomePerKwh.toDouble(),
        "electricityPublicPerKwh" to electricityPublicPerKwh.toDouble(),
        "defaultElectricTariff" to defaultElectricTariff,
        "estimateWhenNotLogged" to estimateWhenNotLogged,
        "askAfterEveryDrive" to askAfterEveryDrive,
    )

    fun toEntity(syncedAt: Long): EnergyPricesEntity = EnergyPricesEntity(
        userId = userId,
        currencyCode = currencyCode,
        petrolPricePerL = petrolPricePerL,
        dieselPricePerL = dieselPricePerL,
        lpgPricePerL = lpgPricePerL,
        electricityHomePerKwh = electricityHomePerKwh,
        electricityPublicPerKwh = electricityPublicPerKwh,
        defaultElectricTariff = defaultElectricTariff,
        estimateWhenNotLogged = estimateWhenNotLogged,
        askAfterEveryDrive = askAfterEveryDrive,
        syncedAt = syncedAt,
    )

    companion object {
        /** The fixed document id under the user's `settings` collection. */
        const val DOC_ID = "energy_prices"

        fun fromEntity(e: EnergyPricesEntity): EnergyPricesDto = EnergyPricesDto(
            userId = e.userId,
            currencyCode = e.currencyCode,
            petrolPricePerL = e.petrolPricePerL,
            dieselPricePerL = e.dieselPricePerL,
            lpgPricePerL = e.lpgPricePerL,
            electricityHomePerKwh = e.electricityHomePerKwh,
            electricityPublicPerKwh = e.electricityPublicPerKwh,
            defaultElectricTariff = e.defaultElectricTariff,
            estimateWhenNotLogged = e.estimateWhenNotLogged,
            askAfterEveryDrive = e.askAfterEveryDrive,
        )

        fun fromMap(userId: String, m: Map<String, Any?>): EnergyPricesDto = EnergyPricesDto(
            userId = userId,
            currencyCode = m.string("currencyCode", "EUR"),
            petrolPricePerL = m.float("petrolPricePerL"),
            dieselPricePerL = m.float("dieselPricePerL"),
            lpgPricePerL = m.float("lpgPricePerL"),
            electricityHomePerKwh = m.float("electricityHomePerKwh"),
            electricityPublicPerKwh = m.float("electricityPublicPerKwh"),
            defaultElectricTariff = m.string("defaultElectricTariff", "PUBLIC"),
            estimateWhenNotLogged = m.bool("estimateWhenNotLogged", true),
            askAfterEveryDrive = m.bool("askAfterEveryDrive", true),
        )
    }
}
