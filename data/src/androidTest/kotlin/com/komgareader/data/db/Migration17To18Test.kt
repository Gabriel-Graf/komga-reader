package com.komgareader.data.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-disk verification that [MIGRATION_17_18] creates the `reading_session` table and leaves
 * existing data intact. [exportSchema] is false, so [androidx.room.testing.MigrationTestHelper]
 * cannot be used. Instead we hand-build a v17 on-disk database and drive the migration via
 * Room's [Room.databaseBuilder] + [addMigrations]. In-memory upgrade tests are falsely green for
 * on-disk regressions, so this uses a real on-disk SQLite file.
 */
@RunWith(AndroidJUnit4::class)
class Migration17To18Test {
    private val dbName = "migration-17-18-test-${System.nanoTime()}.db"
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun deleteOldDb() { ctx.deleteDatabase(dbName) }
    @After fun cleanup() { ctx.deleteDatabase(dbName) }

    @Test
    fun migrate17To18_createsReadingSession_preservesExistingData() {
        createV17Database()

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_17_18)
            .allowMainThreadQueries()
            .build()

        val rawDb = db.openHelper.writableDatabase

        // Existing read_progress row must survive the migration.
        rawDb.query("SELECT COUNT(*) FROM `read_progress`").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // The new reading_session table must be usable.
        rawDb.execSQL(
            "INSERT INTO `reading_session` " +
                "(`readerKind`, `bookRemoteId`, `sourceId`, `startTs`, `durationMs`) " +
                "VALUES ('PAGED', 'b1', 1, 0, 5000)"
        )
        rawDb.query("SELECT COUNT(*) FROM `reading_session`").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        db.close()
    }

    /** Builds a minimal v17 on-disk database with every table that existed at that version. */
    private fun createV17Database() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `server` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `kind` TEXT NOT NULL DEFAULT 'KOMGA', `username` TEXT, `apiKeyCiphertext` TEXT, `apiKeyIv` TEXT, `passwordCiphertext` TEXT, `passwordIv` TEXT, `extrasCiphertext` TEXT, `extrasIv` TEXT, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `format` TEXT NOT NULL, `localPath` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, `seriesTitle` TEXT NOT NULL DEFAULT '', `seriesCoverUrl` TEXT, PRIMARY KEY(`bookRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shelves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sources` TEXT NOT NULL, `defaultContentType` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `series_overrides` (`sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, `contentType` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `seriesRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `read_progress` (`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `page` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `color_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `saturation` REAL NOT NULL, `contrast` REAL NOT NULL, `brightness` REAL NOT NULL, `blackPoint` REAL NOT NULL, `whitePoint` REAL NOT NULL, `gamma` REAL NOT NULL, `sharpenAmount` REAL NOT NULL, `sharpenRadius` INTEGER NOT NULL, `ditherMode` TEXT NOT NULL, `ditherLevels` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, `pluginPackage` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `novel_progress` (`sourceId` INTEGER NOT NULL, `bookId` TEXT NOT NULL, `anchor` TEXT NOT NULL, `fraction` REAL NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `bookId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collection_members` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `position` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_members_collectionId` ON `collection_members` (`collectionId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collection_sync_links` (`collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `remoteCollectionId` TEXT, `status` TEXT NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `sourceId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `plugin_repos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `name` TEXT)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plugin_repos_url` ON `plugin_repos` (`url`)")

                    // Seed one read_progress row to verify data survives the migration.
                    db.execSQL(
                        "INSERT INTO `read_progress` " +
                            "(`bookRemoteId`, `sourceId`, `page`, `completed`, `totalPages`, `dirty`, `updatedAt`) " +
                            "VALUES ('b1', 1, 3, 1, 10, 0, 123)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }
}
