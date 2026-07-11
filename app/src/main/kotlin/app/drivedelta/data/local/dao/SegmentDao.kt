package app.drivedelta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.drivedelta.data.local.entity.SegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<SegmentEntity>)

    @Query("SELECT * FROM segments WHERE tripId = :tripId ORDER BY segmentIndex ASC")
    fun getByTrip(tripId: String): Flow<List<SegmentEntity>>

    /**
     * Best (minimum) duration recorded for a given [roadKey] across all of the user's trips. Joins
     * segments to trips so the query is scoped to a single user. Returns null if no match exists.
     */
    @Query(
        """
        SELECT MIN(s.durationMs) FROM segments s
        INNER JOIN trips t ON s.tripId = t.id
        WHERE t.userId = :userId AND s.roadKey = :roadKey
        """,
    )
    suspend fun getBestDurationForRoadKey(userId: String, roadKey: String): Long?
}
