package com.komgareader.data.di

import android.content.Context
import androidx.room.Room
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "komga-reader.db").build()

    @Provides @Singleton
    fun settingsRepository(db: AppDatabase): SettingsRepository = RoomSettingsRepository(db.settingsDao())

    @Provides @Singleton
    fun serverRepository(db: AppDatabase): ServerRepository = RoomServerRepository(db.serverDao())
}
