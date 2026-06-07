package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.SEED_CALLBACK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifiziert das Fresh-Install-Seeding (onCreate-Callback): eine frische DB erhält die
 * drei Built-in-Profile und den aktiven Pointer auf das Go-7-Profil — ohne dass eine
 * Migration läuft. Deckt damit den realistischen Neu-Installationspfad ab; die Seed-SQL
 * ist mit der v6→v7-Migration geteilt.
 */
@RunWith(AndroidJUnit4::class)
class ColorProfileSeedTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).addCallback(SEED_CALLBACK).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun freshInstall_seedetBuiltInProfileUndAktivenPointer() = runBlocking {
        val profiles = db.colorProfileDao().observeAll().first()
        assertEquals(3, profiles.size)
        assertTrue(profiles.any { it.name == "Boox Go Color 7 Gen2" && it.builtIn })
        assertTrue(profiles.any { it.name == "Aus" && it.builtIn })
        assertEquals("2", db.settingsDao().observe("active_color_profile_id").first())
    }

    @Test
    fun freshInstall_enthältDemoBuiltinVoll() = runBlocking {
        val all = db.colorProfileDao().observeAll().first()
        val voll = all.firstOrNull { it.name == "Boox Go Color 7 — Voll" }
        assertTrue(voll != null && voll.builtIn)
        assertEquals(1.2f, voll!!.gamma, 0.0001f)
        assertTrue(voll.sharpenAmount > 0f)
    }
}
