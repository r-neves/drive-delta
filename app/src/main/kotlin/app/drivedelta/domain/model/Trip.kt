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
) {
    /**
     * Which route this drive belongs to, for every "same route" grouping in the app (Trips by-route,
     * dashboard personal bests, route insights, the cost chart, "vs previous").
     *
     * The saved origin→destination pair when both ends are set — that is what a driver means by
     * "the same route". [routeHash] only as a fallback: it hashes the exact sequence of road
     * segments, so a lane change near a boundary or a geocoder that names one stretch differently
     * gives a different hash, and in practice no two real drives ever shared one (measured: 40
     * drives, 40 distinct hashes, including four Home → Parents Home on the same roads).
     * Direction matters — A→B and B→A are different routes. Null when neither identity exists.
     */
    val routeKey: String?
        get() = when {
            startPlaceId != null && endPlaceId != null -> "places:$startPlaceId>$endPlaceId"
            routeHash.isNotBlank() -> "hash:$routeHash"
            else -> null
        }
}
