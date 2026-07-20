package app.drivedelta.domain.repository

import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.model.Trip
import kotlinx.coroutines.flow.Flow

/**
 * Trips data surface. Room is the source of truth; trip rows are scoped to the signed-in user and
 * pushed to Firestore on write, while route points are local-only (never synced) for MVP. The
 * tracking service drives most of these methods while a drive is in progress.
 */
interface TripRepository {

    /** Streams the current user's trips, newest first. Empty when signed out. */
    fun observeTrips(): Flow<List<Trip>>

    /** One-shot fetch of a single trip, or null if missing / not owned by the current user. */
    suspend fun getTrip(id: String): Trip?

    /** Inserts a freshly started trip (end fields null) and requests a sync. */
    suspend fun startTrip(trip: Trip)

    /** Corrects a trip's start coordinates once the first real GPS fix arrives. */
    suspend fun updateStartLocation(tripId: String, lat: Double, lng: Double)

    /** Appends recorded GPS fixes for a trip. Route points are local-only (not synced). */
    suspend fun appendRoutePoints(points: List<RoutePoint>)

    /** All recorded route points for a trip, ordered by timestamp. */
    suspend fun getRoutePoints(tripId: String): List<RoutePoint>

    /**
     * Finalises a trip on stop: stamps end time/coordinates, totals and the stop trigger, marks it
     * pending sync and requests a push. No-op if the trip is missing.
     */
    suspend fun finishTrip(
        tripId: String,
        endTime: Long,
        endLat: Double,
        endLng: Double,
        distanceMeters: Float,
        durationMs: Long,
        stopTrigger: String,
    )
}
