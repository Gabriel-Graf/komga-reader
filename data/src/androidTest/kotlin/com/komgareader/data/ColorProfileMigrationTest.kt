package com.komgareader.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.MIGRATION_6_7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorProfileMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration6To7_seedsBuiltInsAndSetsActive() {
        val name = "migration-test.db"
        helper.createDatabase(name, 6).apply {
            execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL PRIMARY KEY, `value` TEXT NOT NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7)
        db.query("SELECT COUNT(*) FROM color_profiles").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        db.query("SELECT value FROM settings WHERE key='active_color_profile_id'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2", cursor.getString(0))
        }
        db.close()
    }
}
