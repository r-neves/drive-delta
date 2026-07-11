package app.drivedelta

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.drivedelta.core.sync.SyncWorker
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application entry point. Hosts the Hilt component graph, initialises the Places SDK, and
 * schedules the periodic Firestore sync. Firebase auto-initialises from google-services.json via
 * its manifest-merged initializer, so no explicit FirebaseApp.initializeApp() is required here.
 *
 * Implements [Configuration.Provider] so WorkManager uses Hilt's [HiltWorkerFactory] to construct
 * [SyncWorker] with its injected dependencies. The default WorkManager initializer is removed in
 * the manifest so this on-demand configuration takes effect.
 */
@HiltAndroidApp
class DriveDeltaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initPlaces()
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun initPlaces() {
        val key = BuildConfig.PLACES_API_KEY
        if (key.isBlank()) {
            Log.w(TAG, "PLACES_API_KEY is blank — Places SDK not initialised. Add it to local.properties.")
            return
        }
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, key)
        }
    }

    private companion object {
        const val TAG = "DriveDeltaApp"
    }
}
