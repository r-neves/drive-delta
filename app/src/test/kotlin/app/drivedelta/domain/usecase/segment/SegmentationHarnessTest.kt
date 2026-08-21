package app.drivedelta.domain.usecase.segment

import app.drivedelta.data.remote.roads.SnappedTimedPoint
import app.drivedelta.domain.model.RoutePoint
import app.drivedelta.domain.model.Segment
import app.drivedelta.domain.repository.TripRepository
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real segmentation over a real drive, offline.
 *
 * The fixture (`fixtures/gestosa-home-173km.json`, captured by `SegmentationRecorder`) holds a
 * 173 km / 89.8 min drive: 5,159 raw fixes, 4,864 snapped points, and **the road name the geocoder
 * returned for each of the 784 placeIds**.
 *
 * Pinning those names is the entire point. Segment building is deterministic, but the reverse
 * geocoder is not — the same drive resolved to 116 distinct road names on one run and 138 on
 * another, and that alone moved the result from 175 segments to 853. Iterating against a live
 * geocoder therefore measures the geocoder rather than the algorithm, at two minutes and ~800
 * lookups per attempt. Here it is milliseconds and identical every time.
 *
 * [reports the shape of a real drive] is the working tool: it prints the distribution so a change to
 * the grouping can be judged against a real journey. The assertions around it are deliberately loose
 * invariants — things that must hold for *any* sane segmentation — rather than golden numbers that
 * would have to be rewritten on every tuning change.
 */
class SegmentationHarnessTest {

    private class Fixture(
        val raw: List<RoutePoint>,
        val snapped: List<SnappedTimedPoint>,
        val names: Map<String, String?>,
    )

    // org.json is only a stub on the unit-test classpath (every method throws), so the fixture is
    // parsed with kotlinx.serialization, which the app already depends on.
    private fun loadFixture(name: String): Fixture {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "missing fixture fixtures/$name"
        }.bufferedReader().readText()
        val json = Json.parseToJsonElement(text).jsonObject
        val tripId = json.getValue("tripId").jsonPrimitive.content

        val raw = json.getValue("raw").jsonArray.map { element ->
            val p = element.jsonArray
            RoutePoint(
                tripId = tripId,
                timestamp = p[2].jsonPrimitive.long,
                lat = p[0].jsonPrimitive.double,
                lng = p[1].jsonPrimitive.double,
                accuracyMeters = 5f,
                speedMps = p[3].jsonPrimitive.float,
                altitudeMeters = 0.0,
                isInterpolated = false,
            )
        }

        val snapped = json.getValue("snapped").jsonArray.map { element ->
            val p = element.jsonArray
            val timestamp = p[3].jsonPrimitive.long
            val speed = p[4].jsonPrimitive.float
            SnappedTimedPoint(
                lat = p[0].jsonPrimitive.double,
                lng = p[1].jsonPrimitive.double,
                placeId = p[2].jsonPrimitive.content,
                // -1 is the recorder's marker for "the Roads API interpolated this point".
                timestamp = timestamp.takeIf { it >= 0 },
                speedMps = speed.takeIf { it >= 0f },
            )
        }

        val names = json.getValue("names").jsonObject.mapValues { (_, value) ->
            value.jsonPrimitive.let { if (it is JsonNull) null else it.contentOrNull }
        }
        return Fixture(raw, snapped, names)
    }

    /** Drives the real use case with the fixture's recorded inputs. */
    private suspend fun segment(fixture: Fixture): List<Segment> {
        val repository = mockk<TripRepository>(relaxed = true)
        coEvery { repository.getRoutePoints(any()) } returns fixture.raw
        val captured: CapturingSlot<List<Segment>> = slot()
        coEvery { repository.finishTripSegments(any(), capture(captured), any(), any()) } returns Unit

        val resolver = object : RoadNameResolver {
            override suspend fun roadNameAt(lat: Double, lng: Double): String? =
                error("the harness resolves by placeId; roadNameAt should not be reached")
        }
        val recording = RecordedResolver(fixture, resolver)

        BuildSegmentsUseCase(repository, recording)("trip", fixture.snapped)
        return captured.captured
    }

    /**
     * Replays the recorded name for whichever placeId covers a coordinate. The use case asks by
     * midpoint coordinate, so this maps the coordinate back to its snapped point to find the id.
     */
    private class RecordedResolver(
        private val fixture: Fixture,
        private val delegate: RoadNameResolver,
    ) : RoadNameResolver {
        private val byCoordinate: Map<Pair<Double, Double>, String> =
            fixture.snapped.associate { (it.lat to it.lng) to it.placeId }

        override suspend fun roadNameAt(lat: Double, lng: Double): String? {
            val placeId = byCoordinate[lat to lng]
                ?: fixture.snapped.minByOrNull { abs(it.lat - lat) + abs(it.lng - lng) }?.placeId
                ?: return null
            return fixture.names[placeId]
        }
    }

    @Test
    fun `reports the shape of a real drive`() = runTest {
        val fixture = loadFixture(FIXTURE)
        val segments = segment(fixture)

        val tripMinutes = (fixture.raw.last().timestamp - fixture.raw.first().timestamp) / 60000.0
        val distances = segments.map { it.distanceMeters }
        val speeds = segments.map { it.avgSpeedMps * 3.6f }

        println("=== ${segments.size} segments over a ${"%.1f".format(tripMinutes)} min drive ===")
        println("  distance  sum ${"%.1f".format(distances.sum() / 1000)} km" +
            "   mean ${"%.2f".format(distances.average() / 1000)} km" +
            "   under 250 m: ${distances.count { it < 250f }}")
        println("  duration  sum ${"%.1f".format(segments.sumOf { it.durationMs } / 60000.0)} min")
        println("  avg speed min ${speeds.min().roundToInt()}   max ${speeds.max().roundToInt()} km/h" +
            "   implausible (>170 or <2): ${speeds.count { it > 170f || it < 2f }}")
        println("  distinct road names: ${segments.map { it.roadName }.distinct().size}")
        println("  longest:")
        segments.sortedByDescending { it.distanceMeters }.take(6).forEach {
            println("    ${it.roadName.take(34).padEnd(34)} ${"%6.1f".format(it.distanceMeters / 1000)} km" +
                " ${"%5.1f".format(it.durationMs / 60000.0)} min  ${(it.avgSpeedMps * 3.6f).roundToInt()} km/h")
        }

        // Loose invariants only: golden numbers here would need rewriting on every tuning change,
        // and the print-out above is what a change is actually judged against.
        assertTrue("segments should be produced", segments.isNotEmpty())
        assertTrue("indices are contiguous", segments.mapIndexed { i, s -> s.segmentIndex == i }.all { it })
        assertTrue("no negative durations", segments.all { it.durationMs >= 0 })
        assertTrue("no segment outlasts the drive", segments.all { it.durationMs <= tripMinutes * 60000 })
    }

    @Test
    fun `roadKey is stable across two runs over the same drive`() = runTest {
        // The property that makes splits comparable at all: the same drive must key identically.
        val fixture = loadFixture(FIXTURE)

        val first = segment(fixture).map { it.roadKey }
        val second = segment(fixture).map { it.roadKey }

        assertEquals(first, second)
        assertTrue("keys must not embed coordinates", first.none { it.contains("38.") || it.contains("-9.") })
    }

    private companion object {
        const val FIXTURE = "gestosa-home-173km.json"
    }
}
