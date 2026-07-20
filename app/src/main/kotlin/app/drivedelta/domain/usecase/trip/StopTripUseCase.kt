package app.drivedelta.domain.usecase.trip

import android.content.Context
import app.drivedelta.service.TrackingForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Stops the active trip (F6). Signals the tracking service with the stop [trigger] ("MANUAL" or
 * "GEOFENCE"); the service owns the finalisation sequence (flush buffer, stamp the trip's end
 * fields, kick off post-ride processing, drop the foreground notification) since it holds the live
 * recording state.
 */
class StopTripUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(trigger: String) {
        TrackingForegroundService.stop(context, trigger)
    }
}
