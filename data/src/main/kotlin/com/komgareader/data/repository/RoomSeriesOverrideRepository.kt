package com.komgareader.data.repository

import com.komgareader.data.db.SeriesOverrideDao
import com.komgareader.data.db.SeriesOverrideEntity
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.repository.SeriesOverrideRepository

/** Room-Implementierung von [SeriesOverrideRepository]. Speichert den Typ als Enum-Namen. */
class RoomSeriesOverrideRepository(private val dao: SeriesOverrideDao) : SeriesOverrideRepository {

    override suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType? =
        dao.get(sourceId, seriesRemoteId)?.let { name ->
            runCatching { ContentType.valueOf(name) }.getOrNull()
        }

    override suspend fun all(sourceId: Long): Map<String, ContentType> =
        dao.getAll(sourceId).mapNotNull { entity ->
            runCatching { ContentType.valueOf(entity.contentType) }.getOrNull()
                ?.let { entity.seriesRemoteId to it }
        }.toMap()

    override suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?) {
        if (type == null) {
            dao.delete(sourceId, seriesRemoteId)
        } else {
            dao.put(SeriesOverrideEntity(sourceId, seriesRemoteId, type.name))
        }
    }
}
