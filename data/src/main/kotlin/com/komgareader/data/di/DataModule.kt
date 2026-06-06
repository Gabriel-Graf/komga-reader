package com.komgareader.data.di

import android.content.Context
import androidx.room.Room
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.MIGRATION_1_2
import com.komgareader.data.db.MIGRATION_2_3
import com.komgareader.data.db.MIGRATION_3_4
import com.komgareader.data.db.MIGRATION_4_5
import com.komgareader.data.db.MIGRATION_5_6
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.data.repository.RoomShelfRepository
import com.komgareader.data.security.CredentialStore
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
}
