package app.drivedelta.data.remote.roads

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `fewer than two points snaps to nothing`() = runTest {
        stubEcho()
        assertTrue(dataSource.snapToRoads(points(1)).isEmpty())
    }
}
