package com.komgareader.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.ServerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Persistenz-Roundtrip: beweist, dass eine [ServerConfig] über den verschlüsselten
 * [KeystoreCredentialStore] in Room geschrieben und unverändert wieder entschlüsselt gelesen wird
 * (Schutz gegen die bekannte Room-Migrations-/Keystore-Wipe-Falle). Braucht KEINEN echten Server
 * → in-memory-DB + eindeutiger Keystore-Alias (isoliert, fasst die echte App-DB nicht an) + ein
 * Konstanten-Probe-Key (kein Secret). Läuft daher immer, nicht nur mit konfigurierter dev-local-Komga.
 */
@RunWith(AndroidJUnit4::class)
class SetupServerTest {

    @Test
    fun persistiert_und_entschluesselt_serverkonfiguration() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val store = KeystoreCredentialStore("setup-roundtrip-${System.nanoTime()}")
        val repo = RoomServerRepository(db.serverDao(), store)
        val saved = ServerConfig(
            name = "NAS-Probe",
            baseUrl = "http://example.invalid/api/v1/",
            apiKey = "roundtrip-probe-key-not-a-secret",
        )
        repo.save(saved)
        // Aus Room entschlüsselt gelesen — der ganze Datensatz muss den Roundtrip überleben.
        val loaded = repo.config.first()!!
        assertEquals(saved.name, loaded.name)
        assertEquals(saved.baseUrl, loaded.baseUrl)
        assertEquals(saved.apiKey, loaded.apiKey)
        assertEquals(saved.kind, loaded.kind)
        db.close()
    }
}
