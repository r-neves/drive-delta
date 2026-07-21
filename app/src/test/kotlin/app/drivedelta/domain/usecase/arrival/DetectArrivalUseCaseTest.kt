package app.drivedelta.domain.usecase.arrival

import android.location.Location
import app.drivedelta.domain.model.ArrivalStatus
import app.drivedelta.domain.model.Place
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies the geofence-arrival debounce (F6-B). [Location.distanceBetween] is a static Android call,
 * so it's mocked to feed a controllable distance; the test then drives the tick machine directly —
 * which the emulator can't do reliably because it delivers real GPS fixes too sparsely to land 5 in
 * a row inside the radius.
 */
class DetectArrivalUseCaseTest {

    private lateinit var useCase: DetectArrivalUseCase
    private val location = mockk<Location>(relaxed = true)
    private val destination = Place(
        id = "d",
        userId = "u",
        name = "Home",
        address = "",
        lat = 0.0,
        lng = 0.0,
        radiusMeters = 100f,
        iconEmoji = "🏠",
        createdAt = 0L,
    )

    /** Distance the mocked [Location.distanceBetween] reports on the next call. */
    private var reportedDistance = 0f

    @Before
    fun setUp() {
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            arg<FloatArray>(4)[0] = reportedDistance
        }
        useCase = DetectArrivalUseCase()
    }

    @After
    fun tearDown() = unmockkStatic(Location::class)

    private fun tick(distance: Float): ArrivalStatus {
        reportedDistance = distance
        return useCase.onLocationUpdate(location, destination)
    }

    @Test
    fun `outside radius stays en route`() {
        assertEquals(ArrivalStatus.EN_ROUTE, tick(250f))
        assertEquals(ArrivalStatus.EN_ROUTE, tick(101f))
    }

    @Test
    fun `five consecutive inside ticks arrive, fewer only approach`() {
        assertEquals(ArrivalStatus.APPROACHING, tick(50f)) // 1
        assertEquals(ArrivalStatus.APPROACHING, tick(50f)) // 2
        assertEquals(ArrivalStatus.APPROACHING, tick(50f)) // 3
        assertEquals(ArrivalStatus.APPROACHING, tick(50f)) // 4
        assertEquals(ArrivalStatus.ARRIVED, tick(50f)) // 5
        assertEquals(ArrivalStatus.ARRIVED, tick(50f)) // stays arrived
    }

    @Test
    fun `boundary distance equal to radius counts as inside`() {
        repeat(4) { tick(100f) }
        assertEquals(ArrivalStatus.ARRIVED, tick(100f))
    }

    @Test
    fun `leaving the radius resets the streak`() {
        repeat(3) { tick(40f) }
        assertEquals(ArrivalStatus.EN_ROUTE, tick(300f)) // left → reset
        // Streak restarts: needs another 5 to arrive.
        assertEquals(ArrivalStatus.APPROACHING, tick(40f))
        repeat(3) { tick(40f) }
        assertEquals(ArrivalStatus.ARRIVED, tick(40f))
    }

    @Test
    fun `reset clears the streak`() {
        repeat(4) { tick(40f) }
        useCase.reset()
        assertEquals(ArrivalStatus.APPROACHING, tick(40f)) // back to 1, not 5
    }
}
