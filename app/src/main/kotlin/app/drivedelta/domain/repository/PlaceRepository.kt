package app.drivedelta.domain.repository

import app.drivedelta.domain.model.Place
import kotlinx.coroutines.flow.Flow

/**
 * Places data surface. Room is the source of truth; every method is scoped to the signed-in user and
 * emits an empty result when signed out. Writes stamp `syncedAt = null` so the sync worker pushes
 * them. Unlike cars, places use a hard delete (no soft-delete/undo) per F3.
 */
interface PlaceRepository {

    /** Streams the current user's places, newest first. Empty when signed out. */
    fun observePlaces(): Flow<List<Place>>

    /** One-shot fetch of a single place by id, or null if missing / not owned by the current user. */
    suspend fun getPlace(id: String): Place?

    /** Upserts [place]. The user id is taken from the current session (any value on [place] is ignored). */
    suspend fun savePlace(place: Place)

    /** Hard-deletes [place] from Room and best-effort removes its Firestore doc. */
    suspend fun deletePlace(place: Place)
}
