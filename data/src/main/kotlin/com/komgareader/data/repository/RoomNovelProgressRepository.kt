package com.komgareader.data.repository

import com.komgareader.data.db.NovelProgressDao
import com.komgareader.data.db.NovelProgressEntity
import com.komgareader.domain.repository.NovelProgress
import com.komgareader.domain.repository.NovelProgressRepository

/** Room-Implementierung von [NovelProgressRepository]. */
class RoomNovelProgressRepository(private val dao: NovelProgressDao) : NovelProgressRepository {

    override suspend fun save(sourceId: Long, bookId: String, anchor: String, fraction: Float) {
        dao.upsert(
            NovelProgressEntity(
                sourceId = sourceId,
                bookId = bookId,
                anchor = anchor,
                fraction = fraction.coerceIn(0f, 1f),
                dirty = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun get(sourceId: Long, bookId: String): NovelProgress? =
        dao.get(sourceId, bookId)?.toDomain()

    override suspend fun dirty(): List<NovelProgress> = dao.dirtyEntries().map { it.toDomain() }

    override suspend fun markSynced(sourceId: Long, bookId: String) = dao.markClean(sourceId, bookId)

    private fun NovelProgressEntity.toDomain() = NovelProgress(
        sourceId = sourceId,
        bookId = bookId,
        anchor = anchor,
        fraction = fraction,
        dirty = dirty,
        updatedAt = updatedAt,
    )
}
