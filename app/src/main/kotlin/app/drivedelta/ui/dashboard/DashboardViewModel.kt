package app.drivedelta.ui.dashboard

import androidx.lifecycle.ViewModel
import app.drivedelta.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Signs out of Firebase. In later checkpoints this also clears the current user's Room rows;
     * for the skeleton it only ends the Firebase session.
     */
    fun signOut() {
        authRepository.signOut()
    }
}
