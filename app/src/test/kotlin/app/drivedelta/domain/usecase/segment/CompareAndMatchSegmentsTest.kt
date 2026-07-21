package app.drivedelta.domain.usecase.segment

import app.drivedelta.domain.model.ComparisonWinner
import app.drivedelta.domain.model.Segment
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.TripRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareAndMatchSegmentsTest {

    private val repo = mockk<TripRepository>(relaxed = true)

    private fun seg(tripId: String, index: Int, roadKey: String, durationMs: Long) = Segment(
        tripId = tripId, segmentIndex = index, roadKey = roadKey, roadName = roadKey.substringBefore('|'),
        startLat = 0.0, startLng = 0.0, endLat = 0.0, endLng = 0.0,
        distanceMeters = 100f, durationMs = durationMs, avgSpeedMps = 10f, maxSpeedMps = 12f,
    )

    private fun trip(id: String, routeHash: String, ended: Boolean = true) = Trip(
        id = id, userId = "u", startTime = 0L, endTime = if (ended) 1L else null,
        startLat = 0.0, startLng = 0.0, endLat = null, endLng = null,
        startPlaceId = null, endPlaceId = null, carId = null,
        distanceMeters = 0f, durationMs = 0L, routeHash = routeHash,
    )

    @Test
    fun `compare aligns by roadKey with deltas, winners and a missing segment`() = runTest {
        coEvery { repo.getSegments("A") } returns listOf(
            seg("A", 0, "R1|a", 1000L),
            seg("A", 1, "R2|b", 3000L),
        )
        coEvery { repo.getSegments("B") } returns listOf(
            seg("B", 0, "R1|a", 1200L),
            seg("B", 1, "R3|c", 2000L), // only in B
        )
        coEvery { repo.bestSegmentDuration(any()) } returns 900L

        val result = CompareSegmentsUseCase(repo).invoke("A", "B")

        assertEquals(listOf("R1|a", "R2|b", "R3|c"), result.map { it.roadKey })
        // R1: A 1000 vs B 1200 → A faster, delta -200
        assertEquals(-200L, result[0].deltaMs)
        assertEquals(ComparisonWinner.A, result[0].winner)
        assertEquals(900L, result[0].bestEverMs)
        // R2: only in A → B null, TIE, delta null
        assertNull(result[1].tripBDurationMs)
        assertNull(result[1].deltaMs)
        assertEquals(ComparisonWinner.TIE, result[1].winner)
        // R3: only in B → A null
        assertNull(result[2].tripADurationMs)
        assertEquals(2000L, result[2].tripBDurationMs)
    }

    @Test
    fun `match returns same-routeHash trips and high-overlap trips, excludes low overlap`() = runTest {
        coEvery { repo.getTrip("target") } returns trip("target", "HASH1")
        coEvery { repo.getSegments("target") } returns listOf(
            seg("target", 0, "K1", 0), seg("target", 1, "K2", 0), seg("target", 2, "K3", 0),
        )
        every { repo.observeTrips() } returns flowOf(
            listOf(
                trip("sameHash", "HASH1"),   // exact route match
                trip("overlap", "OTHER"),    // 3/3 keys shared → matches
                trip("different", "OTHER2"), // 0/3 shared → excluded
                trip("inProgress", "HASH1", ended = false), // no endTime → excluded
            ),
        )
        coEvery { repo.getSegments("sameHash") } returns listOf(seg("sameHash", 0, "X", 0))
        coEvery { repo.getSegments("overlap") } returns listOf(
            seg("overlap", 0, "K1", 0), seg("overlap", 1, "K2", 0), seg("overlap", 2, "K3", 0),
        )
        coEvery { repo.getSegments("different") } returns listOf(seg("different", 0, "Z", 0))

        val matches = MatchSegmentsUseCase(repo).invoke("target").map { it.id }

        assertTrue("sameHash" in matches)
        assertTrue("overlap" in matches)
        assertTrue("different" !in matches)
        assertTrue("inProgress" !in matches)
    }
}
