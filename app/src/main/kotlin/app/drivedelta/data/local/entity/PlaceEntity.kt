package app.drivedelta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named location with a geofence radius, used for trip origin/destination detection and
 * auto-stop. [syncedAt] is null while the row is pending a Firestore push.
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,           // UUID
    val userId: String,
    val name: String,
    val address: String,                  // reverse-geocoded label
    val lat: Double,
    val lng: Double,
    val radiusMeters: Float = 100f,       // geofence radius 50–500m
    val iconEmoji: String = "📍",
    val createdAt: Long,
    val syncedAt: Long? = null,
)
