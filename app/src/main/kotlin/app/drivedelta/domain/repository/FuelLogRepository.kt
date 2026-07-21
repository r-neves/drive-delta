package app.drivedelta.domain.repository

import app.drivedelta.domain.model.FuelLog
import kotlinx.coroutines.flow.Flow

/** Fuel/energy logs data surface. Room is the source of truth; writes push to Firestore. */
interface FuelLogRepository {

    /** Streams the current user's fuel logs, newest first. Empty when signed out. */
    fun observeFuelLogs(): Flow<List<FuelLog>>

    /** Upserts a fuel log (user id stamped from the session), marks it pending sync, requests sync. */
    suspend fun saveFuelLog(log: FuelLog)
}
