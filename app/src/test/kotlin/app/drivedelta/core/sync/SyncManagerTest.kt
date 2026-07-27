package app.drivedelta.core.sync

import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.data.local.dao.CarDao
import app.drivedelta.data.local.dao.FuelLogDao
import app.drivedelta.data.local.dao.PlaceDao
import app.drivedelta.data.local.dao.SegmentDao
import app.drivedelta.data.local.dao.TripDao
import app.drivedelta.data.local.entity.CarEntity
import app.drivedelta.data.remote.firestore.FirestoreDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncManagerTest {

    private val auth = mockk<AuthRepository> { coEvery { currentUserId } returns "u" }
    private val remote = mockk<FirestoreDataSource>(relaxed = true)
    private val tripDao = mockk<TripDao>(relaxed = true)
    private val placeDao = mockk<PlaceDao>(relaxed = true)
    private val carDao = mockk<CarDao>(relaxed = true)
    private val fuelLogDao = mockk<FuelLogDao>(relaxed = true)
    private val segmentDao = mockk<SegmentDao>(relaxed = true)

    private fun manager() = SyncManager(auth, remote, tripDao, placeDao, carDao, fuelLogDao, segmentDao)

    private fun car(id: String, deleted: Boolean) = CarEntity(
        id = id, userId = "u", name = "Car", licensePlate = "", fuelType = "PETROL",
        tankCapacityLiters = 50f, batteryCapacityKwh = null, defaultConsumption = null,
        isDefault = false, isDeleted = deleted, createdAt = 0L, syncedAt = null,
    )

    @Test
    fun `pushes a car tombstone then hard-deletes the local row`() = runTest {
        coEvery { carDao.getPendingSync("u") } returns listOf(car("dead", deleted = true))

        manager().pushPending()

        coVerify(exactly = 1) { remote.pushCar(match { it.id == "dead" }) }
        coVerify(exactly = 1) { carDao.hardDelete("dead") }
        // A tombstone must not be re-inserted (that would keep it alive forever).
        coVerify(exactly = 0) { carDao.insertOrReplace(any()) }
    }

    @Test
    fun `pushes a live car then stamps it synced without deleting`() = runTest {
        coEvery { carDao.getPendingSync("u") } returns listOf(car("live", deleted = false))

        manager().pushPending()

        coVerify(exactly = 1) { remote.pushCar(match { it.id == "live" }) }
        coVerify(exactly = 1) { carDao.insertOrReplace(match { it.id == "live" && it.syncedAt != null }) }
        coVerify(exactly = 0) { carDao.hardDelete(any()) }
    }
}
