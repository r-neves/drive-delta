package app.drivedelta.core.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Thin wrapper over [FusedLocationProviderClient] that exposes location updates as a cold [Flow].
 * Callers must hold `ACCESS_FINE_LOCATION` before collecting; the `@SuppressLint` is deliberate —
 * the permission gate lives at the call sites (the tracking service is only started once the
 * permission flow has granted location).
 *
 * The request matches the F4 spec: high accuracy, a 2 s desired interval, a 1 s fastest interval,
 * and a 5 m minimum displacement so a parked car does not accumulate jitter.
 */
@Singleton
class LocationProvider @Inject constructor(
    private val fusedClient: FusedLocationProviderClient,
) {

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_M)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    /**
     * Best-effort one-shot last known location, used to seed a trip's start coordinates. Returns
     * null if none is cached or the lookup fails; the service backfills the start on its first fix.
     */
    @SuppressLint("MissingPermission")
    suspend fun lastLocation(): Location? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }

    /**
     * A location good enough to answer "where am I right now?" — the cached fix while it is still
     * recent, otherwise a fresh single fix.
     *
     * [lastLocation] alone is not enough for that question, in two ways. It is empty on a device
     * that has not used location recently (and routinely on the emulator), so anything built on it
     * silently does nothing; and when it is populated it can be minutes old and hundreds of metres
     * away — measured here, a cache 15 minutes stale put the driver 800 m from the place they were
     * actually standing in, which is the difference between filling the origin in and not. Anything
     * older than [MAX_CACHE_AGE_MS] is therefore refetched.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        val cached = lastLocation()
        if (cached != null && ageMs(cached) <= MAX_CACHE_AGE_MS) return cached
        return freshLocation() ?: cached
    }

    /** Age of a fix in ms, measured on the monotonic clock so a wall-clock change can't skew it. */
    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(): Location? = suspendCancellableCoroutine { cont ->
        val cancellation = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
        cont.invokeOnCancellation { cancellation.cancel() }
    }

    private companion object {
        const val INTERVAL_MS = 2_000L
        const val FASTEST_INTERVAL_MS = 1_000L
        const val MIN_DISPLACEMENT_M = 5f

        /** Beyond this a cached fix is treated as "somewhere I used to be", not "where I am". */
        const val MAX_CACHE_AGE_MS = 60_000L
    }
}
