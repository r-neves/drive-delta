package app.drivedelta.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.core.sync.SyncManager
import app.drivedelta.data.local.dao.PlaceDao
import app.drivedelta.data.local.entity.PlaceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val placeDao: PlaceDao,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    /**
     * Signs out of Firebase. In later checkpoints this also clears the current user's Room rows;
     * for the skeleton it only ends the Firebase session.
     */
    fun signOut() {
        authRepository.signOut()
    }

    /**
     * TEMPORARY Checkpoint 2 acceptance affordance: inserts a test place into Room then forces a
     * push to Firestore, so the sync path can be verified in the Firestore console. Removed in
     * Checkpoint 4 once the real Places feature exists.
     */
    fun insertTestPlaceAndSync() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId
            if (userId == null) {
                _syncStatus.value = "Not signed in"
                return@launch
            }
            val place = PlaceEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                name = "Test Place",
                address = "Debug insert",
                lat = 38.7223,
                lng = -9.1393,
                radiusMeters = 100f,
                iconEmoji = "📍",
                createdAt = System.currentTimeMillis(),
                syncedAt = null,
            )
            placeDao.insertOrReplace(place)
            _syncStatus.value = "Inserted; syncing…"
            val result = syncManager.pushPending()
            _syncStatus.value = if (result.isSuccess) {
                "Synced ✓ — check Firestore /users/$userId/places"
            } else {
                "Sync failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}
