package app.drivedelta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.drivedelta.data.local.entity.RoutePointEntity

@Dao
interface RoutePointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<RoutePointEntity>)

    @Query("SELECT * FROM route_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getByTrip(tripId: String): List<RoutePointEntity>

    @Query("DELETE FROM route_points WHERE tripId = :tripId")
    suspend fun deleteByTrip(tripId: String)
}
