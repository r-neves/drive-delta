package app.drivedelta.data.repository

import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.data.local.dao.PlaceDao
import app.drivedelta.data.local.entity.PlaceEntity
import app.drivedelta.data.remote.firestore.FirestoreDataSource
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [PlaceRepository]. Reads/writes are scoped to [AuthRepository.currentUserId]; signed
 * out, reads emit empty and writes are ignored. Writes stamp `syncedAt = null` for the periodic push.
 * Delete is a hard delete from Room (source of truth) plus a best-effort Firestore doc removal —
 * offline, the local delete still succeeds; the remote tombstone-free cleanup is deferred (see
 * PROGRESS.md).
 */
class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val firestore: FirestoreDataSource,
    private val authRepository: AuthRepository,
) : PlaceRepository {

    override fun observePlaces(): Flow<List<Place>> {
        val userId = authRepository.currentUserId ?: return flowOf(emptyList())
        return placeDao.getByUser(userId).map { rows -> rows.map(PlaceEntity::toDomain) }
    }

    override suspend fun getPlace(id: String): Place? {
        val userId = authRepository.currentUserId ?: return null
        return placeDao.getById(id)?.takeIf { it.userId == userId }?.toDomain()
    }

    override suspend fun savePlace(place: Place) {
        val userId = authRepository.currentUserId ?: return
        placeDao.insertOrReplace(place.toEntity(userId))
    }

    override suspend fun deletePlace(place: Place) {
        val userId = authRepository.currentUserId ?: return
        placeDao.delete(place.toEntity(userId))
        // Best effort: propagate to Firestore. Offline this fails silently and the doc lingers until
        // the deferred tombstone/cleanup work lands; the local delete (source of truth) already stuck.
        runCatching { firestore.deletePlace(userId, place.id) }
    }
}

/** Room row → domain. */
private fun PlaceEntity.toDomain(): Place = Place(
    id = id,
    userId = userId,
    name = name,
    address = address,
    lat = lat,
    lng = lng,
    radiusMeters = radiusMeters,
    iconEmoji = iconEmoji,
    createdAt = createdAt,
)

/** Domain → Room row. [userId] is forced to the current session; [syncedAt] reset for re-push. */
private fun Place.toEntity(currentUserId: String): PlaceEntity = PlaceEntity(
    id = id,
    userId = currentUserId,
    name = name,
    address = address,
    lat = lat,
    lng = lng,
    radiusMeters = radiusMeters,
    iconEmoji = iconEmoji,
    createdAt = createdAt,
    syncedAt = null,
)
