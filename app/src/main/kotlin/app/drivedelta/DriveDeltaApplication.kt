package app.drivedelta

import android.app.Application
import android.util.Log
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hosts the Hilt component graph and initialises the Places SDK.
 * Firebase auto-initialises from google-services.json via its manifest-merged initializer, so no
 * explicit FirebaseApp.initializeApp() is required here.
 */
@HiltAndroidApp
class DriveDeltaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initPlaces()
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
