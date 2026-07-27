package app.drivedelta.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.data.local.AppDatabase
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.FuelLog
import app.drivedelta.domain.model.FuelType
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.FuelLogRepository
import app.drivedelta.domain.repository.PlaceRepository
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

/** A frequently-driven route keyed by routeHash, with its best total time (F13). [bestTripId] is the
 *  fastest ride on the route — the Route Summary entry point. */
data class PersonalBest(
    val routeHash: String,
    val rideCount: Int,
    val bestDurationMs: Long,
    val distanceKm: Float,
    val bestTripId: String,
)

/** A recent drive as the Dashboard card renders it (design/mockups/dashboard.png): resolved place/car
 *  names and a delta vs the route's best-of-other-drives (negative = a new record). */
data class DashboardRecentItem(
    val tripId: String,
    val startTime: Long,
    val originName: String?,
    val destName: String?,
    val carName: String?,
    val fuelType: FuelType?,
    val durationMs: Long,
    val distanceMeters: Float,
    val deltaVsBestMs: Long?,
)

/**
 * Dashboard state (F13): greeting, weekly totals, recent rides (rich cards, the Trip Detail entry
 * point), and the most-driven routes with their best times. Sign-out clears the local cache.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val database: AppDatabase,
    tripRepository: TripRepository,
    carRepository: CarRepository,
    placeRepository: PlaceRepository,
    fuelLogRepository: FuelLogRepository,
) : ViewModel() {

    /** The signed-in user's first name (for the greeting + avatar). Read once; auth is stable here. */
    val userName: String? = authRepository.currentUserName

    private val endedTrips = tripRepository.observeTrips().map { trips -> trips.filter { it.endTime != null } }

    val recentItems: StateFlow<List<DashboardRecentItem>> = combine(
        endedTrips,
        carRepository.observeCars(),
        placeRepository.observePlaces(),
    ) { trips, cars, places ->
        recentItems(trips, cars, places)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyStats: StateFlow<WeeklyStats> =
        combine(endedTrips, fuelLogRepository.observeFuelLogs()) { trips, fuels -> weekStats(trips, fuels) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyStats(0f, 0, 0f))

    val personalBests: StateFlow<List<PersonalBest>> = endedTrips
        .map { trips -> topRoutes(trips) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Newest 3 completed drives, each with a delta vs the best of the route's OTHER drives. */
    private fun recentItems(trips: List<Trip>, cars: List<Car>, places: List<Place>): List<DashboardRecentItem> {
        val carsById = cars.associateBy { it.id }
        val placesById = places.associateBy { it.id }
        val byRoute = trips.filter { it.routeHash.isNotBlank() }.groupBy { it.routeHash }
        return trips.sortedByDescending { it.startTime }.take(RECENT_LIMIT).map { t ->
            val others = byRoute[t.routeHash]?.filter { it.id != t.id }
            val bestOther = others?.minOfOrNull { it.durationMs }
            DashboardRecentItem(
                tripId = t.id,
                startTime = t.startTime,
                originName = t.startPlaceId?.let { placesById[it]?.name },
                destName = t.endPlaceId?.let { placesById[it]?.name },
                carName = t.carId?.let { carsById[it]?.name },
                fuelType = t.carId?.let { carsById[it]?.fuelType },
                durationMs = t.durationMs,
                distanceMeters = t.distanceMeters,
                deltaVsBestMs = bestOther?.let { t.durationMs - it },
            )
        }
    }

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
                val best = group.minBy { it.durationMs }
                PersonalBest(
                    routeHash = hash,
                    rideCount = group.size,
                    bestDurationMs = best.durationMs,
                    distanceKm = group.first().distanceMeters / 1000f,
                    bestTripId = best.id,
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
        const val RECENT_LIMIT = 3
        const val PERSONAL_BEST_LIMIT = 3
    }
}
