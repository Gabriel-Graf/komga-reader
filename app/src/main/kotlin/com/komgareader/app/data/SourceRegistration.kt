package com.komgareader.app.data

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import com.komgareader.source.opds.OpdsSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registriert die aktive Quelle aus der [ServerConfig] im [SourceManager] — Komga oder OPDS,
 * je nach [ServerConfig.kind]. Die konkreten Quellen-Typen (KomgaSourceProvider, OpdsSourceFactory)
 * leben NUR hier in der Wiring-Schicht; ViewModels sehen nur `BrowsableSource` (über ActiveSource).
 */
@Singleton
class SourceRegistration @Inject constructor(
    private val sources: SourceManager,
    private val komgaProvider: KomgaSourceProvider,
) {
    private val lock = Any()
    private var activeIds: Set<Long> = emptySet()

    private fun build(config: ServerConfig): BrowsableSource? = when (config.kind) {
        SourceKind.OPDS -> OpdsSourceFactory.create(config.name, config.baseUrl)
        else -> komgaProvider.from(config)
    }

    /**
     * Registriert **genau** die Quellen aus [configs] im [SourceManager] — beliebig viele,
     * gemischte Quellenarten (n Komga, OPDS, später Plugin-Server). Gibt die aktiven `sourceId`s
     * zurück.
     *
     * **Idempotent + atomar + ohne Churn:** Schon registrierte (gleiche `sourceId`) bleiben
     * unangetastet — neu hinzugekommene werden registriert, nicht mehr gelistete abgemeldet.
     * Das verhindert die Race, in der ein paralleler Coil-Fetcher `sources.get(id) == null` sähe.
     * `synchronized`, damit nebenläufige Aufrufe sich nicht verschränken.
     */
    fun sync(configs: List<ServerConfig>): Set<Long> = synchronized(lock) {
        val built = configs.mapNotNull { build(it) }
        val desiredIds = built.map { it.id }.toSet()
        built.forEach { if (sources.get(it.id) == null) sources.register(it) } // nur Neue → kein Churn
        (activeIds - desiredIds).forEach { sources.unregister(it) }            // Entfernte abmelden
        activeIds = desiredIds
        desiredIds
    }

    /** Übergangs-API: genau eine Quelle (null = keine). Delegiert an [sync]. */
    fun activate(config: ServerConfig?): Long? =
        sync(listOfNotNull(config)).firstOrNull()

    fun activeSourceIds(): Set<Long> = synchronized(lock) { activeIds }

    fun activeSourceId(): Long? = activeSourceIds().firstOrNull()
}
