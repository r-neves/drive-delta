package app.drivedelta.data.remote.firestore

import app.drivedelta.data.local.entity.CarEntity
import app.drivedelta.data.local.entity.FuelLogEntity
import app.drivedelta.data.local.entity.PlaceEntity
import app.drivedelta.data.local.entity.SegmentEntity
import app.drivedelta.data.local.entity.TripEntity
import app.drivedelta.data.remote.firestore.dto.CarDto
import app.drivedelta.data.remote.firestore.dto.FuelLogDto
import app.drivedelta.data.remote.firestore.dto.PlaceDto
import app.drivedelta.data.remote.firestore.dto.SegmentDto
import app.drivedelta.data.remote.firestore.dto.TripDto
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All Firestore CRUD for the app's per-user document tree.
 *
 * Every entity is written and read through its DTO as an explicit `Map<String, Any?>` — no Firestore
 * reflection/POJO mapping and no kotlinx serialization — so field names are stable and number
 * coercion is deterministic. The local-only `syncedAt` marker is never written to or read back from
 * Firestore; pulled rows are stamped with the pull timestamp instead.
 *
 * Firestore layout: `/users/{userId}/{collection}/{docId}` where collection is one of `trips`,
 * `segments`, `places`, `cars`, `fuel_logs`.
 */
@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private fun userDoc(userId: String): DocumentReference =
        firestore.collection(USERS).document(userId)

    /** Upserts a trip under its owner's `trips` collection, keyed by [TripEntity.id]. */
    suspend fun pushTrip(trip: TripEntity) {
        userDoc(trip.userId).collection(TRIPS).document(trip.id)
            .set(TripDto.fromEntity(trip).toMap())
            .await()
    }

    /**
     * Upserts a segment under [userId]'s `segments` collection. [SegmentEntity] carries no `userId`,
     * so the owner is supplied separately; the Firestore document id is the Room [SegmentEntity.id].
     */
    suspend fun pushSegment(userId: String, segment: SegmentEntity) {
        userDoc(userId).collection(SEGMENTS).document(segment.id.toString())
            .set(SegmentDto.fromEntity(segment).toMap())
            .await()
    }

    /** Upserts a place under its owner's `places` collection, keyed by [PlaceEntity.id]. */
    suspend fun pushPlace(place: PlaceEntity) {
        userDoc(place.userId).collection(PLACES).document(place.id)
            .set(PlaceDto.fromEntity(place).toMap())
            .await()
    }

    /** Upserts a car under its owner's `cars` collection, keyed by [CarEntity.id]. */
    suspend fun pushCar(car: CarEntity) {
        userDoc(car.userId).collection(CARS).document(car.id)
            .set(CarDto.fromEntity(car).toMap())
            .await()
    }

    /** Upserts a fuel log under its owner's `fuel_logs` collection, keyed by [FuelLogEntity.id]. */
    suspend fun pushFuelLog(log: FuelLogEntity) {
        userDoc(log.userId).collection(FUEL_LOGS).document(log.id)
            .set(FuelLogDto.fromEntity(log).toMap())
            .await()
    }

    /**
     * Fetches every collection for [userId] and maps each document back to its entity. Pulled rows
     * are marked synced by stamping [syncedAt] (defaults to now) — Firestore is treated as the mirror
     * and the caller merges the snapshot into Room.
     */
    suspend fun pullAll(
        userId: String,
        syncedAt: Long = System.currentTimeMillis(),
    ): RemoteSnapshot {
        val trips = userDoc(userId).collection(TRIPS).get().await().documents.map { doc ->
            TripDto.fromMap(doc.id, doc.data ?: emptyMap()).toEntity(syncedAt)
        }
        val segments = userDoc(userId).collection(SEGMENTS).get().await().documents.map { doc ->
            SegmentDto.fromMap(doc.id, doc.data ?: emptyMap()).toEntity(syncedAt)
        }
        val places = userDoc(userId).collection(PLACES).get().await().documents.map { doc ->
            PlaceDto.fromMap(doc.id, doc.data ?: emptyMap()).toEntity(syncedAt)
        }
        val cars = userDoc(userId).collection(CARS).get().await().documents.map { doc ->
            CarDto.fromMap(doc.id, doc.data ?: emptyMap()).toEntity(syncedAt)
        }
        val fuelLogs = userDoc(userId).collection(FUEL_LOGS).get().await().documents.map { doc ->
            FuelLogDto.fromMap(doc.id, doc.data ?: emptyMap()).toEntity(syncedAt)
        }
        return RemoteSnapshot(
            trips = trips,
            segments = segments,
            places = places,
            cars = cars,
            fuelLogs = fuelLogs,
        )
    }

    private companion object {
        const val USERS = "users"
        const val TRIPS = "trips"
        const val SEGMENTS = "segments"
        const val PLACES = "places"
        const val CARS = "cars"
        const val FUEL_LOGS = "fuel_logs"
    }
}

/**
 * The full set of a user's remote data pulled from Firestore, ready to be merged into Room. Route
 * points are intentionally absent — they are Room-only and never synced.
 */
data class RemoteSnapshot(
    val trips: List<TripEntity>,
    val segments: List<SegmentEntity>,
    val places: List<PlaceEntity>,
    val cars: List<CarEntity>,
    val fuelLogs: List<FuelLogEntity>,
)
