package com.komgareader.app.data

import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Die agnostische Quellen-Grenze für ViewModels: liefert Quellen als [BrowsableSource],
 *  nie als konkreten Typ. Kein ViewModel kennt KomgaSourceProvider — siehe Regel
 *  source-agnostic-integration.
 *
 *  **Multi-Source (generisch):** Es können beliebig viele Verbindungen gleichzeitig konfiguriert
 *  sein (n Komga, OPDS, später Plugin-Server, gemischt). [all] liefert alle aktiven Quellen für
 *  die Bibliotheks-Aggregation, [get] genau die zu einer `sourceId` (für ein bestimmtes Werk).
 *  [current] (erste Quelle) bleibt als Übergangs-API für noch single-source denkende Consumer.
 *
 *  `open`, damit Konsumenten im Unit-Test eine feste Quelle liefern können, ohne eine echte
 *  (netz-gebundene) Quelle aufzubauen. */
@Singleton
open class ActiveSource @Inject constructor(
    private val sources: SourceManager,
    private val servers: ServerRepository,
    private val registration: SourceRegistration,
) {
    /** Stellt alle konfigurierten Quellen registriert sicher und liefert die zur [sourceId] (oder null). */
    open suspend fun get(sourceId: Long): BrowsableSource? {
        syncAll()
        return sources.get(sourceId) as? BrowsableSource
    }

    /** Alle aktiven Quellen (für die Aggregation über mehrere Server). */
    open suspend fun all(): List<BrowsableSource> {
        val ids = syncAll()
        return ids.mapNotNull { sources.get(it) as? BrowsableSource }
    }

    /** Übergangs-API: die erste aktive Quelle (oder null). Neue Consumer nutzen [all]/[get]. */
    open suspend fun current(): BrowsableSource? = all().firstOrNull()

    /** Quelle als Schreib-Capability, oder null wenn sie keine Collections schreiben kann. */
    open suspend fun collectionSource(sourceId: Long): CollectionSyncSource? {
        syncAll()
        return sources.get(sourceId) as? CollectionSyncSource
    }

    /** Alle aktiven, schreibfähigen Collection-Quellen mit ihrer sourceId (für den Voll-Sync). */
    open suspend fun allCollectionSources(): List<Pair<Long, CollectionSyncSource>> {
        val ids = syncAll()
        return ids.mapNotNull { id -> (sources.get(id) as? CollectionSyncSource)?.let { id to it } }
    }

    private suspend fun syncAll(): Set<Long> = registration.sync(servers.configs.first())
}
