package app.drivedelta.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.drivedelta.data.local.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(place: PlaceEntity)

    @Delete
    suspend fun delete(place: PlaceEntity)

    @Query("SELECT * FROM places WHERE userId = :userId ORDER BY createdAt DESC")
    fun getByUser(userId: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE userId = :userId AND syncedAt IS NULL")
    suspend fun getPendingSync(userId: String): List<PlaceEntity>
}
