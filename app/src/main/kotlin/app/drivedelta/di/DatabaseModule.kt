package app.drivedelta.di

import android.content.Context
import androidx.room.Room
import app.drivedelta.data.local.AppDatabase
import app.drivedelta.data.local.dao.CarDao
import app.drivedelta.data.local.dao.FuelLogDao
import app.drivedelta.data.local.dao.PlaceDao
import app.drivedelta.data.local.dao.RoutePointDao
import app.drivedelta.data.local.dao.SegmentDao
import app.drivedelta.data.local.dao.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "drivedelta.db").build()

    // DAO providers delegate to the singleton database; they do not need @Singleton themselves.
    @Provides
    fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()

    @Provides
    fun provideRoutePointDao(db: AppDatabase): RoutePointDao = db.routePointDao()

    @Provides
    fun provideSegmentDao(db: AppDatabase): SegmentDao = db.segmentDao()

    @Provides
    fun providePlaceDao(db: AppDatabase): PlaceDao = db.placeDao()

    @Provides
    fun provideCarDao(db: AppDatabase): CarDao = db.carDao()

    @Provides
    fun provideFuelLogDao(db: AppDatabase): FuelLogDao = db.fuelLogDao()
}
