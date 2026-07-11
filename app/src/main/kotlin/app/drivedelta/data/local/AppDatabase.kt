package app.drivedelta.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.drivedelta.data.local.dao.CarDao
import app.drivedelta.data.local.dao.FuelLogDao
import app.drivedelta.data.local.dao.PlaceDao
import app.drivedelta.data.local.dao.RoutePointDao
import app.drivedelta.data.local.dao.SegmentDao
import app.drivedelta.data.local.dao.TripDao
import app.drivedelta.data.local.entity.CarEntity
import app.drivedelta.data.local.entity.FuelLogEntity
import app.drivedelta.data.local.entity.PlaceEntity
import app.drivedelta.data.local.entity.RoutePointEntity
import app.drivedelta.data.local.entity.SegmentEntity
import app.drivedelta.data.local.entity.TripEntity

/**
 * Room database and single source of truth for all local data. Firestore is a remote mirror synced
 * from these tables. Route points are local-only and are never pushed to Firestore for MVP.
 */
@Database(
    entities = [
        TripEntity::class,
        RoutePointEntity::class,
        SegmentEntity::class,
        PlaceEntity::class,
        CarEntity::class,
        FuelLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun segmentDao(): SegmentDao
    abstract fun placeDao(): PlaceDao
    abstract fun carDao(): CarDao
    abstract fun fuelLogDao(): FuelLogDao
}
