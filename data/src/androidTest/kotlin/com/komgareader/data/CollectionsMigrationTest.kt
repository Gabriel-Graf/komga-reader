package com.komgareader.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.CollectionEntity
import com.komgareader.data.db.MIGRATION_13_14
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beweist, dass MIGRATION_13_14 nicht-destruktiv ist: Eine v13-Datenbank mit einer
 * bestehenden Zeile in `read_progress` wird über die echte Migration geöffnet. Danach
 * muss (a) die alte Zeile UNVERSEHRT vorhanden sein (kein Wipe) und (b) die drei neuen
 * Collections-Tabellen nutzbar sein. `exportSchema = false` → kein MigrationTestHelper;
 * stattdessen wird die v13-DB roh erzeugt und dann via Room mit der Migration geöffnet.
 *
 * Läuft als Instrumented-Test (connectedAndroidTest) — echtes SQLite, kein In-Memory-Fake.
 */
@RunWith(AndroidJUnit4::class)
class CollectionsMigrationTest {

    private val dbName = "migration-test-${System.nanoTime()}.db"
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
    fun migration13to14_legtCollectionsAn_ohneBestandsdatenZuLoeschen() = runBlocking {
        // 1. v13-DB roh erzeugen: vollständiges v13-Schema + eine Bestandszeile.
        createV13DatabaseWithReadProgressRow()

        // 2. Über Room mit der echten Migration öffnen (Ziel-Version = 14).
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // 3a. Bestandsdaten überlebten die Migration (kein destruktiver Wipe).
        val survivor = db.readProgressDao().get("survivor-book")
        assertEquals("survivor-book", survivor?.bookRemoteId)
        assertEquals(7, survivor?.page)

        // 3b. Die neue collections-Tabelle ist nutzbar.
        val id = db.collectionDao().insertCollection(
            CollectionEntity(name = "Meine Sammlung", kind = "MANUAL"),
        )
        val loaded = db.collectionDao().getCollection(id)!!
        assertEquals("Meine Sammlung", loaded.name)
        assertEquals("MANUAL", loaded.kind)

        db.close()
    }

    /**
     * Erzeugt eine vollständige v13-Datenbank (alle Tabellen im v13-Schema, exakt wie Room sie
     * nach den Migrationen 1→13 hätte) und eine Bestandszeile in `read_progress`. Das volle
     * Schema ist nötig, weil Room beim Öffnen ALLE Tabellen validiert — eine Teil-DB würde an
     * einer fehlenden Tabelle scheitern (Harness-Fehler, nicht Migration).
     */
    private fun createV13DatabaseWithReadProgressRow() {
        val callback = object : SupportSQLiteOpenHelper.Callback(13) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
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
                    "INSERT INTO `read_progress` " +
                        "(`bookRemoteId`,`sourceId`,`page`,`completed`,`totalPages`,`dirty`,`updatedAt`) " +
                        "VALUES ('survivor-book', 1, 7, 0, 20, 1, 1000)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        // Schreibender Zugriff löst onCreate aus und committet das v13-Schema.
        helper.writableDatabase.close()
        helper.close()
    }
}
