package app.drivedelta.ui.dashboard

import androidx.lifecycle.ViewModel
import app.drivedelta.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Checkpoint 1 stub. The full dashboard (recent trips, personal bests, weekly stats) arrives in
 * Checkpoint 9 (F13). The "Start Ride" entry point and pre-ride sheet live in the screen; sign-out
 * stays here until a Settings surface exists.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** Signs out of Firebase. Room clearing is added with the full sign-out flow in Checkpoint 9. */
    fun signOut() {
        authRepository.signOut()
    }
}
