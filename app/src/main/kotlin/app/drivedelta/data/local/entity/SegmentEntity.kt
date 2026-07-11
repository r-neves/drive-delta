package app.drivedelta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stretch of road within a trip, computed post-ride after the Roads API snap. [roadKey] is the
 * stable cross-trip identifier for this stretch of road, formatted as
 * "RoadName|startLat4dp,startLng4dp|endLat4dp,endLng4dp".
 */
@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val segmentIndex: Int,
    val roadKey: String,
    val roadName: String,                 // human-readable, e.g. "A1 - Autoestrada do Norte"
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceMeters: Float,
    val durationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
)
