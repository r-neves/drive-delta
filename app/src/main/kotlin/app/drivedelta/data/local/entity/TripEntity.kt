package app.drivedelta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A recorded car drive. The [id] is a locally generated UUID and [userId] is the Firebase UID that
 * every query filters by. [syncedAt] is null while the row is pending a Firestore push.
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,           // UUID generated locally
    val userId: String,                   // Firebase UID — all queries filter by this
    val startTime: Long,                  // epoch ms
    val endTime: Long?,                   // null while in progress
    val startLat: Double,
    val startLng: Double,
    val endLat: Double?,
    val endLng: Double?,
    val startPlaceId: String?,            // FK to places.id
    val endPlaceId: String?,
    val carId: String?,                   // FK to cars.id
    val distanceMeters: Float,
    val durationMs: Long,
    val routeHash: String = "",           // SHA-256 of ordered roadKey sequence; set post-snap
    val stopTrigger: String = "",         // "MANUAL" | "GEOFENCE"
    val roadsProcessed: Boolean = false,  // true once Roads API snap is complete
    val syncedAt: Long? = null,           // null = pending Firestore sync
    val notes: String = "",
)
