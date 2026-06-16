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
import com.komgareader.data.db.MIGRATION_10_11
import com.komgareader.data.db.MIGRATION_11_12
import com.komgareader.data.db.MIGRATION_12_13
import com.komgareader.data.db.MIGRATION_13_14
import com.komgareader.data.db.MIGRATION_14_15
import com.komgareader.data.db.MIGRATION_15_16
import com.komgareader.data.db.MIGRATION_16_17
import com.komgareader.data.db.MIGRATION_17_18
import com.komgareader.data.db.MIGRATION_18_19
import com.komgareader.data.db.MIGRATION_19_20
import com.komgareader.data.db.SEED_CALLBACK
import com.komgareader.data.download.DownloadManager
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.data.repository.RoomCollectionRepository
import com.komgareader.data.repository.RoomColorProfileRepository
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomNovelBookmarkRepository
import com.komgareader.data.repository.RoomNovelProgressRepository
import com.komgareader.data.repository.RoomReadProgressRepository
import com.komgareader.data.repository.RoomReadingStatsRepository
import com.komgareader.data.repository.RoomSeriesOverrideRepository
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.data.repository.RoomShelfRepository
import com.komgareader.data.security.CredentialStore
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.NovelBookmarkRepository
import com.komgareader.domain.repository.NovelProgressRepository
import com.komgareader.domain.repository.ReadProgressRepository
import com.komgareader.domain.repository.ReadingStatsRepository
import com.komgareader.domain.repository.SeriesOverrideRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.repository.CollectionRepository
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
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
                MIGRATION_19_20,
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

    /** Schmales Lese-Interface für Stellen, die nur lokale Buch-Bytes brauchen (DIP). */
    @Provides @Singleton
    fun localBookBytes(manager: DownloadManager): LocalBookBytes = manager

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
    fun novelProgressRepository(db: AppDatabase): NovelProgressRepository =
        RoomNovelProgressRepository(db.novelProgressDao())

    @Provides @Singleton
    fun readingStatsRepository(db: AppDatabase): ReadingStatsRepository =
        RoomReadingStatsRepository(
            sessions = db.readingSessionDao(),
            readProgress = db.readProgressDao(),
            novelProgress = db.novelProgressDao(),
        )

    @Provides @Singleton
    fun novelBookmarkRepository(db: AppDatabase): NovelBookmarkRepository =
        RoomNovelBookmarkRepository(db.novelBookmarkDao())

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

    @Provides @Singleton
    fun collectionRepository(db: AppDatabase): CollectionRepository =
        RoomCollectionRepository(db.collectionDao())

    @Provides @Singleton
    fun pluginRepoDao(db: AppDatabase): com.komgareader.data.db.PluginRepoDao = db.pluginRepoDao()

    @Provides @Singleton
    fun repoStore(
        dao: com.komgareader.data.db.PluginRepoDao,
        settings: SettingsRepository,
    ): com.komgareader.data.plugin.repo.RepoStore =
        com.komgareader.data.plugin.repo.RepoStore(dao, settings)

    @Provides @Singleton
    fun pluginRepoClient(): com.komgareader.data.plugin.repo.PluginRepoClient =
        com.komgareader.data.plugin.repo.PluginRepoClient(
            // Timeouts setzen, damit ein langsames/hängendes Repo den Download nicht unbegrenzt blockiert.
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
        )

    @Provides @Singleton
    fun githubReleaseClient(): com.komgareader.data.update.GithubReleaseClient =
        com.komgareader.data.update.GithubReleaseClient(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                // The APK download can be large → more generous call timeout.
                .callTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
        )
}
