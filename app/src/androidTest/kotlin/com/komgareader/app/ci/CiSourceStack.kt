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
import com.komgareader.plugin.host.PluginHost

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
    // PluginHost wird vom SourceRegistration-Vertrag verlangt; in den Seam-Tests nicht ausgeübt
    // (keine PLUGIN-Quelle registriert) — nur konstruiert, damit die Verdrahtung wie in Produktion steht.
    private val registration = SourceRegistration(sources, KomgaSourceProvider(), PluginHost(ctx), ctx)

    val activeSource = ActiveSource(sources, repo, registration)

    // Collection-Sync-Verdrahtung (identisch zu CollectionSyncManager-Produktion / CollectionSyncLiveTest).
    val collectionRepo = com.komgareader.data.repository.RoomCollectionRepository(db.collectionDao())
    val collectionSyncManager = com.komgareader.app.data.CollectionSyncManager(
        collectionRepo,
        resolver = { id -> activeSource.collectionSource(id) },
        allSources = { activeSource.allCollectionSources() },
        titleResolver = { sourceId, kind, remoteId ->
            if (kind == com.komgareader.domain.model.CollectionKind.SERIES) {
                runCatching { activeSource.get(sourceId)?.seriesDetail(remoteId)?.title }.getOrNull()
            } else {
                null
            }
        },
    )

    /** Persistiert die gegebenen Server-Konfigurationen (wie der echte Settings-Flow). */
    suspend fun register(vararg configs: ServerConfig) {
        configs.forEach { repo.save(it) }
    }

    /** Entfernt eine zuvor registrierte Verbindung über ihre Rowid. */
    suspend fun remove(rowId: Long) = repo.remove(rowId)

    fun close() = db.close()
}
