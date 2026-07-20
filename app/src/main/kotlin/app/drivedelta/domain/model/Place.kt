package app.drivedelta.domain.model

/**
 * A named location with a geofence radius, used for trip origin/destination detection and auto-stop.
 * [userId] is stamped by the repository from the signed-in user on save — a value supplied by the UI
 * is ignored — so per-user isolation lives in one place.
 */
data class Place(
    val id: String,
    val userId: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Float,
    val iconEmoji: String,
    val createdAt: Long,
)
