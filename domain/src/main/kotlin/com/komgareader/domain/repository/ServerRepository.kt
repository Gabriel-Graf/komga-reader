package com.komgareader.domain.repository

import com.komgareader.domain.model.SourceKind
import kotlinx.coroutines.flow.Flow

/**
 * Persistierte Server-Verbindung. Genau eine im MVP (null = nicht konfiguriert).
 * Authentifizierung entweder per [apiKey] oder per [username]+[password] — nie beides gleichzeitig.
 * [kind] wählt die Quellenart (Komga-REST vs. OPDS-Feed); Default [SourceKind.KOMGA] für
 * Bestandskonfigurationen (migrations-kompatibel).
 */
data class ServerConfig(
    val name: String,
    val baseUrl: String,
    val apiKey: String? = null,
    val username: String? = null,
    val password: String? = null,
    val kind: SourceKind = SourceKind.KOMGA,
    /**
     * Plugin-spezifische Config-Werte (key→value aus dem ConfigSchema des Plugins).
     * Leer für eingebaute Quellen (Komga/OPDS). SECRET-Felder werden in :data
     * Keystore-verschlüsselt persistiert (wie apiKey).
     */
    val extras: Map<String, String> = emptyMap(),
    /**
     * Lokale Datenbank-Identität dieser Verbindung (Rowid; 0 = noch nicht gespeichert).
     * **Nicht** die `sourceId` — die leitet jede Quelle aus name/kind/url ab. Dient dem
     * Bearbeiten/Entfernen genau dieser Verbindung, wenn mehrere Server konfiguriert sind.
     */
    val id: Long = 0,
)

interface ServerRepository {
    /** Alle konfigurierten Server-Verbindungen (mehrere gleichzeitig möglich, gemischte Quellenarten). */
    val configs: Flow<List<ServerConfig>>

    /**
     * Erste konfigurierte Verbindung (oder null) — Übergangs-API, solange einzelne Consumer
     * noch single-source denken. Neue Consumer nutzen [configs].
     */
    val config: Flow<ServerConfig?>

    /** Fügt eine Verbindung hinzu (id == 0) oder aktualisiert sie (id != 0). */
    suspend fun save(config: ServerConfig)

    /** Entfernt die Verbindung mit dieser Rowid. */
    suspend fun remove(id: Long)

    /** Entfernt alle Verbindungen. */
    suspend fun clear()
}
