package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.data.security.EncryptedCredentialStore
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
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = EncryptedCredentialStore(ctx, "test-secrets-${System.nanoTime()}")
        store.clear()
        val repo = RoomServerRepository(db.serverDao(), store)
        assertNull(repo.config.first())
        repo.save(ServerConfig(name = "NAS", baseUrl = "https://nas.local/api/v1/", apiKey = "geheim"))
        val loaded = repo.config.first()!!
        assertEquals("NAS", loaded.name)
        assertEquals("geheim", loaded.apiKey)
        // Secret darf NICHT in der Room-Entity liegen:
        assertEquals("geheim", store.getApiKey())
        repo.clear()
        assertNull(repo.config.first())
        assertNull(store.getApiKey())
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
}
