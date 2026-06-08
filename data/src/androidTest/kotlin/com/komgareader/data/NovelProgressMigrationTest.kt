package com.komgareader.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.MIGRATION_11_12
import com.komgareader.data.db.NovelProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beweist, dass MIGRATION_11_12 nicht-destruktiv ist: Eine v11-Datenbank mit einer
 * bestehenden Zeile in `read_progress` wird über die echte Migration geöffnet. Danach
 * muss (a) die alte Zeile UNVERSEHRT vorhanden sein (kein Wipe) und (b) die neue
 * `novel_progress`-Tabelle nutzbar sein. `exportSchema = false` → kein MigrationTestHelper;
 * stattdessen wird die v11-DB roh erzeugt und dann via Room mit der Migration geöffnet.
 *
 * Lief auf: Instrumented (connectedAndroidTest) — echtes SQLite, kein In-Memory-Fake.
 */
@RunWith(AndroidJUnit4::class)
class NovelProgressMigrationTest {

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
    fun migration11to12_legtNovelProgressAn_ohneBestandsdatenZuLoeschen() = runBlocking {
        // 1. v11-DB roh erzeugen: nur die hier relevante read_progress-Tabelle + eine Zeile.
        createV11DatabaseWithReadProgressRow()

        // 2. Über Room mit der echten Migration öffnen (Ziel-Version = 12).
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_11_12)
            .allowMainThreadQueries()
            .build()

        // 3a. Bestandsdaten überlebten die Migration (kein destruktiver Wipe).
        val survivor = db.readProgressDao().get("survivor-book")
        assertEquals("survivor-book", survivor?.bookRemoteId)
        assertEquals(5, survivor?.page)

        // 3b. Die neue Tabelle ist nutzbar.
        db.novelProgressDao().upsert(
            NovelProgressEntity(1, "novel-1", "/body/DocFragment[2].0", 0.5f, true, 99),
        )
        val loaded = db.novelProgressDao().get(1, "novel-1")!!
        assertEquals("/body/DocFragment[2].0", loaded.anchor)
        assertEquals(0.5f, loaded.fraction, 0.0001f)

        db.close()
    }

    /**
     * Erzeugt eine vollständige v11-Datenbank (alle Tabellen im v11-Schema, exakt wie Room sie
     * nach den Migrationen 1→11 hätte) und eine Bestandszeile in `read_progress`. Das volle
     * Schema ist nötig, weil Room beim Öffnen ALLE Tabellen validiert — eine Teil-DB würde an
     * einer fehlenden Tabelle scheitern (Harness-Fehler, nicht Migration).
     */
    private fun createV11DatabaseWithReadProgressRow() {
        val callback = object : SupportSQLiteOpenHelper.Callback(11) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `server` (" +
                        "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, " +
                        "`username` TEXT, `apiKeyCiphertext` TEXT, `apiKeyIv` TEXT, " +
                        "`passwordCiphertext` TEXT, `passwordIv` TEXT, PRIMARY KEY(`id`))",
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
                    "INSERT INTO `read_progress` " +
                        "(`bookRemoteId`,`sourceId`,`page`,`completed`,`totalPages`,`dirty`,`updatedAt`) " +
                        "VALUES ('survivor-book', 1, 5, 0, 20, 1, 1000)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        // Schreibender Zugriff löst onCreate aus und committet die v11-Tabelle.
        helper.writableDatabase.close()
        helper.close()
    }
}
