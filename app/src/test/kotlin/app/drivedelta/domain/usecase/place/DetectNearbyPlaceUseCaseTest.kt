package app.drivedelta.domain.usecase.place

import app.drivedelta.domain.model.Place
import app.drivedelta.domain.repository.PlaceRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Coordinates are the real places and real drive starts from the phone that reported the bug. */
class DetectNearbyPlaceUseCaseTest {

    private fun place(id: String, lat: Double, lng: Double, radius: Float) =
        Place(id, "u", id, "", lat, lng, radius, "📍", 0L)

    private val home = place("Home", 38.88787, -9.04732, 50f)
    private val auchan = place("Auchan", 38.88885, -9.04364, 100f) // ~330 m from Home
    private val parents = place("Parents", 38.77439, -9.10713, 50f)

    private val repo = mockk<PlaceRepository> {
        every { observePlaces() } returns flowOf(listOf(home, auchan, parents))
    }
    private val detect = DetectNearbyPlaceUseCase(repo)

    @Test
    fun `inside a radius wins`() = runTest {
        assertEquals("Home", detect(38.8879, -9.0475)?.id)
    }

    @Test
    fun `parked outside the circle still counts as leaving from the place`() = runTest {
        // A real return-trip start: 230 m from Parents, whose radius is 50 m.
        assertEquals("Parents", detect(38.7754, -9.1094)?.id)
    }

    @Test
    fun `between two close places the nearer one is chosen`() = runTest {
        // ~120 m from Home towards Auchan, so ~210 m from Auchan: both within slack, Home nearer.
        assertEquals("Home", detect(38.88820, -9.04600)?.id)
    }

    @Test
    fun `far from every place is no place`() = runTest {
        assertNull(detect(38.7223, -9.1393))
    }
}
