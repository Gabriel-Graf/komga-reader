package com.komgareader.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.security.EncryptedCredentialStore
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFlowInstrumentedTest {

    @Test fun laedt_echte_serien_von_lokaler_komga() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val store = EncryptedCredentialStore(ctx, "test-secrets-${System.nanoTime()}")
        val repo = RoomServerRepository(db.serverDao(), store)
        repo.save(ServerConfig(
            name = "Test", baseUrl = "http://10.0.2.2:25600/api/v1/",
            apiKey = "2243c9f4ecc5404992ddf8eba4bf6488",
        ))
        val source = KomgaSourceProvider().from(repo.config.first())!!
        val page = source.browse(0, SourceFilter())
        val titles = page.items.map { it.title }
        assertTrue("Serien geladen: $titles", titles.any { it.contains("Berserk") })
        assertTrue("Serien geladen: $titles", titles.any { it.contains("Saga") })
        db.close()
    }
}
