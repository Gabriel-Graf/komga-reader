package com.komgareader.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SettingEntity::class, ServerEntity::class, DownloadEntity::class, ShelfEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun serverDao(): ServerDao
    abstract fun downloadDao(): DownloadDao
    abstract fun shelfDao(): ShelfDao
}

/** v1 → v2: downloads-Tabelle ergänzt. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `downloads` (
                `bookRemoteId` TEXT NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `seriesRemoteId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `localPath` TEXT NOT NULL,
                `totalPages` INTEGER NOT NULL,
                PRIMARY KEY(`bookRemoteId`)
            )""",
        )
    }
}

/** v2 → v3: username-Spalte in server-Tabelle ergänzt. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `server` ADD COLUMN `username` TEXT")
    }
}

/** v3 → v4: shelves-Tabelle für Nutzer-definierte Gruppen hinzugefügt. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `shelves` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `sourceIds` TEXT NOT NULL
            )""",
        )
    }
}

/**
 * v4 → v5: Keystore-verschlüsselte Credential-Spalten in der server-Tabelle.
 * Ersetzt EncryptedSharedPreferences durch AndroidKeyStore + AES/GCM-Blobs.
 * Bestehende Zeilen erhalten NULL in allen neuen Spalten — der Nutzer muss nach dem
 * Update einmalig die Credentials neu eingeben (Migration aus EncryptedSharedPreferences
 * nicht möglich, da der Schlüssel gerätespezifisch ist und Chiffrierung sich ändert).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `server` ADD COLUMN `apiKeyCiphertext` TEXT")
        db.execSQL("ALTER TABLE `server` ADD COLUMN `apiKeyIv` TEXT")
        db.execSQL("ALTER TABLE `server` ADD COLUMN `passwordCiphertext` TEXT")
        db.execSQL("ALTER TABLE `server` ADD COLUMN `passwordIv` TEXT")
    }
}
