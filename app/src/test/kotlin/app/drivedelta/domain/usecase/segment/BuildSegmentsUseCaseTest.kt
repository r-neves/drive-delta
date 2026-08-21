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

    private fun snapped(placeId: String, lat: Double, lng: Double, ts: Long?, speed: Float?) =
        SnappedTimedPoint(lat, lng, placeId, ts, speed)

    private fun raw(tripId: String, lat: Double, lng: Double, ts: Long, speed: Float) =
        RoutePoint(tripId, ts, lat, lng, 5f, speed, 0.0, isInterpolated = false)

    /**
     * A straight 1.67 km at 60 km/h: 21 raw fixes, 5 s and ~83 m apart. Distance and speed are
     * measured from this trace, so it has to be finer than the snapped points laid over it — as a
     * real one is, since the snap sees only an RDP-thinned copy of it.
     */
    private fun givenRawTrace() {
        coEvery { tripRepository.getRoutePoints("trip-1") } returns (0..20).map { i ->
            raw("trip-1", 38.7000 + i * 0.00075, -9.1000, i * 5_000L, 17f)
        }
    }

    private fun sixSnappedPoints() = listOf(
        snapped("road-1", 38.7000, -9.1000, 0L, 17f),
        snapped("road-1", 38.7030, -9.1000, 20_000L, 17f),
        snapped("road-2", 38.7060, -9.1000, 40_000L, 17f),
        snapped("road-2", 38.7090, -9.1000, 60_000L, 17f),
        snapped("road-3", 38.7120, -9.1000, 80_000L, 17f),
        snapped("road-3", 38.7150, -9.1000, 100_000L, 17f),
    )

    private fun captureSegments(): CapturingSlot<List<Segment>> = slot<List<Segment>>().also { s ->
        coEvery { tripRepository.finishTripSegments(any(), capture(s), any(), any()) } returns Unit
    }

    @Test
    fun `adjacent placeId runs on the same road become one segment`() = runTest {
        // Google splits a single road into many placeId features; on a real 173 km drive that gave
        // 853 segments averaging six seconds each. A segment is a stretch of road, not a feature id.
        givenRawTrace()
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
        // Segments tile the drive: A1 runs until IC3 starts, so the two sum to the trip's 100 s.
        givenRawTrace()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", "A1", "IC3")
        val segments = captureSegments()

        useCase("trip-1", sixSnappedPoints())

        assertEquals(80_000L, segments.captured[0].durationMs)
        assertEquals(20_000L, segments.captured[1].durationMs)
        // No time is lost between segments.
        assertEquals(100_000L, segments.captured.sumOf { it.durationMs })
    }

    @Test
    fun `a boundary with no fix of its own is timed between the fixes either side`() = runTest {
        // Only a minority of snapped points carry a timestamp — RDP thinning before the snap and
        // interpolate=true after it leave real fixes in the minority (509 of 4,864 on the reference
        // drive), and most road changes land on a point that has none. Those are placed by distance
        // along the road between the two exact times either side: here the road changes three fifths
        // of the way along, so it changes three fifths of the way through the 100 s.
        givenRawTrace()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", "IC3")
        val segments = captureSegments()

        useCase(
            "trip-1",
            listOf(
                snapped("road-1", 38.7000, -9.1000, 0L, 17f),
                snapped("road-1", 38.7030, -9.1000, null, null),
                snapped("road-1", 38.7060, -9.1000, null, null),
                snapped("road-2", 38.7090, -9.1000, null, null),
                snapped("road-2", 38.7120, -9.1000, null, null),
                snapped("road-2", 38.7150, -9.1000, 100_000L, 17f),
            ),
        )

        assertEquals(2, segments.captured.size)
        assertEquals(60_000L, segments.captured[0].durationMs)
        assertEquals(40_000L, segments.captured[1].durationMs)
    }

    @Test
    fun `a stretch too short to be a split is folded into its longer neighbour`() = runTest {
        // A 100 m split is a slip road or a geocoder slip, not a stretch anyone drives as a unit —
        // and its time is decided by where a GPS fix landed rather than by how it was driven. Half
        // the segments on the reference drive were under 250 m. Absorbing rather than dropping keeps
        // every metre and second accounted for.
        givenRawTrace()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", "Rua do Talho", "IC3")
        val segments = captureSegments()

        useCase(
            "trip-1",
            listOf(
                snapped("road-1", 38.7000, -9.1000, 0L, 17f),
                snapped("road-1", 38.7030, -9.1000, 20_000L, 17f),
                snapped("road-1", 38.7060, -9.1000, 40_000L, 17f),
                // ~110 m of side street between two long stretches.
                snapped("road-2", 38.7090, -9.1000, 60_000L, 17f),
                snapped("road-3", 38.7100, -9.1000, 66_000L, 17f),
                snapped("road-3", 38.7150, -9.1000, 100_000L, 17f),
            ),
        )

        assertEquals(2, segments.captured.size)
        assertTrue(segments.captured.none { it.roadName == "Rua do Talho" })
        assertEquals(100_000L, segments.captured.sumOf { it.durationMs })
    }

    @Test
    fun `interpolated geometry no car could have driven is discarded`() = runTest {
        // Between two real fixes, everything else is the API's guess at the road's shape. This guess
        // leaves the road for 22 km and comes back inside 100 s. Keeping it would invent a segment
        // on a road the car never touched — which is what the chunk-overlap artefact used to do,
        // five times per drive.
        givenRawTrace()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1")
        val segments = captureSegments()

        useCase(
            "trip-1",
            listOf(
                snapped("road-1", 38.7000, -9.1000, 0L, 17f),
                snapped("phantom", 38.8000, -9.1000, null, null),
                snapped("phantom", 38.9000, -9.1000, null, null),
                snapped("phantom", 38.8000, -9.1000, null, null),
                snapped("road-1", 38.7150, -9.1000, 100_000L, 17f),
            ),
        )

        assertEquals(1, segments.captured.size)
        assertEquals("A1", segments.captured[0].roadName)
        assertEquals(100_000L, segments.captured[0].durationMs)
    }

    @Test
    fun `an unnamed stretch inherits the road around it instead of cutting it in two`() = runTest {
        // The geocoder returns no thoroughfare for many motorway midpoints. Treating that as its own
        // road (or as the locality, which is what it used to fall back to) fragmented long roads.
        givenRawTrace()
        coEvery { resolver.roadNameAt(any(), any()) } returnsMany listOf("A1", null, "A1")
        val segments = captureSegments()

        useCase("trip-1", sixSnappedPoints())

        assertEquals(1, segments.captured.size)
        assertEquals("A1", segments.captured[0].roadName)
        assertEquals(100_000L, segments.captured[0].durationMs)
    }

    @Test
    fun `roadKey is built from stable feature ids so a stretch matches itself across drives`() = runTest {
        // The old key embedded coordinates at ~11 m precision, so GPS noise alone gave the same
        // stretch a different key on every drive - which is why nearly every split showed as a PB.
        givenRawTrace()
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
        givenRawTrace()
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
