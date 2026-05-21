package net.number42.dutchtrains.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.number42.dutchtrains.data.repository.StationRepository
import net.number42.dutchtrains.data.repository.StationRepositoryImpl
import net.number42.dutchtrains.data.repository.TripRepository
import net.number42.dutchtrains.data.repository.TripRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository

    @Binds @Singleton
    abstract fun bindStationRepository(impl: StationRepositoryImpl): StationRepository
}
