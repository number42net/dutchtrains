package net.number42.dutchtrains

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.number42.dutchtrains.di.LocationModule
import net.number42.dutchtrains.util.LocationHelper
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LocationModule::class])
object MockLocationTestModule {
    @Provides @Singleton
    fun provideLocationHelper(@ApplicationContext context: Context): LocationHelper = FakeLocationHelper(context)
}
