package app.drivedelta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.drivedelta.data.local.entity.FuelLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(log: FuelLogEntity)

    @Query("SELECT * FROM fuel_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getByUser(userId: String): Flow<List<FuelLogEntity>>

    @Query("SELECT * FROM fuel_logs WHERE userId = :userId AND syncedAt IS NULL")
    suspend fun getPendingSync(userId: String): List<FuelLogEntity>
}
