package com.komgareader.data.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration16To17Test {
    private val dbName = "migration-16-17-test-${System.nanoTime()}.db"
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun deleteOldDb() { ctx.deleteDatabase(dbName) }
    @After fun cleanup() { ctx.deleteDatabase(dbName) }

    @Test
    fun migrate16To17_legtPluginReposTabelleAn_leer() {
        createV16Database()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_16_17)
            .allowMainThreadQueries()
            .build()
        val c = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `plugin_repos`")
        c.moveToFirst()
        assertEquals(0, c.getInt(0))
        c.close()
        db.close()
    }

    private fun createV16Database() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(16) {
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
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }
}
