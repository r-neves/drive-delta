package app.drivedelta.domain.model

/**
 * A user's vehicle, as the domain sees it. [FuelType] is a typed enum here; the Room entity stores
 * it as a String. [userId] is stamped by the repository from the signed-in user on save — a value
 * supplied by the UI is ignored — so per-user isolation is enforced in one place.
 */
data class Car(
    val id: String,
    val userId: String,
    val name: String,
    val licensePlate: String,
    val fuelType: FuelType,
    val tankCapacityLiters: Float?,   // null for electric
    val batteryCapacityKwh: Float?,   // null for non-electric
    val defaultConsumption: Float?,   // L/100km or kWh/100km
    val isDefault: Boolean,
    val createdAt: Long,
)

/**
 * The five supported fuel types. [name] is the stable storage token persisted in
 * [app.drivedelta.data.local.entity.CarEntity.fuelType] and mirrored to Firestore.
 */
enum class FuelType {
    PETROL,
    DIESEL,
    HYBRID,
    ELECTRIC,
    LPG,
    ;

    /** Electric cars track battery capacity + kWh/100km instead of a tank + L/100km. */
    val isElectric: Boolean get() = this == ELECTRIC

    companion object {
        /** Parses a stored token back to a [FuelType], defaulting to [PETROL] for unknown values. */
        fun fromStorage(token: String): FuelType =
            entries.firstOrNull { it.name == token } ?: PETROL
    }
}
