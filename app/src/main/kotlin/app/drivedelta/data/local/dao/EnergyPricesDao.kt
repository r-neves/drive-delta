package app.drivedelta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.drivedelta.data.local.entity.EnergyPricesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EnergyPricesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(prices: EnergyPricesEntity)

    /** Streams the user's settings row, or null until they've been saved (defaults applied upstream). */
    @Query("SELECT * FROM energy_prices WHERE userId = :userId LIMIT 1")
    fun getByUser(userId: String): Flow<EnergyPricesEntity?>

    @Query("SELECT * FROM energy_prices WHERE userId = :userId LIMIT 1")
    suspend fun getOnce(userId: String): EnergyPricesEntity?

    @Query("SELECT * FROM energy_prices WHERE userId = :userId AND syncedAt IS NULL")
    suspend fun getPendingSync(userId: String): List<EnergyPricesEntity>
}
