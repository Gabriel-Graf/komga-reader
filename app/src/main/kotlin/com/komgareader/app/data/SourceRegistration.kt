package com.komgareader.app.data

import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registriert die aktive Quelle (heute: eine Komga-Verbindung) im [SourceManager].
 * Der konkrete Quellen-Typ (KomgaSourceProvider) lebt NUR hier in der Wiring-Schicht.
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
        val source = komgaProvider.from(config) ?: run {
            activeId = null
            return null
        }
        sources.register(source)
        activeId = source.id
        return source.id
    }

    fun activeSourceId(): Long? = activeId
}
