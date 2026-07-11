package app.drivedelta.data.remote.firestore.dto

import app.drivedelta.data.local.entity.TripEntity
import app.drivedelta.data.remote.firestore.bool
import app.drivedelta.data.remote.firestore.double
import app.drivedelta.data.remote.firestore.doubleOrNull
import app.drivedelta.data.remote.firestore.float
import app.drivedelta.data.remote.firestore.long
import app.drivedelta.data.remote.firestore.longOrNull
import app.drivedelta.data.remote.firestore.string
import app.drivedelta.data.remote.firestore.stringOrNull

/**
 * Firestore representation of a [TripEntity]. Written and read via explicit [Map] entries — no
 * reflection or POJO mapping — so field names are stable and coercion is deterministic. The
 * local-only `syncedAt` marker is deliberately excluded from the wire format.
 */
data class TripDto(
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
    val routeHash: String,
    val stopTrigger: String,
    val roadsProcessed: Boolean,
    val notes: String,
) {

    /** Serializes to a Firestore-friendly primitive map. Floats widen to [Double]; nullables stay null. */
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "startTime" to startTime,
        "endTime" to endTime,
        "startLat" to startLat,
        "startLng" to startLng,
        "endLat" to endLat,
        "endLng" to endLng,
        "startPlaceId" to startPlaceId,
        "endPlaceId" to endPlaceId,
        "carId" to carId,
        "distanceMeters" to distanceMeters.toDouble(),
        "durationMs" to durationMs,
        "routeHash" to routeHash,
        "stopTrigger" to stopTrigger,
        "roadsProcessed" to roadsProcessed,
        "notes" to notes,
    )

    /** Rehydrates a [TripEntity], stamping [syncedAt] with the value from the pull. */
    fun toEntity(syncedAt: Long): TripEntity = TripEntity(
        id = id,
        userId = userId,
        startTime = startTime,
        endTime = endTime,
        startLat = startLat,
        startLng = startLng,
        endLat = endLat,
        endLng = endLng,
        startPlaceId = startPlaceId,
        endPlaceId = endPlaceId,
        carId = carId,
        distanceMeters = distanceMeters,
        durationMs = durationMs,
        routeHash = routeHash,
        stopTrigger = stopTrigger,
        roadsProcessed = roadsProcessed,
        syncedAt = syncedAt,
        notes = notes,
    )

    companion object {

        fun fromEntity(e: TripEntity): TripDto = TripDto(
            id = e.id,
            userId = e.userId,
            startTime = e.startTime,
            endTime = e.endTime,
            startLat = e.startLat,
            startLng = e.startLng,
            endLat = e.endLat,
            endLng = e.endLng,
            startPlaceId = e.startPlaceId,
            endPlaceId = e.endPlaceId,
            carId = e.carId,
            distanceMeters = e.distanceMeters,
            durationMs = e.durationMs,
            routeHash = e.routeHash,
            stopTrigger = e.stopTrigger,
            roadsProcessed = e.roadsProcessed,
            notes = e.notes,
        )

        fun fromMap(id: String, m: Map<String, Any?>): TripDto = TripDto(
            id = id,
            userId = m.string("userId"),
            startTime = m.long("startTime"),
            endTime = m.longOrNull("endTime"),
            startLat = m.double("startLat"),
            startLng = m.double("startLng"),
            endLat = m.doubleOrNull("endLat"),
            endLng = m.doubleOrNull("endLng"),
            startPlaceId = m.stringOrNull("startPlaceId"),
            endPlaceId = m.stringOrNull("endPlaceId"),
            carId = m.stringOrNull("carId"),
            distanceMeters = m.float("distanceMeters"),
            durationMs = m.long("durationMs"),
            routeHash = m.string("routeHash"),
            stopTrigger = m.string("stopTrigger"),
            roadsProcessed = m.bool("roadsProcessed"),
            notes = m.string("notes"),
        )
    }
}
