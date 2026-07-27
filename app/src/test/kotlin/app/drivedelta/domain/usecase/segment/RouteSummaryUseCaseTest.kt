package app.drivedelta.domain.usecase.segment

import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.FuelLog
import app.drivedelta.domain.model.FuelType
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.model.Segment
import app.drivedelta.domain.model.Trip
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.FuelLogRepository
import app.drivedelta.domain.repository.PlaceRepository
import app.drivedelta.domain.repository.TripRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSummaryUseCaseTest {

    private val tripRepo = mockk<TripRepository>(relaxed = true)
    private val placeRepo = mockk<PlaceRepository>(relaxed = true)
    private val carRepo = mockk<CarRepository>(relaxed = true)
    private val fuelRepo = mockk<FuelLogRepository>(relaxed = true)

    private fun useCase() = RouteSummaryUseCase(tripRepo, placeRepo, carRepo, fuelRepo)

    private fun seg(tripId: String, roadKey: String, durationMs: Long) = Segment(
        tripId = tripId, segmentIndex = 0, roadKey = roadKey, roadName = roadKey,
        startLat = 0.0, startLng = 0.0, endLat = 0.0, endLng = 0.0,
        distanceMeters = 100f, durationMs = durationMs, avgSpeedMps = 10f, maxSpeedMps = 12f,
    )

    private fun trip(
        id: String, start: Long, durationMs: Long, hash: String = "HASH1",
        startPlace: String? = null, endPlace: String? = null, carId: String? = null,
    ) = Trip(
        id = id, userId = "u", startTime = start, endTime = start + durationMs,
        startLat = 0.0, startLng = 0.0, endLat = 0.0, endLng = 0.0,
        startPlaceId = startPlace, endPlaceId = endPlace, carId = carId,
        distanceMeters = 10_000f, durationMs = durationMs, routeHash = hash,
    )

    private fun fuel(tripId: String, cost: Float) = FuelLog(
        id = "f_$tripId", userId = "u", tripId = tripId, carId = "car1", timestamp = 0L,
        liters = 30f, pricePerLiter = 1.5f, kwhCharged = null, pricePerKwh = null,
        totalCost = cost, odometerKm = null,
    )

    @Test
    fun `aggregates route, partitions segments and flags scatter points`() = runTest {
        val t1 = trip("T1", start = 100, durationMs = 130_000)
        val t2 = trip("T2", start = 200, durationMs = 90_000)  // fastest total (best other)
        val t3 = trip("T3", start = 300, durationMs = 100_000, startPlace = "home", endPlace = "office", carId = "car1")

        coEvery { tripRepo.getTrip("T3") } returns t3
        every { tripRepo.observeTrips() } returns flowOf(listOf(t1, t2, t3))
        coEvery { tripRepo.getSegments("T1") } returns listOf(
            seg("T1", "R1", 1200), seg("T1", "R2", 800), seg("T1", "R3", 750),
        )
        coEvery { tripRepo.getSegments("T2") } returns listOf(
            seg("T2", "R1", 900), seg("T2", "R2", 900),
        )
        coEvery { tripRepo.getSegments("T3") } returns listOf(
            seg("T3", "R1", 1000), // record(other)=900, prev(T2)=900 → slower
            seg("T3", "R2", 850),  // record(other)=800, prev(T2)=900 → faster
            seg("T3", "R3", 700),  // record(other)=750 → purple (new PB)
        )
        coEvery { placeRepo.getPlace("home") } returns place("home", "Home")
        coEvery { placeRepo.getPlace("office") } returns place("office", "Office")
        coEvery { carRepo.getCar("car1") } returns car("car1", "Model 3")
        every { fuelRepo.observeFuelLogs() } returns flowOf(listOf(fuel("T2", 3.0f), fuel("T3", 4.0f)))

        val s = useCase().invoke("T3")!!

        assertEquals(3, s.driveCount)
        assertEquals(3, s.segmentsTimed)
        assertEquals(1, s.fasterCount)
        assertEquals(1, s.slowerCount)
        assertEquals(1, s.purpleCount)
        assertEquals(1, s.newPersonalBests)
        assertEquals(3, s.fasterCount + s.slowerCount + s.purpleCount)

        assertEquals(100_000L, s.totalTimeMs)
        assertEquals(90_000L, s.bestTotalMs)
        assertEquals(10_000L, s.deltaVsBestMs) // this drive is 10s off the standing record

        assertEquals("Home", s.originName)
        assertEquals("Office", s.destinationName)
        assertEquals("Model 3", s.carName)
        assertEquals(4.0f, s.energyCost!!, 0.001f)

        // Scatter: only T2 and T3 have linked cost; T2 is both fastest (min time) and cheapest (min cost).
        assertEquals(2, s.scatter.size)
        val this3 = s.scatter.first { it.tripId == "T3" }
        val other2 = s.scatter.first { it.tripId == "T2" }
        assertTrue(this3.isThisDrive)
        assertTrue(other2.isFastest)
        assertTrue(other2.isCheapest)
    }

    @Test
    fun `single-drive route makes every segment a personal best with no delta`() = runTest {
        val only = trip("T1", start = 0, durationMs = 60_000, hash = "SOLO")
        coEvery { tripRepo.getTrip("T1") } returns only
        every { tripRepo.observeTrips() } returns flowOf(listOf(only))
        coEvery { tripRepo.getSegments("T1") } returns listOf(seg("T1", "R1", 500), seg("T1", "R2", 400))
        every { fuelRepo.observeFuelLogs() } returns flowOf(emptyList())

        val s = useCase().invoke("T1")!!

        assertEquals(1, s.driveCount)
        assertEquals(2, s.purpleCount)
        assertEquals(0, s.fasterCount)
        assertEquals(0, s.slowerCount)
        assertEquals(0L, s.deltaVsBestMs)
        assertEquals(60_000L, s.bestTotalMs)
        assertTrue(s.scatter.isEmpty())
        assertNull(s.energyCost)
    }

    @Test
    fun `returns null for an in-progress trip`() = runTest {
        val live = trip("T1", start = 0, durationMs = 0).copy(endTime = null)
        coEvery { tripRepo.getTrip("T1") } returns live
        assertNull(useCase().invoke("T1"))
    }

    private fun place(id: String, name: String) = Place(
        id = id, userId = "u", name = name, address = "", lat = 0.0, lng = 0.0,
        radiusMeters = 100f, iconEmoji = "📍", createdAt = 0L,
    )

    private fun car(id: String, name: String) = Car(
        id = id, userId = "u", name = name, licensePlate = "", fuelType = FuelType.PETROL,
        tankCapacityLiters = 50f, batteryCapacityKwh = null, defaultConsumption = null,
        isDefault = false, createdAt = 0L,
    )
}
