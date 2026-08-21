package app.drivedelta.core.postride

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.drivedelta.domain.usecase.segment.BuildSegmentsUseCase
import app.drivedelta.domain.usecase.segment.SnapRouteToRoadsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Post-ride processing for one finished trip (F7): snap the raw trace to roads, then build named
 * segments from the snapped path.
 *
 * This used to run inline in `TrackingForegroundService.stopTracking`, *before* the service
 * published `isTracking = false` — the one state change the tracking screen observes. Because
 * `SnapRouteToRoadsUseCase` issues a sequential HTTP request per 100-point chunk, each retried up to
 * three times with exponential backoff, finishing a long drive left the UI frozen for tens of
 * seconds with the arrival countdown still ticking, which read as "the Finish Ride button does
 * nothing". Moving it here takes the network off the stop path entirely.
 *
 * As a worker it also survives the service being torn down, and gets WorkManager's retry/backoff for
 * free when the Roads API is rate-limited or the device is offline. Snapping and segment building are
 * both idempotent — a re-run overwrites the trip's segments rather than appending — so retrying is safe.
 */
@HiltWorker
class PostRideWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val snapRouteToRoads: SnapRouteToRoadsUseCase,
    private val buildSegments: BuildSegmentsUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        return runCatching {
            // A null snap means the Roads API was unreachable; buildSegments then falls back to
            // fixed 500 m raw chunks, so the trip still gets segments either way.
            val snapped = snapRouteToRoads(tripId)
            buildSegments(tripId, snapped)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val KEY_TRIP_ID = "trip_id"

        /** Unique work name for [tripId], so two stops of the same trip can't queue twice. */
        fun uniqueName(tripId: String): String = "drivedelta_post_ride_$tripId"
    }
}
