package com.komgareader.app.ci

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.SourceRegistration
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceManager

/**
 * Baut einen frischen, isolierten quellen-agnostischen Stack für einen Seam-Test:
 * inMemory-Room (kein Zugriff auf echte App-DB) + eindeutiger Keystore-Alias (keine
 * Kollision mit App-Credentials, kein Wipe echter Daten) + verdrahtete [ActiveSource].
 *
 * Spiegelt das etablierte `MixedSourcesLiveTest`-Setup, an genau einer Stelle (DRY).
 * [close] schließt die DB — im `@After` aufrufen.
 */
class CiSourceStack {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    private val store = KeystoreCredentialStore("ci-seam-${System.nanoTime()}")
    private val repo = RoomServerRepository(db.serverDao(), store)
    private val sources = SourceManager()
    private val registration = SourceRegistration(sources, KomgaSourceProvider())

    val activeSource = ActiveSource(sources, repo, registration)

    /** Persistiert die gegebenen Server-Konfigurationen (wie der echte Settings-Flow). */
    suspend fun register(vararg configs: ServerConfig) {
        configs.forEach { repo.save(it) }
    }

    /** Entfernt eine zuvor registrierte Verbindung über ihre Rowid. */
    suspend fun remove(rowId: Long) = repo.remove(rowId)

    fun close() = db.close()
}
