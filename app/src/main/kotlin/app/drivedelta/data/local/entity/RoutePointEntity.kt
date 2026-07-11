package app.drivedelta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single GPS fix within a trip. Route points are stored in Room only and are never synced to
 * Firestore for MVP. [isInterpolated] marks points inserted to bridge a GPS gap.
 */
@Entity(tableName = "route_points")
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val altitudeMeters: Double,
    val isInterpolated: Boolean = false,  // true = GPS gap, linearly interpolated
)
