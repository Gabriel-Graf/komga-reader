package com.komgareader.app.di

import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.app.eink.NoOpEinkController
import com.komgareader.domain.eink.EinkController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun einkController(bus: HardwareButtonBus): EinkController = NoOpEinkController(bus)
}
