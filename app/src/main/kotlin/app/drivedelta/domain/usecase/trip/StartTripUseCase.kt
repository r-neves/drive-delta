package app.drivedelta.domain.usecase.trip

import android.content.Context
import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.core.location.LocationProvider
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import app.drivedelta.service.TrackingForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * Starts a new trip (F5): mints a trip UUID, snapshots the current GPS as the start, persists the
 * [Trip] to Room immediately, then starts the foreground tracking service. Returns the new trip id,
 * or null when signed out. The start snapshot is best-effort — if no last-known fix is cached the
 * trip starts at (0,0) and the service backfills the start on its first accepted fix.
 */
class StartTripUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val locationProvider: LocationProvider,
) {
    suspend operator fun invoke(
        carId: String? = null,
        startPlaceId: String? = null,
        destinationPlaceId: String? = null,
    ): String? {
        val userId = authRepository.currentUserId ?: return null
        val start = locationProvider.lastLocation()
        val tripId = UUID.randomUUID().toString()

        val trip = Trip(
            id = tripId,
            userId = userId,
            startTime = System.currentTimeMillis(),
            endTime = null,
            startLat = start?.latitude ?: 0.0,
            startLng = start?.longitude ?: 0.0,
            endLat = null,
            endLng = null,
            startPlaceId = startPlaceId,
            endPlaceId = destinationPlaceId,
            carId = carId,
            distanceMeters = 0f,
            durationMs = 0L,
        )
        tripRepository.startTrip(trip)

        TrackingForegroundService.start(context, tripId, destinationPlaceId)
        return tripId
    }
}
