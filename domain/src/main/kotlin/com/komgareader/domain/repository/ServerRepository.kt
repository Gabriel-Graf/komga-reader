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
)

interface ServerRepository {
    val config: Flow<ServerConfig?>
    suspend fun save(config: ServerConfig)
    suspend fun clear()
}
