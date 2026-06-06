package com.komgareader.data.di

import android.content.Context
import androidx.room.Room
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.data.security.CredentialStore
import com.komgareader.data.security.EncryptedCredentialStore
import com.komgareader.domain.repository.DownloadRepository
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
        Room.databaseBuilder(ctx, AppDatabase::class.java, "komga-reader.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun settingsRepository(db: AppDatabase): SettingsRepository = RoomSettingsRepository(db.settingsDao())

    @Provides @Singleton
    fun credentialStore(@ApplicationContext ctx: Context): CredentialStore = EncryptedCredentialStore(ctx)

    @Provides @Singleton
    fun serverRepository(db: AppDatabase, credentials: CredentialStore): ServerRepository =
        RoomServerRepository(db.serverDao(), credentials)

    @Provides @Singleton
    fun downloadRepository(db: AppDatabase): DownloadRepository =
        RoomDownloadRepository(db.downloadDao())
}
