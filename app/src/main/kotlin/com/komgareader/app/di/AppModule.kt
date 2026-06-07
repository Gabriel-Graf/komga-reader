package com.komgareader.app.di

import android.content.Context
import android.os.Build
import coil.ImageLoader
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.app.eink.NoOpEinkController
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.usecase.NovelProgressMapper
import com.komgareader.eink.onyx.OnyxEinkController
import com.komgareader.eink.onyx.OnyxRefresher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun imageLoader(@ApplicationContext ctx: Context): ImageLoader = ImageLoader(ctx)

    @Provides
    @Singleton
    fun novelProgressMapper(): NovelProgressMapper = NovelProgressMapper()

    @Provides
    @Singleton
    fun einkController(
        bus: HardwareButtonBus,
        refresher: OnyxRefresher,
        @ApplicationContext ctx: Context,
    ): EinkController {
        return if (Build.MANUFACTURER.equals("ONYX", ignoreCase = true)) {
            val controller = OnyxEinkController(
                buttonEvents_ = bus.events,
                appPackageName = ctx.packageName,
            )
            // OnyxRefresher erhält die konkrete Controller-Instanz
            refresher.controller = controller
            controller
        } else {
            NoOpEinkController(bus)
        }
    }
}
