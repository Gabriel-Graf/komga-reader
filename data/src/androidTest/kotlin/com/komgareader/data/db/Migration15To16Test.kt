package com.komgareader.data.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beweist, dass MIGRATION_15_16 nicht-destruktiv ist: Eine v15-DB mit einem Bestands-Profil in
 * `color_profiles` wird über die echte Migration auf v16 geöffnet. Danach muss (a) das alte Profil
 * unversehrt vorhanden sein (kein Wipe) und (b) die neue Spalte `pluginPackage` mit NULL existieren.
 */
@RunWith(AndroidJUnit4::class)
class Migration15To16Test {

    private val dbName = "migration-15-16-test-${System.nanoTime()}.db"
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun deleteOldDb() { ctx.deleteDatabase(dbName) }
    @After fun cleanup() { ctx.deleteDatabase(dbName) }

    @Test
    fun migrate15To16_behaeltProfil_undFuegtPluginPackageSpalteHinzu() {
        createV15DatabaseWithColorProfile()

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_15_16)
            .allowMainThreadQueries()
            .build()

        val cursor = db.openHelper.readableDatabase.query(
            "SELECT `name`, `pluginPackage` FROM `color_profiles` WHERE `id` = 99",
        )
        assertTrue("Profil muss nach der Migration noch vorhanden sein", cursor.moveToFirst())
        assertEquals("Bestand", cursor.getString(0))
        assertTrue("pluginPackage muss NULL für Altbestand sein", cursor.isNull(1))
        cursor.close()
        db.close()
    }

    /**
     * Erzeugt eine vollständige v15-Datenbank (alle Tabellen exakt im v15-Schema) plus ein
     * Bestands-Profil. Room validiert beim Öffnen ALLE Tabellen — eine Teil-DB würde scheitern.
     */
    private fun createV15DatabaseWithColorProfile() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `server` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `kind` TEXT NOT NULL DEFAULT 'KOMGA', `username` TEXT, `apiKeyCiphertext` TEXT, `apiKeyIv` TEXT, `passwordCiphertext` TEXT, `passwordIv` TEXT, `extrasCiphertext` TEXT, `extrasIv` TEXT, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `format` TEXT NOT NULL, `localPath` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, `seriesTitle` TEXT NOT NULL DEFAULT '', `seriesCoverUrl` TEXT, PRIMARY KEY(`bookRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shelves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sources` TEXT NOT NULL, `defaultContentType` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `series_overrides` (`sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, `contentType` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `seriesRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `read_progress` (`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `page` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `color_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `saturation` REAL NOT NULL, `contrast` REAL NOT NULL, `brightness` REAL NOT NULL, `blackPoint` REAL NOT NULL, `whitePoint` REAL NOT NULL, `gamma` REAL NOT NULL, `sharpenAmount` REAL NOT NULL, `sharpenRadius` INTEGER NOT NULL, `ditherMode` TEXT NOT NULL, `ditherLevels` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `novel_progress` (`sourceId` INTEGER NOT NULL, `bookId` TEXT NOT NULL, `anchor` TEXT NOT NULL, `fraction` REAL NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `bookId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collection_members` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `position` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_members_collectionId` ON `collection_members` (`collectionId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collection_sync_links` (`collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `remoteCollectionId` TEXT, `status` TEXT NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `sourceId`))")
                    db.execSQL("INSERT INTO `color_profiles` (`id`,`name`,`saturation`,`contrast`,`brightness`,`blackPoint`,`whitePoint`,`gamma`,`sharpenAmount`,`sharpenRadius`,`ditherMode`,`ditherLevels`,`builtIn`) VALUES (99,'Bestand',1.0,1.0,0.0,0.0,1.0,1.0,0.0,1,'NONE',16,0)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }
}
