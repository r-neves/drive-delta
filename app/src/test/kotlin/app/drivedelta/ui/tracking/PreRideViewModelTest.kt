package app.drivedelta.ui.tracking

import android.location.Location
import app.drivedelta.core.location.LocationProvider
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.usecase.car.GetCarsUseCase
import app.drivedelta.domain.usecase.place.DetectNearbyPlaceUseCase
import app.drivedelta.domain.usecase.place.GetPlacesUseCase
import app.drivedelta.domain.usecase.trip.StartTripUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Pins the two behaviours behind CP26 and CP28. This ViewModel is reached through `hiltViewModel()`
 * from inside the pre-ride sheet, which is composed by the Dashboard — so its store owner is the
 * Dashboard's back-stack entry and it outlives every open and close of the sheet. Both tests here
 * are about that lifetime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreRideViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val getCars = mockk<GetCarsUseCase>()
    private val getPlaces = mockk<GetPlacesUseCase>()
    private val detectNearby = mockk<DetectNearbyPlaceUseCase>()
    private val locationProvider = mockk<LocationProvider>()
    private val startTrip = mockk<StartTripUseCase>()

    private val home = Place(
        id = "place-home",
        userId = "user",
        name = "Home",
        address = "",
        lat = 38.88,
        lng = -9.04,
        radiusMeters = 50f,
        iconEmoji = "🏠",
        createdAt = 0L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { getCars() } returns flowOf(emptyList())
        every { getPlaces() } returns flowOf(emptyList())
        coEvery { locationProvider.currentLocation() } returns location(38.88, -9.04)
        coEvery { detectNearby(any(), any()) } returns home
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The CP26 defect: `startedTripId` is a one-shot event, but the ViewModel outlives the sheet. An
     * unconsumed id replayed on the next open, so the sheet's "did we start?" effect fired
     * immediately and dropped the driver onto a tracking screen with no ride behind it.
     */
    @Test
    fun `start event is consumed so it cannot replay on the next open`() = runTest(dispatcher) {
        coEvery { startTrip(any(), any(), any()) } returns "trip-1"
        val viewModel = newViewModel()

        viewModel.startRide(carId = "car", originPlaceId = null, destinationPlaceId = null)
        advanceUntilIdle()
        assertEquals("trip-1", viewModel.startedTripId.value)

        viewModel.onStartHandled()
        assertNull(viewModel.startedTripId.value)
    }

    /**
     * The CP28 half: detection has to re-run per open, or the sheet keeps suggesting wherever the
     * driver was the first time it was opened this session.
     */
    @Test
    fun `refreshing re-detects the nearby place`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(home, viewModel.nearbyPlace.value)

        val office = home.copy(id = "place-office", name = "Office")
        coEvery { detectNearby(any(), any()) } returns office
        viewModel.refreshNearbyPlace()
        advanceUntilIdle()
        assertEquals(office, viewModel.nearbyPlace.value)
    }

    private fun newViewModel() = PreRideViewModel(
        getCarsUseCase = getCars,
        getPlacesUseCase = getPlaces,
        detectNearbyPlaceUseCase = detectNearby,
        locationProvider = locationProvider,
        startTripUseCase = startTrip,
    )

    private fun location(lat: Double, lng: Double): Location = mockk<Location>().also {
        every { it.latitude } returns lat
        every { it.longitude } returns lng
    }
}
