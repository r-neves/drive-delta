package app.drivedelta.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background worker that pushes pending Room rows to Firestore. Scheduled from
 * [app.drivedelta.DriveDeltaApplication] as a 15-minute PeriodicWorkRequest requiring network.
 * A push failure returns retry() so WorkManager backs off and tries again.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        syncManager.pushPending().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )

    companion object {
        const val UNIQUE_NAME = "drivedelta_periodic_sync"
    }
}
