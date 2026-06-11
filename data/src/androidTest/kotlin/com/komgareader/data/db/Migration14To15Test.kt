package com.komgareader.data.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beweist, dass MIGRATION_14_15 nicht-destruktiv ist: Eine v14-Datenbank mit einer
 * bestehenden Zeile in `server` wird über die echte Migration geöffnet. Danach muss
 * (a) die alte Server-Zeile UNVERSEHRT vorhanden sein (kein Wipe) und (b) die neuen
 * Spalten `extrasCiphertext` und `extrasIv` mit NULL für Altbestand vorhanden sein.
 *
 * Läuft als Instrumented-Test (connectedAndroidTest) — echtes SQLite, kein In-Memory-Fake.
 * `exportSchema = false` → kein MigrationTestHelper; stattdessen wird die v14-DB roh
 * erzeugt und dann via Room mit der Migration geöffnet (wie CollectionsMigrationTest).
 */
@RunWith(AndroidJUnit4::class)
class Migration14To15Test {

    private val dbName = "migration-14-15-test-${System.nanoTime()}.db"
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun deleteOldDb() {
        ctx.deleteDatabase(dbName)
    }

    @After
    fun cleanup() {
        ctx.deleteDatabase(dbName)
    }

    @Test
    fun migrate14To15_behaeltServerZeile_undFuegtExtrasSpaltenHinzu() {
        // 1. v14-DB roh erzeugen: vollständiges v14-Schema + eine Bestandszeile in `server`.
        createV14DatabaseWithServerRow()

        // 2. Über Room mit der echten Migration öffnen (Ziel-Version = 15).
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()

        // 3a. Bestandsdaten überlebten die Migration (kein destruktiver Wipe).
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT `name`, `baseUrl`, `extrasCiphertext`, `extrasIv` FROM `server` WHERE `id` = 1",
        )
        assertTrue("Server-Zeile muss nach der Migration noch vorhanden sein", cursor.moveToFirst())
        assertEquals("TestServer", cursor.getString(0))
        assertEquals("https://komga.example.com", cursor.getString(1))

        // 3b. Neue Spalten sind NULL für Altbestand (kein Default).
        assertTrue("extrasCiphertext muss NULL für Altbestand sein", cursor.isNull(2))
        assertTrue("extrasIv muss NULL für Altbestand sein", cursor.isNull(3))
        cursor.close()

        db.close()
    }

    /**
     * Erzeugt eine vollständige v14-Datenbank (alle Tabellen im v14-Schema, exakt wie Room sie
     * nach den Migrationen 1→14 hätte) und eine Bestandszeile in `server`. Das volle Schema
     * ist nötig, weil Room beim Öffnen ALLE Tabellen validiert — eine Teil-DB würde an einer
     * fehlenden Tabelle scheitern.
     *
     * NOT-NULL-Spalten der `server`-Tabelle in v14:
     * - `id` INTEGER NOT NULL
     * - `name` TEXT NOT NULL
     * - `baseUrl` TEXT NOT NULL
     * - `kind` TEXT NOT NULL DEFAULT 'KOMGA'
     * Alle anderen Spalten (`username`, `apiKeyCiphertext`, `apiKeyIv`, `passwordCiphertext`,
     * `passwordIv`) sind nullable.
     */
    private fun createV14DatabaseWithServerRow() {
        val callback = object : SupportSQLiteOpenHelper.Callback(14) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `server` (" +
                        "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, " +
                        "`username` TEXT, `apiKeyCiphertext` TEXT, `apiKeyIv` TEXT, " +
                        "`passwordCiphertext` TEXT, `passwordIv` TEXT, " +
                        "`kind` TEXT NOT NULL DEFAULT 'KOMGA', PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, " +
                        "`seriesRemoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `format` TEXT NOT NULL, " +
                        "`localPath` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, " +
                        "`seriesTitle` TEXT NOT NULL DEFAULT '', `seriesCoverUrl` TEXT, " +
                        "PRIMARY KEY(`bookRemoteId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shelves` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`sources` TEXT NOT NULL, `defaultContentType` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `series_overrides` (" +
                        "`sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, " +
                        "`contentType` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `seriesRemoteId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `read_progress` (" +
                        "`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `page` INTEGER NOT NULL, " +
                        "`completed` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookRemoteId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `color_profiles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`saturation` REAL NOT NULL, `contrast` REAL NOT NULL, `brightness` REAL NOT NULL, " +
                        "`blackPoint` REAL NOT NULL, `whitePoint` REAL NOT NULL, `gamma` REAL NOT NULL, " +
                        "`sharpenAmount` REAL NOT NULL, `sharpenRadius` INTEGER NOT NULL, " +
                        "`ditherMode` TEXT NOT NULL, `ditherLevels` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `novel_progress` (" +
                        "`sourceId` INTEGER NOT NULL, `bookId` TEXT NOT NULL, `anchor` TEXT NOT NULL, " +
                        "`fraction` REAL NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`sourceId`, `bookId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collections` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection_members` (" +
                        "`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `collectionId` INTEGER NOT NULL, " +
                        "`sourceId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `position` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_members_collectionId` ON `collection_members` (`collectionId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection_sync_links` (" +
                        "`collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, " +
                        "`remoteCollectionId` TEXT, `status` TEXT NOT NULL, `dirty` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `sourceId`))",
                )
                // Bestandszeile: simuliert einen vor der Migration konfigurierten Server.
                db.execSQL(
                    "INSERT INTO `server` (`id`,`name`,`baseUrl`,`kind`) VALUES (1,'TestServer','https://komga.example.com','KOMGA')",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        // Schreibender Zugriff löst onCreate aus und committet das v14-Schema.
        helper.writableDatabase.close()
        helper.close()
    }
}
