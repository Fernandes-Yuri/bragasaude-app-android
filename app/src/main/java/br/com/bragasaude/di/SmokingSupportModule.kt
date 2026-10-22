package br.com.bragasaude.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SmokingClock
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SmokingZone

@Module
@InstallIn(SingletonComponent::class)
object SmokingSupportModule {
    @Provides @SmokingClock fun clock(): Clock = Clock.systemUTC()
    @Provides @SmokingZone fun zone(): ZoneId = ZoneId.systemDefault()
}
