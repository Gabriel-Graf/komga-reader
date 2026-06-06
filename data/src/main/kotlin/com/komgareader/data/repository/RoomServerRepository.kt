package com.komgareader.data.repository

import com.komgareader.data.db.ServerDao
import com.komgareader.data.db.ServerEntity
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomServerRepository(private val dao: ServerDao) : ServerRepository {
    override val config: Flow<ServerConfig?> = dao.observe().map { e ->
        e?.let { ServerConfig(it.name, it.baseUrl, it.apiKey) }
    }
    override suspend fun save(config: ServerConfig) =
        dao.save(ServerEntity(name = config.name, baseUrl = config.baseUrl, apiKey = config.apiKey))
    override suspend fun clear() = dao.clear()
}
