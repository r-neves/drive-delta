package app.drivedelta.data.remote.firestore.dto

import app.drivedelta.data.local.entity.PlaceEntity
import app.drivedelta.data.remote.firestore.double
import app.drivedelta.data.remote.firestore.float
import app.drivedelta.data.remote.firestore.long
import app.drivedelta.data.remote.firestore.string

/**
 * Firestore representation of a [PlaceEntity]. Written and read via explicit [Map] entries. The
 * local-only `syncedAt` marker is excluded from the wire format.
 */
data class PlaceDto(
    val id: String,
    val userId: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Float,
    val iconEmoji: String,
    val createdAt: Long,
) {

    /** Serializes to a Firestore-friendly primitive map. Floats widen to [Double]. */
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "name" to name,
        "address" to address,
        "lat" to lat,
        "lng" to lng,
        "radiusMeters" to radiusMeters.toDouble(),
        "iconEmoji" to iconEmoji,
        "createdAt" to createdAt,
    )

    /** Rehydrates a [PlaceEntity], stamping [syncedAt] with the value from the pull. */
    fun toEntity(syncedAt: Long): PlaceEntity = PlaceEntity(
        id = id,
        userId = userId,
        name = name,
        address = address,
        lat = lat,
        lng = lng,
        radiusMeters = radiusMeters,
        iconEmoji = iconEmoji,
        createdAt = createdAt,
        syncedAt = syncedAt,
    )

    companion object {

        fun fromEntity(e: PlaceEntity): PlaceDto = PlaceDto(
            id = e.id,
            userId = e.userId,
            name = e.name,
            address = e.address,
            lat = e.lat,
            lng = e.lng,
            radiusMeters = e.radiusMeters,
            iconEmoji = e.iconEmoji,
            createdAt = e.createdAt,
        )

        fun fromMap(id: String, m: Map<String, Any?>): PlaceDto = PlaceDto(
            id = id,
            userId = m.string("userId"),
            name = m.string("name"),
            address = m.string("address"),
            lat = m.double("lat"),
            lng = m.double("lng"),
            radiusMeters = m.float("radiusMeters"),
            iconEmoji = m.string("iconEmoji"),
            createdAt = m.long("createdAt"),
        )
    }
}
