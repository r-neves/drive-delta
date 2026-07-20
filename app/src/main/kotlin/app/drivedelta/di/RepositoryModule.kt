package app.drivedelta.di

import app.drivedelta.data.repository.CarRepositoryImpl
import app.drivedelta.data.repository.PlaceRepositoryImpl
import app.drivedelta.data.repository.TripRepositoryImpl
import app.drivedelta.domain.repository.CarRepository
import app.drivedelta.domain.repository.PlaceRepository
import app.drivedelta.domain.repository.TripRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds domain repository interfaces to their Room-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCarRepository(impl: CarRepositoryImpl): CarRepository

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository
}
