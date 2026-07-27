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

    /**
     * Hard delete: remove the row outright. Used to prune a tombstone (`isDeleted = 1`) once its
     * deletion has been pushed to Firestore, so hidden rows don't accumulate locally forever.
     */
    @Query("DELETE FROM cars WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM cars WHERE userId = :userId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getByUser(userId: String): Flow<List<CarEntity>>

    @Query("SELECT * FROM cars WHERE id = :id")
    suspend fun getById(id: String): CarEntity?

    @Query("SELECT * FROM cars WHERE userId = :userId AND isDefault = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getDefault(userId: String): CarEntity?

    /**
     * Clears the default flag on every other car for [userId], leaving [exceptId] untouched.
     * Touched rows are marked pending sync so the change propagates. Enforces "only one default".
     */
    @Query(
        "UPDATE cars SET isDefault = 0, syncedAt = NULL " +
            "WHERE userId = :userId AND id != :exceptId AND isDefault = 1",
    )
    suspend fun clearDefaultExcept(userId: String, exceptId: String)

    @Query("SELECT * FROM cars WHERE userId = :userId AND syncedAt IS NULL")
    suspend fun getPendingSync(userId: String): List<CarEntity>
}
