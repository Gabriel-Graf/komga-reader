package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Persistierte Komga-Verbindung. Genau eine im MVP (null = nicht konfiguriert).
 * Authentifizierung entweder per [apiKey] oder per [username]+[password] — nie beides gleichzeitig.
 */
data class ServerConfig(
    val name: String,
    val baseUrl: String,
    val apiKey: String? = null,
    val username: String? = null,
    val password: String? = null,
)

interface ServerRepository {
    val config: Flow<ServerConfig?>
    suspend fun save(config: ServerConfig)
    suspend fun clear()
}
