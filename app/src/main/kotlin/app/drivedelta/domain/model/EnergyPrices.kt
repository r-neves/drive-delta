package app.drivedelta.domain.model

/**
 * The user's global energy-pricing settings (design/mockups/settings-energy-prices.png). One row per
 * user, synced like any other entity. Prices are per litre for liquid fuels and per kWh for electric.
 *
 * Electric drives can be charged at a [home][electricityHomePerKwh] or
 * [public][electricityPublicPerKwh] tariff; [defaultElectricTariff] is pre-selected when logging a new
 * electric drive and can be switched per drive. Liquid fuels (petrol/diesel/hybrid/LPG) each have a
 * single price. [userId] is stamped by the repository from the signed-in user on save.
 */
data class EnergyPrices(
    val userId: String,
    val currencyCode: String = DEFAULT_CURRENCY,
    val petrolPricePerL: Float = DEFAULT_PETROL,
    val dieselPricePerL: Float = DEFAULT_DIESEL,
    val lpgPricePerL: Float = DEFAULT_LPG,
    val electricityHomePerKwh: Float = DEFAULT_ELECTRICITY_HOME,
    val electricityPublicPerKwh: Float = DEFAULT_ELECTRICITY_PUBLIC,
    val defaultElectricTariff: ElectricTariff = ElectricTariff.PUBLIC,
    val estimateWhenNotLogged: Boolean = true,
    val askAfterEveryDrive: Boolean = true,
) {

    /** The per-kWh price for a given electric [tariff]. */
    fun electricityPrice(tariff: ElectricTariff): Float = when (tariff) {
        ElectricTariff.HOME -> electricityHomePerKwh
        ElectricTariff.PUBLIC -> electricityPublicPerKwh
    }

    /**
     * The per-unit price to bill a drive in the given [car]: the electric tariff (defaulting to
     * [defaultElectricTariff]) for electric cars, otherwise the matching liquid-fuel price. Hybrids
     * are billed at the petrol price.
     */
    fun unitPriceFor(car: Car, tariff: ElectricTariff = defaultElectricTariff): Float =
        when (car.fuelType) {
            FuelType.ELECTRIC -> electricityPrice(tariff)
            FuelType.DIESEL -> dieselPricePerL
            FuelType.LPG -> lpgPricePerL
            FuelType.PETROL, FuelType.HYBRID -> petrolPricePerL
        }

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
        const val DEFAULT_PETROL = 2.00f
        const val DEFAULT_DIESEL = 1.96f
        const val DEFAULT_LPG = 0.96f
        const val DEFAULT_ELECTRICITY_HOME = 0.155781f
        const val DEFAULT_ELECTRICITY_PUBLIC = 0.79f

        /** The seed settings for a user who has never opened Energy Prices. */
        fun default(userId: String): EnergyPrices = EnergyPrices(userId = userId)
    }
}

/** Which electricity tariff an electric drive is billed at. */
enum class ElectricTariff {
    HOME,
    PUBLIC,
    ;

    companion object {
        fun fromStorage(token: String): ElectricTariff =
            entries.firstOrNull { it.name == token } ?: PUBLIC
    }
}
