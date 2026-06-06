package com.komgareader.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.MIGRATION_1_2
import com.komgareader.data.db.MIGRATION_2_3
import com.komgareader.data.db.MIGRATION_3_4
import com.komgareader.data.db.ServerEntity
import com.komgareader.data.security.EncryptedCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Einmal-Setup: schreibt die echte Server-Konfiguration in die App-Datenbank
 * (gleicher DB-Name und EncryptedCredentialStore-Alias wie in DataModule).
 * Nach diesem Test kann die App die NAS-Verbindung nutzen.
 */
@RunWith(AndroidJUnit4::class)
class SetupServerTest {

    @Test
    fun speichert_server_konfiguration() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Gleicher DB-Name und gleiche Migrationen wie DataModule.database()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "komga-reader.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
        withContext(Dispatchers.IO) {
            db.serverDao().save(
                ServerEntity(
                    name = "NAS",
                    baseUrl = "http://10.0.2.2:25600/api/v1/",
                    username = null,
                ),
            )
        }
        db.close()

        // API-Key direkt in EncryptedSharedPreferences schreiben
        val store = EncryptedCredentialStore(ctx)
        store.setApiKey("2243c9f4ecc5404992ddf8eba4bf6488")
        // Gelesenen Wert verifizieren
        val readBack = store.getApiKey()
        assertEquals("2243c9f4ecc5404992ddf8eba4bf6488", readBack)
    }
}
