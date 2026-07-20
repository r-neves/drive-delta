package app.drivedelta.domain.repository

import app.drivedelta.domain.model.Car
import kotlinx.coroutines.flow.Flow

/**
 * Cars data surface. Room is the source of truth; every method is scoped to the signed-in user and
 * emits an empty result when signed out. Writes stamp `syncedAt = null` so the periodic sync worker
 * pushes them to Firestore.
 */
interface CarRepository {

    /** Streams the current user's non-deleted cars, newest first. Empty when signed out. */
    fun observeCars(): Flow<List<Car>>

    /** One-shot fetch of a single car by id, or null if missing / not owned by the current user. */
    suspend fun getCar(id: String): Car?

    /**
     * Upserts [car]. The user id is taken from the current session (any value on [car] is ignored).
     * If [Car.isDefault] is true, clears the default flag on all of the user's other cars.
     */
    suspend fun saveCar(car: Car)

    /** Soft-deletes the car (flags `isDeleted`, marks pending sync); the row leaves [observeCars]. */
    suspend fun deleteCar(carId: String)
}
