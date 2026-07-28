package app.drivedelta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's global energy-pricing settings, one row per user (keyed by [userId]). Liquid-fuel
 * prices are per litre and electricity tariffs are per kWh. [defaultElectricTariff] stores an
 * [app.drivedelta.domain.model.ElectricTariff] token. [syncedAt] is null while pending a Firestore push.
 */
@Entity(tableName = "energy_prices")
data class EnergyPricesEntity(
    @PrimaryKey val userId: String,
    val currencyCode: String,
    val petrolPricePerL: Float,
    val dieselPricePerL: Float,
    val lpgPricePerL: Float,
    val electricityHomePerKwh: Float,
    val electricityPublicPerKwh: Float,
    val defaultElectricTariff: String,   // "HOME" | "PUBLIC"
    val estimateWhenNotLogged: Boolean,
    val askAfterEveryDrive: Boolean,
    val syncedAt: Long? = null,
)
