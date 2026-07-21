package app.drivedelta.domain.usecase.segment

import app.drivedelta.data.remote.roads.SnappedTimedPoint
import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.model.Segment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildSegmentsUseCaseTest {

    private val tripRepository = mockk<app.drivedelta.domain.repository.TripRepository>(relaxed = true)
    private val resolver = mockk<RoadNameResolver>()
    private val useCase = BuildSegmentsUseCase(tripRepository, resolver)

    private fun snapped(placeId: String, lat: Double, lng: Double, ts: Long, speed: Float) =
        SnappedTimedPoint(lat, lng, placeId, ts, speed)

    private fun raw(tripId: String, lat: Double, lng: Double, ts: Long, speed: Float) =
        RoutePoint(tripId, ts, lat, lng, 5f, speed, 0.0, isInterpolated = false)

    @Test
    fun `groups consecutive placeIds into segments and hashes the roadKey sequence`() = runTest {
        coEvery { resolver.roadNameAt(any(), any()) } returns "Rua A"
        // Timing is recovered from the raw trace (same coords/timestamps as the snapped points).
        coEvery { tripRepository.getRoutePoints("trip-1") } returns listOf(
            raw("trip-1", 38.7000, -9.1000, 0L, 10f),
            raw("trip-1", 38.7010, -9.1000, 2_000L, 20f),
            raw("trip-1", 38.7020, -9.1000, 4_000L, 20f),
            raw("trip-1", 38.7030, -9.1000, 6_000L, 30f),
            raw("trip-1", 38.7040, -9.1000, 8_000L, 30f),
        )
        val segmentsSlot = slot<List<Segment>>()
        val hashSlot = slot<String>()
        coEvery {
            tripRepository.finishTripSegments(any(), capture(segmentsSlot), capture(hashSlot), any())
        } returns Unit

        val points = listOf(
            snapped("road-1", 38.7000, -9.1000, 0L, 10f),
            snapped("road-1", 38.7010, -9.1000, 2_000L, 20f),
            snapped("road-1", 38.7020, -9.1000, 4_000L, 20f),
            snapped("road-2", 38.7030, -9.1000, 6_000L, 30f),
            snapped("road-2", 38.7040, -9.1000, 8_000L, 30f),
        )

        useCase("trip-1", points)

        val segments = segmentsSlot.captured
        assertEquals(2, segments.size)
        assertEquals(0, segments[0].segmentIndex)
        assertEquals("Rua A", segments[0].roadName)
        // Time is distributed by distance share; both positive, monotone, summing to the trip's 8 s.
        assertTrue(segments.all { it.durationMs > 0 })
        // ~8 s total (per-segment toLong() flooring can lose a couple ms).
        assertTrue(segments.sumOf { it.durationMs } in 7_995L..8_000L)
        assertTrue(segments[0].durationMs > segments[1].durationMs) // road-1 spans 2 gaps vs road-2's 1
        assertTrue(segments[0].roadKey.startsWith("Rua A|"))
        // routeHash is a 64-char hex SHA-256 of the ordered roadKeys.
        assertEquals(64, hashSlot.captured.length)
        coVerify { tripRepository.finishTripSegments("trip-1", any(), any(), roadsProcessed = true) }
    }

    @Test
    fun `null snap falls back to raw 500m segments marked not-roads-processed`() = runTest {
        // ~1.3 km due north → at least two 500 m fallback chunks.
        val raw = (0..13).map { i ->
            RoutePoint(
                tripId = "trip-2",
                timestamp = i * 1_000L,
                lat = 38.7000 + i * 0.0009, // ~100 m per step
                lng = -9.1000,
                accuracyMeters = 5f,
                speedMps = 25f,
                altitudeMeters = 0.0,
                isInterpolated = false,
            )
        }
        coEvery { tripRepository.getRoutePoints("trip-2") } returns raw
        val segmentsSlot = slot<List<Segment>>()
        coEvery {
            tripRepository.finishTripSegments(any(), capture(segmentsSlot), any(), any())
        } returns Unit

        useCase("trip-2", snapped = null)

        val segments = segmentsSlot.captured
        assertTrue("expected ≥2 fallback segments, got ${segments.size}", segments.size >= 2)
        assertTrue(segments.all { it.roadName == "Unknown road" })
        assertTrue(segments.all { it.roadKey.startsWith("RAW|") })
        coVerify { tripRepository.finishTripSegments("trip-2", any(), any(), roadsProcessed = false) }
    }
}
