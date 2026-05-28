package net.number42.dutchtrains.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.number42.dutchtrains.util.LocationHelper
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides @Singleton
    fun provideLocationHelper(@ApplicationContext context: Context): LocationHelper = LocationHelper(context)
}
