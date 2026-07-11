package app.drivedelta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.drivedelta.data.local.entity.CarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(car: CarEntity)

    /** Soft delete: flag the row as deleted and mark it pending sync so the deletion propagates. */
    @Query("UPDATE cars SET isDeleted = 1, syncedAt = NULL WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT * FROM cars WHERE userId = :userId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getByUser(userId: String): Flow<List<CarEntity>>

    @Query("SELECT * FROM cars WHERE userId = :userId AND isDefault = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getDefault(userId: String): CarEntity?

    @Query("SELECT * FROM cars WHERE userId = :userId AND syncedAt IS NULL")
    suspend fun getPendingSync(userId: String): List<CarEntity>
}
