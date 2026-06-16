package com.komgareader.data.repository

import com.komgareader.data.db.SeriesAutoTypeDao
import com.komgareader.data.db.SeriesAutoTypeEntity
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.repository.SeriesAutoTypeRepository

/** Room implementation of [SeriesAutoTypeRepository]. Stores the type as an enum name. */
class RoomSeriesAutoTypeRepository(private val dao: SeriesAutoTypeDao) : SeriesAutoTypeRepository {

    override suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType? =
        dao.get(sourceId, seriesRemoteId)?.let { runCatching { ContentType.valueOf(it.contentType) }.getOrNull() }

    override suspend fun detectorVersion(sourceId: Long, seriesRemoteId: String): Int? =
        dao.get(sourceId, seriesRemoteId)?.detectorVersion

    override suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?, detectorVersion: Int) {
        if (type == null) {
            dao.delete(sourceId, seriesRemoteId)
        } else {
            dao.put(SeriesAutoTypeEntity(sourceId, seriesRemoteId, type.name, detectorVersion))
        }
    }
}
