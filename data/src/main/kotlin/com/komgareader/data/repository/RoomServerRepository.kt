package com.komgareader.data.repository

import com.komgareader.data.db.ServerDao
import com.komgareader.data.db.ServerEntity
import com.komgareader.data.security.CredentialStore
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomServerRepository(
    private val dao: ServerDao,
    private val credentials: CredentialStore,
) : ServerRepository {
    override val config: Flow<ServerConfig?> = dao.observe().map { e ->
        e?.let { ServerConfig(it.name, it.baseUrl, credentials.getApiKey().orEmpty()) }
    }
    override suspend fun save(config: ServerConfig) {
        credentials.setApiKey(config.apiKey)
        dao.save(ServerEntity(name = config.name, baseUrl = config.baseUrl))
    }
    override suspend fun clear() {
        dao.clear()
        credentials.clear()
    }
}
