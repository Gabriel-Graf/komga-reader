package com.komgareader.data.di

import android.content.Context
import androidx.room.Room
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.MIGRATION_1_2
import com.komgareader.data.db.MIGRATION_2_3
import com.komgareader.data.db.MIGRATION_3_4
import com.komgareader.data.db.MIGRATION_4_5
import com.komgareader.data.db.MIGRATION_5_6
import com.komgareader.data.db.MIGRATION_6_7
import com.komgareader.data.db.MIGRATION_7_8
import com.komgareader.data.db.MIGRATION_8_9
import com.komgareader.data.db.MIGRATION_9_10
import com.komgareader.data.db.SEED_CALLBACK
import com.komgareader.data.repository.RoomColorProfileRepository
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomReadProgressRepository
import com.komgareader.data.repository.RoomSeriesOverrideRepository
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.data.repository.RoomShelfRepository
import com.komgareader.data.security.CredentialStore
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ReadProgressRepository
import com.komgareader.domain.repository.SeriesOverrideRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ShelfRepository
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
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            )
            .addCallback(SEED_CALLBACK)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun settingsRepository(db: AppDatabase): SettingsRepository = RoomSettingsRepository(db.settingsDao())

    @Provides @Singleton
    fun keystoreCredentialStore(): KeystoreCredentialStore = KeystoreCredentialStore()

    /** CredentialStore-Binding für Stellen, die nur das Interface benötigen. */
    @Provides @Singleton
    fun credentialStore(store: KeystoreCredentialStore): CredentialStore = store

    @Provides @Singleton
    fun serverRepository(db: AppDatabase, credentials: KeystoreCredentialStore): ServerRepository =
        RoomServerRepository(db.serverDao(), credentials)

    @Provides @Singleton
    fun downloadRepository(db: AppDatabase): DownloadRepository =
        RoomDownloadRepository(db.downloadDao())

    @Provides @Singleton
    fun shelfRepository(db: AppDatabase): ShelfRepository =
        RoomShelfRepository(db.shelfDao())

    @Provides @Singleton
    fun seriesOverrideRepository(db: AppDatabase): SeriesOverrideRepository =
        RoomSeriesOverrideRepository(db.seriesOverrideDao())

    @Provides @Singleton
    fun readProgressRepository(db: AppDatabase): ReadProgressRepository =
        RoomReadProgressRepository(db.readProgressDao())

    @Provides @Singleton
    fun colorProfileRepository(
        db: AppDatabase,
        settings: SettingsRepository,
    ): ColorProfileRepository =
        RoomColorProfileRepository(
            dao = db.colorProfileDao(),
            activePointer = settings.activeColorProfileId,
            setActivePointer = { settings.setActiveColorProfileId(it) },
        )
}
