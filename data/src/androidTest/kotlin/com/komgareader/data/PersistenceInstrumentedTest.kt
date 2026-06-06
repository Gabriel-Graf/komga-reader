package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.ServerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }
    @After fun teardown() = db.close()

    @Test fun server_wird_gespeichert_und_gelesen() = runTest {
        val store = KeystoreCredentialStore("test-cred-key-${System.nanoTime()}")
        val repo = RoomServerRepository(db.serverDao(), store)
        assertNull(repo.config.first())
        repo.save(ServerConfig(name = "NAS", baseUrl = "https://nas.local/api/v1/", apiKey = "geheim"))
        val loaded = repo.config.first()!!
        assertEquals("NAS", loaded.name)
        assertEquals("geheim", loaded.apiKey)
        // Secret darf NICHT im Klartext in der Room-Entity liegen:
        val entity = db.serverDao().observe().first()!!
        assertNull("Klartext-ApiKey darf nicht in Room stehen", entity.apiKeyCiphertext?.takeIf { it == "geheim" })
        assert(entity.apiKeyCiphertext != null) { "Ciphertext muss in Room persistiert sein" }
        repo.clear()
        assertNull(repo.config.first())
    }

    @Test fun server_mit_benutzername_passwort_wird_gespeichert_und_gelesen() = runTest {
        val store = KeystoreCredentialStore("test-cred-key-${System.nanoTime()}")
        val repo = RoomServerRepository(db.serverDao(), store)
        assertNull(repo.config.first())
        repo.save(ServerConfig(name = "Heimserver", baseUrl = "https://home.local/api/v1/", username = "admin", password = "s3krit"))
        val loaded = repo.config.first()!!
        assertEquals("Heimserver", loaded.name)
        assertEquals("admin", loaded.username)
        assertEquals("s3krit", loaded.password)
        assertNull(loaded.apiKey)
        // Passwort-Ciphertext muss in Room liegen:
        val entity = db.serverDao().observe().first()!!
        assert(entity.passwordCiphertext != null) { "Passwort-Ciphertext muss in Room persistiert sein" }
        assertNull("Klartext-Passwort darf nicht in Room stehen", entity.passwordCiphertext?.takeIf { it == "s3krit" })
        repo.clear()
        assertNull(repo.config.first())
    }

    @Test fun settings_default_und_ueberschreiben() = runTest {
        val repo = RoomSettingsRepository(db.settingsDao())
        assertEquals("SYSTEM", repo.themeMode.first())
        assertEquals("de", repo.language.first())
        repo.setThemeMode("DARK")
        repo.setLanguage("en")
        assertEquals("DARK", repo.themeMode.first())
        assertEquals("en", repo.language.first())
    }

    @Test fun download_dir_setzen_und_zuruecksetzen() = runTest {
        val repo = RoomSettingsRepository(db.settingsDao())
        assertNull(repo.downloadDir.first())
        repo.setDownloadDir("content://com.android.externalstorage/tree/primary%3ADownloads")
        assertEquals(
            "content://com.android.externalstorage/tree/primary%3ADownloads",
            repo.downloadDir.first(),
        )
        repo.setDownloadDir(null)
        assertNull(repo.downloadDir.first())
    }
}
