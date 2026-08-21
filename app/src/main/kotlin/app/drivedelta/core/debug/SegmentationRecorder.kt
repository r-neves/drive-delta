package app.drivedelta.core.debug

import android.content.Context
import app.drivedelta.BuildConfig
import app.drivedelta.data.remote.roads.SnappedTimedPoint
import app.drivedelta.domain.model.RoutePoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only capture of everything `BuildSegmentsUseCase` was given for one drive: the raw trace,
 * the snapped points, and **the road name the geocoder returned for each placeId**.
 *
 * That last part is the point. Segment building is otherwise deterministic, but the reverse geocoder
 * is not: re-running the same drive returned 116 distinct road names one time and 138 another, which
 * changed a 173 km drive from 175 segments to 853. Iterating the grouping algorithm against a live
 * geocoder therefore measures the geocoder, not the algorithm — and each run costs ~850 lookups and
 * two minutes on a device. Pinning the responses in a fixture makes the harness deterministic and
 * turns the loop from minutes into milliseconds.
 *
 * Writes to the app's external files dir so it can be pulled without root:
 * `adb shell run-as app.drivedelta cat files/segmentation/<tripId>.json`
 *
 * No-op in release.
 */
@Singleton
class SegmentationRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun record(
        tripId: String,
        raw: List<RoutePoint>,
        snapped: List<SnappedTimedPoint>,
        namesByPlaceId: Map<String, String?>,
    ) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val dir = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            File(dir, "$tripId.json").writeText(buildJson(tripId, raw, snapped, namesByPlaceId))
        }
    }

    /**
     * Hand-rolled rather than pulled in via a serializer: this is a debug artefact whose shape is
     * dictated by the harness that reads it, and coordinates are trimmed to 6dp (~11 cm) to keep a
     * 5,000-point drive down to a size that is comfortable to commit as a test fixture.
     */
    private fun buildJson(
        tripId: String,
        raw: List<RoutePoint>,
        snapped: List<SnappedTimedPoint>,
        namesByPlaceId: Map<String, String?>,
    ): String = buildString {
        append("{\"tripId\":\"").append(tripId).append("\",\n\"raw\":[")
        raw.forEachIndexed { i, p ->
            if (i > 0) append(',')
            append("[").append(fmt(p.lat)).append(',').append(fmt(p.lng)).append(',')
                .append(p.timestamp).append(',').append(p.speedMps).append(']')
        }
        append("],\n\"snapped\":[")
        snapped.forEachIndexed { i, p ->
            if (i > 0) append(',')
            append("[").append(fmt(p.lat)).append(',').append(fmt(p.lng)).append(',')
                .append('"').append(p.placeId).append('"').append(',')
                .append(p.timestamp ?: -1L).append(',').append(p.speedMps ?: -1f).append(']')
        }
        append("],\n\"names\":{")
        var first = true
        namesByPlaceId.forEach { (placeId, name) ->
            if (!first) append(',')
            first = false
            append('"').append(placeId).append("\":")
            if (name == null) append("null") else append('"').append(escape(name)).append('"')
        }
        append("}}")
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.6f", value)

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        const val DIRECTORY = "segmentation"
    }
}
