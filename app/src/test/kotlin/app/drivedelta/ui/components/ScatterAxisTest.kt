package app.drivedelta.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cost scatter's x axis, whose arithmetic has been wrong three times in three different ways:
 * a window that shrank below its own minimum, a domain that drifted off the tick grid and clipped a
 * drive against the plot edge, and a per-tick clock format that mixed `m:ss` with `h:mm:ss` on the
 * same axis. Each of those is one of the cases below.
 */
class ScatterAxisTest {

    private fun onGrid(value: Double, step: Double) =
        assertEquals("$value is not a multiple of $step", 0.0, value % step, 1e-9)

    @Test
    fun `duration domain contains the drives, on the grid, never negative`() {
        val (min, max) = scatterXDomain(listOf(9.5, 10.5), ScatterAxis.DURATION)
        assertTrue("9.5 falls outside $min..$max", min < 9.5)
        assertTrue("10.5 falls outside $min..$max", max > 10.5)
        assertTrue("window is under the 3-minute minimum", max - min >= 3.0)
        onGrid(min, 1.0)
        onGrid(max, 1.0)
    }

    /** The regression: the zero clamp used to run after the widening and undo it. */
    @Test
    fun `a single sub-minute drive still gets the minimum window`() {
        val (min, max) = scatterXDomain(listOf(0.4), ScatterAxis.DURATION)
        assertEquals(0.0, min, 1e-9)
        assertTrue("window $min..$max is under the minimum", max - min >= 3.0)
    }

    @Test
    fun `the domain never goes negative`() {
        val (min, _) = scatterXDomain(listOf(0.0), ScatterAxis.DURATION)
        assertTrue("$min is negative", min >= 0.0)
    }

    @Test
    fun `speed domain pads to ten and holds its own minimum window`() {
        val (min, max) = scatterXDomain(listOf(73.0), ScatterAxis.SPEED)
        onGrid(min, 10.0)
        onGrid(max, 10.0)
        assertTrue(min < 73.0 && max > 73.0)
        assertTrue("window is under the 20 km/h minimum", max - min >= 20.0)
    }

    @Test
    fun `an empty chart still produces a usable domain`() {
        val (min, max) = scatterXDomain(emptyList(), ScatterAxis.DURATION)
        assertTrue(max > min)
    }

    @Test
    fun `duration labels carry seconds below an hour`() {
        assertEquals("2:24", formatAxisValue(2.4, ScatterAxis.DURATION, useHours = false))
        assertEquals("0:30", formatAxisValue(0.5, ScatterAxis.DURATION, useHours = false))
    }

    /**
     * Above an hour every tick uses h:mm:ss — including the ones that would still fit in m:ss. A
     * "50:00" beside a "1:02:00" reads as fifty *hours* at a glance, which is the ambiguity the
     * format exists to remove.
     */
    @Test
    fun `duration labels use one format across the whole axis`() {
        assertEquals("0:50:00", formatAxisValue(50.0, ScatterAxis.DURATION, useHours = true))
        assertEquals("1:02:00", formatAxisValue(62.0, ScatterAxis.DURATION, useHours = true))
    }

    @Test
    fun `speed labels are whole numbers`() {
        assertEquals("73", formatAxisValue(72.6, ScatterAxis.SPEED, useHours = false))
    }
}
