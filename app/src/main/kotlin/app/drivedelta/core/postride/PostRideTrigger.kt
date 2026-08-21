package app.drivedelta.core.postride

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queues [PostRideWorker] for a trip that has just finished recording.
 *
 * Deliberately **not** constrained to `NetworkType.CONNECTED`, unlike [app.drivedelta.core.sync.SyncTrigger]:
 * with no network the Roads API snap fails fast and `BuildSegmentsUseCase` falls back to raw 500 m
 * segmentation, which is a useful result the user should get immediately rather than after
 * connectivity returns.
 *
 * `ExistingWorkPolicy.KEEP` on a per-trip unique name: processing the same trip twice would only
 * redo identical work, and a duplicate STOP intent must not queue a second run.
 */
@Singleton
class PostRideTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun requestProcessing(tripId: String) {
        val request = OneTimeWorkRequestBuilder<PostRideWorker>()
            .setInputData(workDataOf(PostRideWorker.KEY_TRIP_ID to tripId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PostRideWorker.uniqueName(tripId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
