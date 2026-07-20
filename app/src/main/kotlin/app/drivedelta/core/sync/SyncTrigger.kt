package app.drivedelta.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires an on-demand Firestore push right after a local write, so changes propagate in seconds
 * instead of waiting up to 15 minutes for the periodic [SyncWorker]. The periodic worker stays as a
 * backstop for anything this misses (e.g. a write made while offline, synced once connectivity
 * returns).
 *
 * Implementation notes:
 * - A plain [OneTimeWorkRequestBuilder], NOT `setExpedited`: expedited work on API < 31 (our minSdk
 *   is 26) demands a foreground notification via `getForegroundInfo`, which would flash a notification
 *   on every edit. A normal one-time request runs promptly enough while the app is foreground.
 * - `ExistingWorkPolicy.REPLACE` on a unique name so the most recent write's push wins; since
 *   [SyncManager.pushPending] pushes *all* pending rows, replacing an in-flight push simply re-runs it
 *   and captures the latest state (a set() is idempotent).
 * - `NetworkType.CONNECTED` keeps it offline-first: offline the push waits until connectivity returns.
 */
@Singleton
class SyncTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val UNIQUE_NAME = "drivedelta_expedited_sync"
    }
}
