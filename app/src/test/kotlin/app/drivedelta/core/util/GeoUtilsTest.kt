package app.drivedelta.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun `haversine matches a known short distance`() {
        // ~0.001 deg latitude ≈ 111 m.
        val d = GeoUtils.haversineMeters(38.7223, -9.1393, 38.7233, -9.1393)
        assertTrue("expected ~111 m, was $d", d in 108.0..115.0)
    }

    @Test
    fun `bearing due north is ~0 and due east ~90`() {
        assertEquals(0.0, GeoUtils.bearingDegrees(38.72, -9.14, 38.73, -9.14), 1.0)
        assertEquals(90.0, GeoUtils.bearingDegrees(38.72, -9.14, 38.72, -9.13), 1.0)
    }

    @Test
    fun `simplify drops collinear midpoints but keeps a corner`() {
        // A near-straight line: the middle points sit on the A→C line, so RDP drops them.
        val straight = listOf(
            0.0 to 0.0,
            0.0 to 0.0010,
            0.0 to 0.0020,
            0.0 to 0.0030,
        )
        assertEquals(listOf(0, 3), GeoUtils.simplify(straight, epsilonMeters = 10.0))

        // A sharp detour at index 2 (~1 km north) must be retained.
        val corner = listOf(
            0.0 to 0.0,
            0.0 to 0.0010,
            0.0090 to 0.0020,
            0.0 to 0.0030,
            0.0 to 0.0040,
        )
        val kept = GeoUtils.simplify(corner, epsilonMeters = 10.0)
        assertTrue("corner index 2 must be kept: $kept", kept.contains(2))
        assertEquals(0, kept.first())
        assertEquals(corner.lastIndex, kept.last())
    }

    @Test
    fun `simplify keeps all when fewer than three points`() {
        val two = listOf(1.0 to 1.0, 2.0 to 2.0)
        assertEquals(listOf(0, 1), GeoUtils.simplify(two, 10.0))
    }
}
