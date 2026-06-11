package com.komgareader.app.data

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import com.komgareader.plugin.host.PluginHost
import com.komgareader.source.opds.OpdsSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registriert die aktive Quelle aus der [ServerConfig] im [SourceManager] — Komga, OPDS oder
 * Plugin-APK, je nach [ServerConfig.kind]. Die konkreten Quellen-Typen (KomgaSourceProvider,
 * OpdsSourceFactory, PluginHost) leben NUR hier in der Wiring-Schicht; ViewModels sehen nur
 * `BrowsableSource` (über ActiveSource). Naht A gewahrt.
 */
@Singleton
class SourceRegistration @Inject constructor(
    private val sources: SourceManager,
    private val komgaProvider: KomgaSourceProvider,
    private val pluginHost: PluginHost,
) {
    private val lock = Any()
    private var activeIds: Set<Long> = emptySet()

    private fun build(config: ServerConfig): BrowsableSource? = when (config.kind) {
        SourceKind.OPDS -> OpdsSourceFactory.create(
            name = config.name,
            catalogUrl = config.baseUrl,
            username = config.username,
            password = config.password,
        )
        SourceKind.PLUGIN -> {
            // Reservierte Wiring-Keys: __pkg/__entry/__sig steuern den Classloader-Pfad und
            // werden NICHT ans Plugin weitergereicht — nur die echten Konfigurations-Einträge.
            val pkg = config.extras["__pkg"] ?: return null
            val entry = config.extras["__entry"] ?: return null
            val sig = config.extras["__sig"] ?: return null // ohne Pin nicht laden (TOFU)
            val pluginConfig = config.extras.filterKeys { !it.startsWith("__") }
            pluginHost.sourceFor(pkg, entry, sig, pluginConfig)
        }
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

    /** Reine Abbildung Config → sourceId (ohne Registrierung) — für Cleanup beim Server-Entfernen. */
    fun sourceIdOf(config: ServerConfig): Long? = build(config)?.id

    /** Übergangs-API: genau eine Quelle (null = keine). Delegiert an [sync]. */
    fun activate(config: ServerConfig?): Long? =
        sync(listOfNotNull(config)).firstOrNull()

    fun activeSourceIds(): Set<Long> = synchronized(lock) { activeIds }

    fun activeSourceId(): Long? = activeSourceIds().firstOrNull()
}
