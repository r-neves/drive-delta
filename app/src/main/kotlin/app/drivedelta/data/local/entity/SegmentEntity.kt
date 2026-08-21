package app.drivedelta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stretch of road within a trip, computed post-ride after the Roads API snap. [roadKey] is the
 * stable cross-trip identifier for this stretch of road, formatted as
 * "RoadName|startLat4dp,startLng4dp|endLat4dp,endLng4dp".
 *
 * [id] is **derived**, not generated — see [idFor]. It used to be `@PrimaryKey(autoGenerate = true)
 * val id: Long = 0`, which silently duplicated every segment the app owned: `pullAll` rehydrates
 * segments with `id = 0`, and Room reads 0 on an autoGenerate key as "assign me a fresh one", so
 * `OnConflictStrategy.REPLACE` had nothing to match on and every Firestore pull re-inserted the
 * whole set. A 173 km drive had accumulated 10,236 rows for ~853 real segments, which in turn made
 * the Splits tab repeat rows and the "vs best" header read 16 h. A deterministic id makes both the
 * Room upsert and the Firestore document id idempotent.
 */
@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey val id: String,
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
) {
    companion object {
        /**
         * The canonical id for a segment: a trip's segment N is the same row no matter how many
         * times it is rebuilt locally or pulled back from Firestore. Also used as the Firestore
         * document id so pushes are idempotent across devices.
         */
        fun idFor(tripId: String, segmentIndex: Int): String = "$tripId#$segmentIndex"
    }
}
