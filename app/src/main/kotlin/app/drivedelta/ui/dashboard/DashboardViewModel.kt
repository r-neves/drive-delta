package app.drivedelta.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.data.local.AppDatabase
import app.drivedelta.domain.model.FuelLog
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.FuelLogRepository
import app.drivedelta.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Current-calendar-week totals (F13). */
data class WeeklyStats(val distanceKm: Float, val driveMinutes: Int, val fuelCost: Float)

/** A frequently-driven route keyed by routeHash, with its best total time (F13). */
data class PersonalBest(val routeHash: String, val rideCount: Int, val bestDurationMs: Long, val distanceKm: Float)

/**
 * Dashboard state (F13): recent rides (the Trip Detail entry point), the current week's totals, and
 * the most-driven routes with their best times. Sign-out clears the local cache.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val database: AppDatabase,
    tripRepository: TripRepository,
    fuelLogRepository: FuelLogRepository,
) : ViewModel() {

    private val endedTrips = tripRepository.observeTrips().map { trips -> trips.filter { it.endTime != null } }

    val recentTrips: StateFlow<List<Trip>> = endedTrips
        .map { it.take(RECENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyStats: StateFlow<WeeklyStats> =
        combine(endedTrips, fuelLogRepository.observeFuelLogs()) { trips, fuels -> weekStats(trips, fuels) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyStats(0f, 0, 0f))

    val personalBests: StateFlow<List<PersonalBest>> = endedTrips
        .map { trips -> topRoutes(trips) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun weekStats(trips: List<Trip>, fuels: List<FuelLog>): WeeklyStats {
        val weekStart = weekStartMillis()
        val weekTrips = trips.filter { it.startTime >= weekStart }
        val meters = weekTrips.map { it.distanceMeters }.sum()
        val durationMs = weekTrips.sumOf { it.durationMs }
        val fuelCost = fuels.filter { it.timestamp >= weekStart }.map { it.totalCost }.sum()
        return WeeklyStats(
            distanceKm = meters / 1000f,
            driveMinutes = (durationMs / 60_000).toInt(),
            fuelCost = fuelCost,
        )
    }

    /** Groups trips by routeHash (routes driven at least twice), newest-usage first, top 3. */
    private fun topRoutes(trips: List<Trip>): List<PersonalBest> =
        trips.filter { it.routeHash.isNotBlank() }
            .groupBy { it.routeHash }
            .map { (hash, group) ->
                PersonalBest(
                    routeHash = hash,
                    rideCount = group.size,
                    bestDurationMs = group.minOf { it.durationMs },
                    distanceKm = group.first().distanceMeters / 1000f,
                )
            }
            .filter { it.rideCount >= 2 }
            .sortedByDescending { it.rideCount }
            .take(PERSONAL_BEST_LIMIT)

    private fun weekStartMillis(): Long =
        java.time.LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    fun signOut() {
        authRepository.signOut()
        viewModelScope.launch { withContext(Dispatchers.IO) { database.clearAllTables() } }
    }

    private companion object {
        const val RECENT_LIMIT = 5
        const val PERSONAL_BEST_LIMIT = 3
    }
}
