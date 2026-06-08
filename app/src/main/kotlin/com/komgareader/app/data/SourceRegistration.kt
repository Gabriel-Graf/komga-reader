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
    private var activeId: Long? = null

    /**
     * Aktiviert die Quelle aus [config] (registriert sie); null deaktiviert die bisherige.
     * Gibt die aktive sourceId zurück (null wenn keine/Aufbau fehlschlug).
     */
    fun activate(config: ServerConfig?): Long? {
        activeId?.let { sources.unregister(it) }
        if (config == null) {
            activeId = null
            return null
        }
        val source: BrowsableSource? = when (config.kind) {
            SourceKind.OPDS -> OpdsSourceFactory.create(config.name, config.baseUrl)
            else -> komgaProvider.from(config)
        }
        if (source == null) {
            activeId = null
            return null
        }
        sources.register(source)
        activeId = source.id
        return source.id
    }

    fun activeSourceId(): Long? = activeId
}
