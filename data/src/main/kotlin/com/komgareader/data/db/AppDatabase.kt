package com.komgareader.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SettingEntity::class, ServerEntity::class, DownloadEntity::class, ShelfEntity::class,
        SeriesOverrideEntity::class, ReadProgressEntity::class, ColorProfileEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun serverDao(): ServerDao
    abstract fun downloadDao(): DownloadDao
    abstract fun shelfDao(): ShelfDao
    abstract fun seriesOverrideDao(): SeriesOverrideDao
    abstract fun readProgressDao(): ReadProgressDao
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

/** v6 → v7: Tabelle für manuelle Werk-Inhaltstypen (per Quelle + Serien-Remote-ID). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `series_overrides` (
                `sourceId` INTEGER NOT NULL,
                `seriesRemoteId` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                PRIMARY KEY(`sourceId`, `seriesRemoteId`)
            )""",
        )
    }
}

/** v7 → v8: lokaler Lesefortschritt (offline-first, dirty → Sync). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `read_progress` (
                `bookRemoteId` TEXT NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `page` INTEGER NOT NULL,
                `completed` INTEGER NOT NULL,
                `totalPages` INTEGER NOT NULL,
                `dirty` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`bookRemoteId`)
            )""",
        )
    }
}

/** v8 → v9: Serien-Metadaten (Titel/Cover) am Download für Offline-Browsing. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `seriesTitle` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `seriesCoverUrl` TEXT")
    }
}

/**
 * Seedet die drei mitgelieferten Farbfilter-Profile (Aus, Boox Go Color 7 Gen2,
 * Boox Go Color 7 — Voll) + den aktiven Pointer im **v11-Schema** (13 Spalten). Wird vom
 * Fresh-Install-Callback (onCreate) und der Selbstheilung (onOpen, wenn die Tabelle leer ist)
 * genutzt. NICHT von MIGRATION_9_10 — die erzeugt eine 6-Spalten-Tabelle und seedet selbst.
 */
private fun seedColorProfiles(db: SupportSQLiteDatabase) {
    val cols = "(`id`,`name`,`saturation`,`contrast`,`brightness`,`blackPoint`,`whitePoint`,`gamma`,`sharpenAmount`,`sharpenRadius`,`ditherMode`,`ditherLevels`,`builtIn`)"
    db.execSQL("INSERT OR REPLACE INTO `color_profiles` $cols VALUES (1,'Aus',1.0,1.0,0.0,0.0,1.0,1.0,0.0,1,'NONE',16,1)")
    db.execSQL("INSERT OR REPLACE INTO `color_profiles` $cols VALUES (2,'Boox Go Color 7 Gen2',1.4,1.15,0.05,0.0,1.0,1.0,0.0,1,'NONE',16,1)")
    db.execSQL("INSERT OR REPLACE INTO `color_profiles` $cols VALUES (3,'Boox Go Color 7 — Voll',1.4,1.15,0.05,0.05,0.95,1.2,0.6,1,'NONE',16,1)")
    db.execSQL("INSERT OR REPLACE INTO `settings` (`key`,`value`) VALUES ('active_color_profile_id','2')")
}

/**
 * v9 → v10: color_profiles-Tabelle (Phase-1, 6-spaltig) für E-Ink-Farbfilter-Profile. Seedet zwei
 * Built-ins (Aus = neutral, Boox Go Color 7 Gen2 = Kaleido-getunt) und setzt das Go-7-Profil aktiv.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Robust gegen eine inkompatible Alt-Tabelle aus parallelem Branch-Testen (z. B. ein
        // `color_profiles` mit zusätzlichen NOT-NULL-Spalten): vor dem Anlegen verwerfen. In der
        // master-Lineage existiert die Tabelle vor v10 nicht → für saubere DBs ein No-Op.
        db.execSQL("DROP TABLE IF EXISTS `color_profiles`")
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
        // 6-Spalten-Seed (Phase-1-Form). Die Phase-2-Spalten + das Voll-Built-in kommen erst in
        // MIGRATION_10_11 dazu — hier NICHT das v11-seedColorProfiles (13-spaltig) nutzen.
        db.execSQL("INSERT OR REPLACE INTO `color_profiles` (`id`,`name`,`saturation`,`contrast`,`brightness`,`builtIn`) VALUES (1,'Aus',1.0,1.0,0.0,1)")
        db.execSQL("INSERT OR REPLACE INTO `color_profiles` (`id`,`name`,`saturation`,`contrast`,`brightness`,`builtIn`) VALUES (2,'Boox Go Color 7 Gen2',1.4,1.15,0.05,1)")
        db.execSQL("INSERT OR REPLACE INTO `settings` (`key`,`value`) VALUES ('active_color_profile_id','2')")
    }
}

/**
 * v10 → v11: Phase-2-Pixel-Pipeline-Spalten (Levels/Gamma/Unsharp/Dither). Die Tabelle wird
 * **neu erstellt** (statt ALTER ADD COLUMN), weil ALTER in SQLite bei `NOT NULL` einen
 * Spalten-`DEFAULT` erzwingt — das Entity hat aber keinen `@ColumnInfo(defaultValue=…)`, sodass
 * Rooms Schema-Validierung nach der Migration fehlschlüge und `fallbackToDestructiveMigration`
 * die GANZE Datenbank löschen würde (Datenverlust). Die neue Tabelle hat exakt das vom Entity
 * generierte Schema (keine Spalten-Defaults); bestehende Profile werden mit neutralen Phase-2-
 * Werten übernommen. Zusätzlich das Demo-Built-in „Boox Go Color 7 — Voll". Aktiver Pointer
 * bleibt unverändert.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `color_profiles_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`saturation` REAL NOT NULL, `contrast` REAL NOT NULL, `brightness` REAL NOT NULL, " +
                "`blackPoint` REAL NOT NULL, `whitePoint` REAL NOT NULL, `gamma` REAL NOT NULL, " +
                "`sharpenAmount` REAL NOT NULL, `sharpenRadius` INTEGER NOT NULL, " +
                "`ditherMode` TEXT NOT NULL, `ditherLevels` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `color_profiles_new` " +
                "(`id`,`name`,`saturation`,`contrast`,`brightness`,`blackPoint`,`whitePoint`,`gamma`,`sharpenAmount`,`sharpenRadius`,`ditherMode`,`ditherLevels`,`builtIn`) " +
                "SELECT `id`,`name`,`saturation`,`contrast`,`brightness`,0,1,1,0,1,'NONE',16,`builtIn` FROM `color_profiles`",
        )
        db.execSQL("DROP TABLE `color_profiles`")
        db.execSQL("ALTER TABLE `color_profiles_new` RENAME TO `color_profiles`")
        db.execSQL(
            "INSERT OR REPLACE INTO `color_profiles` " +
                "(`id`,`name`,`saturation`,`contrast`,`brightness`,`blackPoint`,`whitePoint`,`gamma`,`sharpenAmount`,`sharpenRadius`,`ditherMode`,`ditherLevels`,`builtIn`) " +
                "VALUES (3,'Boox Go Color 7 — Voll',1.4,1.15,0.05,0.05,0.95,1.2,0.6,1,'NONE',16,1)",
        )
    }
}

/**
 * Fresh-Install: Room legt die Tabellen direkt aus den Entities an — die Migration läuft
 * dann NICHT. Daher hier die Built-in-Profile seeden.
 */
val SEED_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedColorProfiles(db)
    }

    /**
     * Selbstheilung: feuert bei jedem Öffnen. Ist die Profil-Tabelle leer (z. B. weil ein
     * früherer fehlerhafter Build eine destruktive Migration ausgelöst hat — onCreate läuft
     * dann nicht), werden die Built-ins erneut geseedet. Verhindert eine leere Profilliste.
     */
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        val isEmpty = db.query("SELECT COUNT(*) FROM `color_profiles`").use { c ->
            c.moveToFirst() && c.getInt(0) == 0
        }
        if (isEmpty) seedColorProfiles(db)
    }
}
