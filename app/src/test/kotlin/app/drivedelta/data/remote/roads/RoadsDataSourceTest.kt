package app.drivedelta.data.remote.roads

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RoadsDataSourceTest {

    private val service = mockk<RoadsApiService>()
    private val dataSource = RoadsDataSource(service)

    /** Stub the API to echo each request path back as snapped points with sequential originalIndex. */
    private fun stubEcho() {
        coEvery { service.snapToRoads(any(), any(), any()) } answers {
            val path = firstArg<String>()
            val coords = path.split("|")
            RoadsSnapResponse(
                snappedPoints = coords.mapIndexed { i, c ->
                    val (lat, lng) = c.split(",").map { it.toDouble() }
                    SnappedPointDto(RoadsLocationDto(lat, lng), originalIndex = i, placeId = "road$i")
                },
            )
        }
    }

    private fun points(n: Int) = (0 until n).map { TimedPoint(38.0 + it * 1e-4, -9.0, it.toLong(), 10f) }

    @Test
    fun `single chunk returns one point per input`() = runTest {
        stubEcho()
        val result = dataSource.snapToRoads(points(50))
        assertEquals(50, result.size)
        assertEquals(10L, result[10].timestamp) // timing carried from the source point
    }

    @Test
    fun `long route is chunked and stitched without duplicating overlap points`() = runTest {
        stubEcho()
        // 205 points → chunks [0,100), [90,190), [180,205]; overlap must not double-count.
        val result = dataSource.snapToRoads(points(205))
        assertEquals(205, result.size)
        // Timings should be strictly increasing 0..204 (proves no overlap duplication / gaps).
        val timestamps = result.mapNotNull { it.timestamp }
        assertEquals((0L until 205L).toList(), timestamps)
    }

    /**
     * As above, but with a point interpolated between each pair — which is what `interpolate=true`
     * actually returns, and what the echo stub above never produced. Interpolated points carry no
     * originalIndex, so they cannot be recognised as duplicates one at a time.
     */
    private fun stubEchoInterpolating() {
        coEvery { service.snapToRoads(any(), any(), any()) } answers {
            val coords = firstArg<String>().split("|").map { c ->
                val (lat, lng) = c.split(",").map { it.toDouble() }
                lat to lng
            }
            val snapped = mutableListOf<SnappedPointDto>()
            coords.forEachIndexed { i, (lat, lng) ->
                if (i > 0) {
                    val (previousLat, previousLng) = coords[i - 1]
                    snapped += SnappedPointDto(
                        RoadsLocationDto((lat + previousLat) / 2, (lng + previousLng) / 2),
                        originalIndex = null,
                        placeId = "road$i",
                    )
                }
                snapped += SnappedPointDto(RoadsLocationDto(lat, lng), originalIndex = i, placeId = "road$i")
            }
            RoadsSnapResponse(snapped)
        }
    }

    @Test
    fun `the overlap's interpolated points are dropped so the route never doubles back`() = runTest {
        // Each chunk re-snaps the ten points the previous one ended on. Dropping only the real
        // duplicates left their interpolated neighbours in, so the route jumped back ten points and
        // re-drove them — five backward jumps of up to 4.6 km on a 173 km drive, and 33 km of
        // geometry the car never covered, which segment building then reported as impossible speeds.
        stubEchoInterpolating()

        val result = dataSource.snapToRoads(points(205))

        // Latitude only ever increases along this route, so any step backwards is re-driven ground.
        val backwards = (1 until result.size).filter { result[it].lat < result[it - 1].lat }
        assertTrue("route doubles back at $backwards", backwards.isEmpty())
        assertEquals((0L until 205L).toList(), result.mapNotNull { it.timestamp })
    }

    @Test
    fun `fewer than two points snaps to nothing`() = runTest {
        stubEcho()
        assertTrue(dataSource.snapToRoads(points(1)).isEmpty())
    }

    @Test
    fun `transient failures are retried then succeed`() = runTest {
        var calls = 0
        coEvery { service.snapToRoads(any(), any(), any()) } answers {
            calls++
            if (calls < 3) throw IOException("transient")
            RoadsSnapResponse(listOf(SnappedPointDto(RoadsLocationDto(38.0, -9.0), 0, "r"), SnappedPointDto(RoadsLocationDto(38.1, -9.0), 1, "r")))
        }
        val result = dataSource.snapToRoads(points(2))
        assertEquals(3, calls) // failed twice, succeeded on the third attempt
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `gives up after max attempts and propagates`() = runTest {
        var calls = 0
        coEvery { service.snapToRoads(any(), any(), any()) } answers { calls++; throw IOException("down") }
        var thrown = false
        try {
            dataSource.snapToRoads(points(2))
        } catch (e: IOException) {
            thrown = true
        }
        assertTrue(thrown)
        assertEquals(3, calls) // MAX_ATTEMPTS
    }
}
