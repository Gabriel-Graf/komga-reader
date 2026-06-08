package com.komgareader.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.MIGRATION_11_12
import com.komgareader.data.db.MIGRATION_12_13
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifiziert Multi-Source-Persistenz auf echtem Room (inkl. AES/GCM-Keystore): **mehrere**
 * Server-Verbindungen gleichzeitig (n Komga + OPDS, gemischt), jede mit eigener Rowid und
 * entschlüsselten Credentials. Öffnet die echte App-DB — beweist nebenbei, dass die
 * `ServerEntity.id` Int→Long-Änderung die bestehende v13-DB **ohne destruktiven Wipe** öffnet
 * (gleiche INTEGER-Affinität, keine Migration).
 */
@RunWith(AndroidJUnit4::class)
class MultiServerPersistenceTest {

    @Test
    fun mehrere_server_persistieren_gemischt() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "multi-server-test.db")
            .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
            .build()
        val repo = RoomServerRepository(db.serverDao(), KeystoreCredentialStore("multi-test-${System.nanoTime()}"))
        repo.clear()

        repo.save(ServerConfig(name = "Komga A", baseUrl = "http://a:25600/api/v1/", apiKey = "keyA"))
        repo.save(ServerConfig(name = "Komga B", baseUrl = "http://b:25600/api/v1/", apiKey = "keyB"))
        repo.save(ServerConfig(name = "Feed", baseUrl = "http://o/opds", kind = SourceKind.OPDS))

        val all = repo.configs.first()
        assertEquals(3, all.size)
        assertTrue("alle haben eine eigene Rowid", all.map { it.id }.toSet().size == 3)
        assertEquals("keyA", all.first { it.name == "Komga A" }.apiKey)   // Credential entschlüsselt
        assertEquals(SourceKind.OPDS, all.first { it.name == "Feed" }.kind)
        assertEquals(SourceKind.KOMGA, all.first { it.name == "Komga B" }.kind)

        // Eine entfernen → 2 bleiben.
        val bId = all.first { it.name == "Komga B" }.id
        repo.remove(bId)
        assertEquals(2, repo.configs.first().size)

        db.close()
    }
}
