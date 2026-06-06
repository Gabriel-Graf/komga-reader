package com.komgareader.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SettingEntity::class, ServerEntity::class, DownloadEntity::class, ShelfEntity::class, ColorProfileEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun serverDao(): ServerDao
    abstract fun downloadDao(): DownloadDao
    abstract fun shelfDao(): ShelfDao
    abstract fun colorProfileDao(): ColorProfileDao
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

/**
 * v5 → v6: shelves-Tabelle restrukturiert. `contentType`+`sourceIds` (CSV)
 * werden zu `sources` (kodiert `id=container,…|…`) und nullable `defaultContentType`.
 * Bestehende Gruppen bleiben erhalten: alte sourceIds werden als „ganze Quelle"
 * übernommen, der alte contentType wird zum Viewer-Default.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `shelves_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `sources` TEXT NOT NULL,
                `defaultContentType` TEXT
            )""",
        )
        db.execSQL(
            """INSERT INTO `shelves_new` (`id`, `name`, `sources`, `defaultContentType`)
               SELECT `id`, `name`, REPLACE(`sourceIds`, ',', '=|') || '=', `contentType`
               FROM `shelves`""",
        )
        db.execSQL("DROP TABLE `shelves`")
        db.execSQL("ALTER TABLE `shelves_new` RENAME TO `shelves`")
    }
}

/**
 * v6 → v7: color_profiles-Tabelle für E-Ink-Farbfilter-Profile. Seedet zwei Built-ins
 * (Aus = neutral, Boox Go Color 7 Gen2 = Kaleido-getunt) und setzt das Go-7-Profil aktiv.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `color_profiles` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `saturation` REAL NOT NULL,
                `contrast` REAL NOT NULL,
                `brightness` REAL NOT NULL,
                `builtIn` INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            "INSERT INTO `color_profiles` (`id`,`name`,`saturation`,`contrast`,`brightness`,`builtIn`) " +
                "VALUES (1,'Aus',1.0,1.0,0.0,1)",
        )
        db.execSQL(
            "INSERT INTO `color_profiles` (`id`,`name`,`saturation`,`contrast`,`brightness`,`builtIn`) " +
                "VALUES (2,'Boox Go Color 7 Gen2',1.4,1.15,0.05,1)",
        )
        db.execSQL(
            "INSERT OR REPLACE INTO `settings` (`key`,`value`) VALUES ('active_color_profile_id','2')",
        )
    }
}
