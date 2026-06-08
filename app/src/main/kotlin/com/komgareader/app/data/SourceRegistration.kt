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
    private var activeId: Long? = null

    /**
     * Aktiviert die Quelle aus [config] (registriert sie); null deaktiviert die bisherige.
     * Gibt die aktive sourceId zurück (null wenn keine/Aufbau fehlschlug).
     *
     * **Idempotent + atomar:** Bleibt die Config unverändert (gleiche `sourceId`, noch
     * registriert), passiert NICHTS — kein Ab-/Neu-Registrieren. Das verhindert die Race,
     * in der ein paralleler Coil-Fetcher `sources.get(id) == null` zwischen unregister und
     * register sehen würde (`current()` ruft dies bei jedem Aufruf). Beim echten Wechsel wird
     * die NEUE Quelle **vor** dem Abmelden der alten registriert — die neue id hat nie ein
     * Null-Fenster. `synchronized`, damit nebenläufige Aufrufe sich nicht verschränken.
     */
    fun activate(config: ServerConfig?): Long? = synchronized(lock) {
        if (config == null) {
            activeId?.let { sources.unregister(it) }
            activeId = null
            return@synchronized null
        }
        val source: BrowsableSource? = when (config.kind) {
            SourceKind.OPDS -> OpdsSourceFactory.create(config.name, config.baseUrl)
            else -> komgaProvider.from(config)
        }
        if (source == null) {
            activeId?.let { sources.unregister(it) }
            activeId = null
            return@synchronized null
        }
        // Unverändert + noch registriert → kein Churn (das ist der Race-Fix).
        if (source.id == activeId && sources.get(source.id) != null) {
            return@synchronized source.id
        }
        val previous = activeId
        sources.register(source)                                   // neue id zuerst → kein Null-Fenster
        if (previous != null && previous != source.id) sources.unregister(previous)
        activeId = source.id
        return@synchronized source.id
    }

    fun activeSourceId(): Long? = synchronized(lock) { activeId }
}
