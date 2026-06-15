package com.komgareader.app.ci.ui

import android.content.Context
import androidx.room.Room
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.SEED_CALLBACK
import com.komgareader.data.di.DataModule
import com.komgareader.data.download.DownloadManager
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.data.repository.RoomCollectionRepository
import com.komgareader.data.repository.RoomColorProfileRepository
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomNovelProgressRepository
import com.komgareader.data.repository.RoomReadProgressRepository
import com.komgareader.data.repository.RoomSeriesOverrideRepository
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.data.repository.RoomShelfRepository
import com.komgareader.data.security.CredentialStore
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.NovelProgressRepository
import com.komgareader.domain.repository.ReadProgressRepository
import com.komgareader.domain.repository.SeriesOverrideRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.repository.ShelfRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.testing.TestInstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Test-Doppel für [DataModule]: identische Bindings, aber die [AppDatabase] ist **in-memory**
 * (frisch pro Hilt-Test-Komponente → pro Test isoliert, kein Zugriff auf die echte
 * `komga-reader.db`). Der KeystoreCredentialStore nutzt einen eindeutigen Alias je Testlauf,
 * damit keine echten App-Credentials berührt werden.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SEED_CALLBACK)
            .build()

    @Provides @Singleton
    fun settingsRepository(db: AppDatabase): SettingsRepository = RoomSettingsRepository(db.settingsDao())

    @Provides @Singleton
    fun keystoreCredentialStore(): KeystoreCredentialStore = KeystoreCredentialStore("ci-ui-test")

    @Provides @Singleton
    fun credentialStore(store: KeystoreCredentialStore): CredentialStore = store

    @Provides @Singleton
    fun serverRepository(db: AppDatabase, credentials: KeystoreCredentialStore): ServerRepository =
        RoomServerRepository(db.serverDao(), credentials)

    @Provides @Singleton
    fun downloadRepository(db: AppDatabase): DownloadRepository = RoomDownloadRepository(db.downloadDao())

    @Provides @Singleton
    fun localBookBytes(manager: DownloadManager): LocalBookBytes = manager

    @Provides @Singleton
    fun shelfRepository(db: AppDatabase): ShelfRepository = RoomShelfRepository(db.shelfDao())

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
    fun collectionRepository(db: AppDatabase): CollectionRepository = RoomCollectionRepository(db.collectionDao())

    // Plugin-Repo-Browser-Bindings (spiegeln DataModule) — sonst fehlen sie im Test-Graph, weil
    // dieses Modul DataModule komplett ersetzt (@TestInstallIn replaces). PluginCatalog/PluginsViewModel
    // der ci.ui-Tests brauchen RepoStore + PluginRepoClient.
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
        com.komgareader.data.plugin.repo.PluginRepoClient(okhttp3.OkHttpClient())

    // Bindings neuerer Features (spiegeln DataModule) — sonst fehlen sie im Test-Graph,
    // weil dieses Modul DataModule komplett ersetzt (@TestInstallIn replaces).
    @Provides @Singleton
    fun novelBookmarkRepository(db: AppDatabase): com.komgareader.domain.repository.NovelBookmarkRepository =
        com.komgareader.data.repository.RoomNovelBookmarkRepository(db.novelBookmarkDao())

    @Provides @Singleton
    fun readingStatsRepository(db: AppDatabase): com.komgareader.domain.repository.ReadingStatsRepository =
        com.komgareader.data.repository.RoomReadingStatsRepository(
            sessions = db.readingSessionDao(),
            readProgress = db.readProgressDao(),
            novelProgress = db.novelProgressDao(),
        )

    @Provides @Singleton
    fun githubReleaseClient(): com.komgareader.data.update.GithubReleaseClient =
        com.komgareader.data.update.GithubReleaseClient(okhttp3.OkHttpClient())
}
