package app.drivedelta.domain.model

/**
 * A recorded (or in-progress) car drive, as the domain sees it. [userId] is stamped by the
 * repository from the signed-in user on save. While a trip is being recorded the end fields are
 * null; [app.drivedelta.service.TrackingForegroundService] fills them in on stop. [routeHash],
 * [stopTrigger] and [roadsProcessed] are populated by the Roads/segment pass (Checkpoint 7).
 */
data class Trip(
    val id: String,
    val userId: String,
    val startTime: Long,
    val endTime: Long?,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double?,
    val endLng: Double?,
    val startPlaceId: String?,
    val endPlaceId: String?,
    val carId: String?,
    val distanceMeters: Float,
    val durationMs: Long,
    val routeHash: String = "",
    val stopTrigger: String = "",
    val roadsProcessed: Boolean = false,
    val notes: String = "",
)
