package com.komgareader.data.repository

import com.komgareader.data.db.SeriesAutoTypeDao
import com.komgareader.data.db.SeriesAutoTypeEntity
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.repository.SeriesAutoTypeRepository

/**
 * Room implementation of [SeriesAutoTypeRepository]. Stores the type as an enum name; an empty
 * name marks "detection ran at this version but produced no verdict" so an ambiguous series is
 * sampled once and never re-sampled (the row still carries [detectorVersion]). [get] maps the
 * empty marker — and any unparseable name — back to `null` (no suggestion).
 */
class RoomSeriesAutoTypeRepository(private val dao: SeriesAutoTypeDao) : SeriesAutoTypeRepository {

    override suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType? =
        dao.get(sourceId, seriesRemoteId)?.let { runCatching { ContentType.valueOf(it.contentType) }.getOrNull() }

    override suspend fun detectorVersion(sourceId: Long, seriesRemoteId: String): Int? =
        dao.get(sourceId, seriesRemoteId)?.detectorVersion

    override suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?, detectorVersion: Int) {
        // Always persist a row — even for a null verdict — so the detector records that it ran and
        // does not re-sample an ambiguous series on every open. Empty name = "ran, no verdict".
        dao.put(SeriesAutoTypeEntity(sourceId, seriesRemoteId, type?.name ?: "", detectorVersion))
    }
}
