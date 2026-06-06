package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/** Persistierte Komga-Verbindung. Genau eine im MVP (null = nicht konfiguriert). */
data class ServerConfig(val name: String, val baseUrl: String, val apiKey: String)

interface ServerRepository {
    val config: Flow<ServerConfig?>
    suspend fun save(config: ServerConfig)
    suspend fun clear()
}
