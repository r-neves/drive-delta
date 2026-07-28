package app.drivedelta.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.drivedelta.data.local.dao.CarDao
import app.drivedelta.data.local.dao.EnergyPricesDao
import app.drivedelta.data.local.dao.FuelLogDao
import app.drivedelta.data.local.dao.PlaceDao
import app.drivedelta.data.local.dao.RoutePointDao
import app.drivedelta.data.local.dao.SegmentDao
import app.drivedelta.data.local.dao.TripDao
import app.drivedelta.data.local.entity.CarEntity
import app.drivedelta.data.local.entity.EnergyPricesEntity
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
        EnergyPricesEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun segmentDao(): SegmentDao
    abstract fun placeDao(): PlaceDao
    abstract fun carDao(): CarDao
    abstract fun fuelLogDao(): FuelLogDao
    abstract fun energyPricesDao(): EnergyPricesDao

    companion object {
        /** v1 → v2: adds the per-user `energy_prices` settings table (Energy Logging feature). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `energy_prices` (" +
                        "`userId` TEXT NOT NULL, " +
                        "`currencyCode` TEXT NOT NULL, " +
                        "`petrolPricePerL` REAL NOT NULL, " +
                        "`dieselPricePerL` REAL NOT NULL, " +
                        "`lpgPricePerL` REAL NOT NULL, " +
                        "`electricityHomePerKwh` REAL NOT NULL, " +
                        "`electricityPublicPerKwh` REAL NOT NULL, " +
                        "`defaultElectricTariff` TEXT NOT NULL, " +
                        "`estimateWhenNotLogged` INTEGER NOT NULL, " +
                        "`askAfterEveryDrive` INTEGER NOT NULL, " +
                        "`syncedAt` INTEGER, " +
                        "PRIMARY KEY(`userId`))",
                )
            }
        }
    }
}
