package com.komgareader.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.MIGRATION_17_18
import com.komgareader.data.db.MIGRATION_18_19
import com.komgareader.data.db.NovelBookmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the migrations up to the bookmark table are non-destructive: a v17 database with an
 * existing row in `read_progress` is opened through the real migration chain (17→18→19; the
 * `novel_bookmark` table lands in MIGRATION_18_19, after MIGRATION_17_18's `reading_session`).
 * Afterwards (a) the old row must be intact (no wipe) and (b) the new `novel_bookmark` table must
 * be usable. `exportSchema = false` → no MigrationTestHelper; instead the v17 DB is created raw and
 * then opened via Room with the migrations.
 *
 * Ran on: Instrumented (connectedAndroidTest) — real SQLite, no in-memory fake.
 */
@RunWith(AndroidJUnit4::class)
class NovelBookmarkMigrationTest {

    private val dbName = "migration-test-bookmark-${System.nanoTime()}.db"
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
    fun migration17to19_legtNovelBookmarkAn_ohneBestandsdatenZuLoeschen() = runBlocking {
        // 1. v17-DB roh erzeugen: volles v17-Schema + eine read_progress-Bestandszeile.
        createV17DatabaseWithReadProgressRow()

        // 2. Über Room mit den echten Migrationen öffnen (Ziel-Version = 19; Kette 17→18→19).
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_17_18, MIGRATION_18_19)
            .allowMainThreadQueries()
            .build()

        // 3a. Bestandsdaten überlebten die Migration (kein destruktiver Wipe).
        val survivor = db.readProgressDao().get("survivor-book")
        assertEquals("survivor-book", survivor?.bookRemoteId)
        assertEquals(5, survivor?.page)

        // 3b. Die neue Tabelle ist nutzbar.
        db.novelBookmarkDao().insert(
            NovelBookmarkEntity(
                id = 0, sourceId = 1, bookId = "novel-1", xpointer = "/body/DocFragment[2].0",
                number = 1, label = "Intro", snippet = "the quick brown fox", createdAt = 99,
            ),
        )
        val loaded = db.novelBookmarkDao().observe(1, "novel-1").first().single()
        assertEquals("/body/DocFragment[2].0", loaded.xpointer)
        assertEquals("Intro", loaded.label)

        db.close()
    }

    /**
     * Erzeugt eine vollständige v17-Datenbank (alle Tabellen im v17-Schema, exakt wie Room sie
     * nach den Migrationen 1→17 hätte) und eine Bestandszeile in `read_progress`. Das volle
     * Schema ist nötig, weil Room beim Öffnen ALLE Tabellen validiert — eine Teil-DB würde an
     * einer fehlenden Tabelle scheitern (Harness-Fehler, nicht Migration).
     */
    private fun createV17DatabaseWithReadProgressRow() {
        val callback = object : SupportSQLiteOpenHelper.Callback(17) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `server` (" +
                        "`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, " +
                        "`username` TEXT, `apiKeyCiphertext` TEXT, `apiKeyIv` TEXT, " +
                        "`passwordCiphertext` TEXT, `passwordIv` TEXT, " +
                        "`kind` TEXT NOT NULL DEFAULT 'KOMGA', " +
                        "`extrasCiphertext` TEXT, `extrasIv` TEXT, PRIMARY KEY(`id`))",
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
                        "`ditherMode` TEXT NOT NULL, `ditherLevels` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, " +
                        "`pluginPackage` TEXT)",
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
                        "`sourceId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL)",
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
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plugin_repos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `name` TEXT)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plugin_repos_url` ON `plugin_repos` (`url`)")
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
        // Schreibender Zugriff löst onCreate aus und committet die v17-Tabellen.
        helper.writableDatabase.close()
        helper.close()
    }
}
