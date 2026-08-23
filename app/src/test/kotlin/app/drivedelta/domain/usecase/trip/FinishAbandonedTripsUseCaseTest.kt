package app.drivedelta.domain.usecase.trip

import app.drivedelta.core.postride.PostRideTrigger
import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FinishAbandonedTripsUseCaseTest {

    private val tripRepository = mockk<TripRepository>(relaxed = true)
    private val postRideTrigger = mockk<PostRideTrigger>(relaxed = true)
    private val useCase = FinishAbandonedTripsUseCase(tripRepository, postRideTrigger)

    private val now = 1_000_000_000L

    private fun trip(startTime: Long) = Trip(
        id = "trip-1",
        userId = "user",
        startTime = startTime,
        endTime = null,
        startLat = 38.7,
        startLng = -9.1,
        endLat = null,
        endLng = null,
        startPlaceId = null,
        endPlaceId = null,
        carId = null,
        distanceMeters = 0f,
        durationMs = 0L,
        routeHash = "",
        stopTrigger = "",
        roadsProcessed = false,
        notes = "",
    )

    /** Fixes 5 s apart, [stepDegrees] of latitude between them (0.0010 ≈ 111 m), ending [endingAt]. */
    private fun trace(count: Int, endingAt: Long, stepDegrees: Double = 0.0010) = (0 until count).map { i ->
        RoutePoint(
            tripId = "trip-1",
            timestamp = endingAt - (count - 1 - i) * 5_000L,
            lat = 38.7000 + i * stepDegrees,
            lng = -9.1000,
            accuracyMeters = 5f,
            speedMps = 20f,
            altitudeMeters = 0.0,
            isInterpolated = false,
        )
    }

    @Test
    fun `a ride still being recorded is left alone`() = runTest {
        // The whole risk of a sweep like this is closing a live ride underneath the service.
        coEvery { tripRepository.getUnfinishedTrips() } returns listOf(trip(now - 600_000L))
        coEvery { tripRepository.getRoutePoints("trip-1") } returns trace(20, endingAt = now - 4_000L)

        useCase(now)

        coVerify(exactly = 0) { tripRepository.finishTrip(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { tripRepository.deleteTrip(any()) }
    }

    @Test
    fun `a stranded ride is closed at its last fix and sent for processing`() = runTest {
        // The recording already happened: it belongs in the history like any other drive.
        val lastFix = now - 3 * 60 * 60 * 1000L
        coEvery { tripRepository.getUnfinishedTrips() } returns listOf(trip(lastFix - 100_000L))
        coEvery { tripRepository.getRoutePoints("trip-1") } returns trace(20, endingAt = lastFix)

        useCase(now)

        coVerify {
            tripRepository.finishTrip(
                tripId = "trip-1",
                endTime = lastFix,
                endLat = any(),
                endLng = any(),
                distanceMeters = any(),
                durationMs = 100_000L,
                stopTrigger = "ABANDONED",
            )
        }
        verify { postRideTrigger.requestProcessing("trip-1", any()) }
    }

    @Test
    fun `a ride that recorded nothing is deleted rather than kept`() = runTest {
        // Start Ride tapped by accident: a 0.0 km entry in the history is worse than no entry.
        coEvery { tripRepository.getUnfinishedTrips() } returns listOf(trip(now - 86_400_000L))
        coEvery { tripRepository.getRoutePoints("trip-1") } returns emptyList()

        useCase(now)

        coVerify { tripRepository.deleteTrip("trip-1") }
        coVerify(exactly = 0) { tripRepository.finishTrip(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a ride shorter than the floor is deleted too`() = runTest {
        // Two fixes ~22 m apart is a GPS twitch in a car park, not a drive.
        val lastFix = now - 86_400_000L
        coEvery { tripRepository.getUnfinishedTrips() } returns listOf(trip(lastFix - 5_000L))
        coEvery { tripRepository.getRoutePoints("trip-1") } returns
            trace(2, endingAt = lastFix, stepDegrees = 0.0002) // ~22 m

        useCase(now)

        coVerify { tripRepository.deleteTrip("trip-1") }
    }
}
