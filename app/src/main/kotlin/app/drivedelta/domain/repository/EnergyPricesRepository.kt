package app.drivedelta.domain.repository

import app.drivedelta.domain.model.EnergyPrices
import kotlinx.coroutines.flow.Flow

/**
 * The user's global energy-pricing settings. Room is the source of truth and Firestore is the remote
 * mirror. Reads emit the stored settings, or [EnergyPrices.default] until the user has saved any —
 * so callers always get usable prices without a null check.
 */
interface EnergyPricesRepository {

    /** Streams the current user's settings, seeded with defaults when none are stored yet. */
    fun observePrices(): Flow<EnergyPrices>

    /** One-shot read of the current user's settings (defaults when none are stored yet). */
    suspend fun getPrices(): EnergyPrices

    /** Persists the settings for the signed-in user and requests a Firestore push. */
    suspend fun savePrices(prices: EnergyPrices)
}
