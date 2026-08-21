package app.drivedelta.domain.usecase.segment

import app.drivedelta.data.remote.roads.SnappedTimedPoint
import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.model.Segment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.CapturingSlot
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

    /**
     * Six raw fixes 2 s apart. The snapped trace covers the same ground as three placeId runs, but
     * the first two are the same road, so they must come back as ONE segment.
     */
    private fun givenSixFixes() {
        coEvery { tripRepository.getRoutePoints("trip-1") } returns listOf(
            raw("trip-1", 38.7000, -9.1000, 0L, 10f),
            raw("trip-1", 38.7010, -9.1000, 2_000L, 20f),
            raw("trip-1", 38.7020, -9.1000, 4_000L, 20f),
            raw("trip-1", 38.7030, -9.1000, 6_000L, 30f),
            raw("trip-1", 38.7040, -9.1000, 8_000L, 30f),
            raw("trip-1", 38.7050, -9.1000, 10_000L, 30f),
        )
    }

    private fun sixSnappedPoints() = listOf(
        snapped("road-1", 38.7000, -9.1000, 0L, 10f),
        snapped("road-1", 38.7010, -9.1000, 2_000L, 20f),
        snapped("road-2", 38.7020, -9.1000, 4_000L, 20f),
        snapped("road-2", 38.7030, -9.1000, 6_000L, 30f),
        snapped("road-3", 38.7040, -9.1000, 8_000L, 30f),
        snapped("road-3", 38.7050, -9.1000, 10_000L, 30f),
    )

    private fun captureSegments(): CapturingSlot<List<Segment>> = slot<List<Segment>>().also { s ->
        coEvery { tripRepository.finishTripSegments(any(), capture(s), any(), any()) } returns Unit
    }

    @Test
    fun `adjacent placeId runs on the same road become one segment`() = runTest {
        // Google splits a single road into many placeId features; on a real 173 km drive that gave
        // 853 segments averaging six seconds each. A segment is a stretch of road, not a feature id.
        givenSixFixes()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", "A1", "IC3")
        val segments = captureSegments()

        useCase("trip-1", sixSnappedPoints())

        assertEquals(2, segments.captured.size)
        assertEquals("A1", segments.captured[0].roadName)
        assertEquals("IC3", segments.captured[1].roadName)
        assertEquals(0, segments.captured[0].segmentIndex)
        assertEquals(1, segments.captured[1].segmentIndex)
    }

    @Test
    fun `durations are measured from the raw trace, not distributed by distance`() = runTest {
        // The old code split the trip's total time in proportion to each segment's distance, so two
        // equal-length stretches always reported equal times however differently they were driven.
        // Segments tile the drive: A1 runs until IC3 starts, so the two sum to the trip's 10 s.
        givenSixFixes()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", "A1", "IC3")
        val segments = captureSegments()

        useCase("trip-1", sixSnappedPoints())

        assertEquals(8_000L, segments.captured[0].durationMs)
        assertEquals(2_000L, segments.captured[1].durationMs)
        // No time is lost between segments.
        assertEquals(10_000L, segments.captured.sumOf { it.durationMs })
    }

    @Test
    fun `an unnamed stretch inherits the road around it instead of cutting it in two`() = runTest {
        // The geocoder returns no thoroughfare for many motorway midpoints. Treating that as its own
        // road (or as the locality, which is what it used to fall back to) fragmented long roads.
        givenSixFixes()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", null, "A1")
        val segments = captureSegments()

        useCase("trip-1", sixSnappedPoints())

        assertEquals(1, segments.captured.size)
        assertEquals("A1", segments.captured[0].roadName)
        assertEquals(10_000L, segments.captured[0].durationMs)
    }

    @Test
    fun `roadKey is built from stable feature ids so a stretch matches itself across drives`() = runTest {
        // The old key embedded coordinates at ~11 m precision, so GPS noise alone gave the same
        // stretch a different key on every drive - which is why nearly every split showed as a PB.
        givenSixFixes()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", "A1", "IC3")
        val segments = captureSegments()

        useCase("trip-1", sixSnappedPoints())

        assertEquals("A1|road-1|road-2", segments.captured[0].roadKey)
        assertEquals("IC3|road-3|road-3", segments.captured[1].roadKey)
        // No coordinates anywhere in the key.
        assertTrue(segments.captured.none { it.roadKey.contains("38.7") })
    }

    @Test
    fun `routeHash is a sha256 of the ordered roadKeys`() = runTest {
        givenSixFixes()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", "A1", "IC3")
        val hash = slot<String>()
        coEvery { tripRepository.finishTripSegments(any(), any(), capture(hash), any()) } returns Unit

        useCase("trip-1", sixSnappedPoints())

        assertEquals(64, hash.captured.length)
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
