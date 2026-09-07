package app.drivedelta.domain.usecase.trip

import android.content.Context
import app.drivedelta.service.TrackingForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Throws away the active trip instead of saving it (F6-C) — the answer to a ride started by
 * mistake. Signals the tracking service, which owns the live recording state and so is the only
 * thing that can stop recording and delete the trip without racing its own buffer flush.
 */
class DiscardTripUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke() {
        TrackingForegroundService.discard(context)
    }
}
