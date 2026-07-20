package app.drivedelta.ui.cars

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.drivedelta.R
import app.drivedelta.domain.model.FuelType
import app.drivedelta.ui.theme.DdFuelDiesel
import app.drivedelta.ui.theme.DdFuelElectric
import app.drivedelta.ui.theme.DdFuelHybrid
import app.drivedelta.ui.theme.DdFuelLpg
import app.drivedelta.ui.theme.DdFuelPetrol

/**
 * Presentation mapping for a [FuelType]: its badge colour (design/tokens.md §2.3) and label string.
 * Badges must always render the label — Diesel shares [primary] and Electric shares [success], so
 * colour alone is ambiguous.
 */
val FuelType.badgeColor: Color
    get() = when (this) {
        FuelType.ELECTRIC -> DdFuelElectric
        FuelType.DIESEL -> DdFuelDiesel
        FuelType.PETROL -> DdFuelPetrol
        FuelType.HYBRID -> DdFuelHybrid
        FuelType.LPG -> DdFuelLpg
    }

@get:StringRes
val FuelType.labelRes: Int
    get() = when (this) {
        FuelType.PETROL -> R.string.fuel_petrol
        FuelType.DIESEL -> R.string.fuel_diesel
        FuelType.HYBRID -> R.string.fuel_hybrid
        FuelType.ELECTRIC -> R.string.fuel_electric
        FuelType.LPG -> R.string.fuel_lpg
    }
