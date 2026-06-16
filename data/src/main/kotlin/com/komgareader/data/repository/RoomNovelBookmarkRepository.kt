package com.komgareader.data.repository

import com.komgareader.data.db.NovelBookmarkDao
import com.komgareader.data.db.NovelBookmarkEntity
import com.komgareader.domain.model.NovelBookmark
import com.komgareader.domain.repository.NovelBookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room implementation of [NovelBookmarkRepository]. */
class RoomNovelBookmarkRepository(private val dao: NovelBookmarkDao) : NovelBookmarkRepository {

    override fun observe(sourceId: Long, bookId: String): Flow<List<NovelBookmark>> =
        dao.observe(sourceId, bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(bookmark: NovelBookmark) {
        dao.insert(
            NovelBookmarkEntity(
                id = 0,
                sourceId = bookmark.sourceId,
                bookId = bookmark.bookId,
                xpointer = bookmark.xpointer,
                number = bookmark.number,
                label = bookmark.label,
                snippet = bookmark.snippet,
                createdAt = bookmark.createdAt,
                markerStyle = bookmark.markerStyle,
                color = bookmark.color,
            ),
        )
    }

    override suspend fun remove(id: Long) = dao.delete(id)

    override suspend fun rename(id: Long, label: String?) = dao.rename(id, label)

    override suspend fun setMarkerStyle(id: Long, style: String) = dao.setMarkerStyle(id, style)

    override suspend fun setColor(id: Long, color: Int) = dao.setColor(id, color)

    override suspend fun removeMany(ids: List<Long>) = dao.deleteMany(ids)

    override suspend fun setMarkerStyleMany(ids: List<Long>, style: String) =
        dao.setMarkerStyleMany(ids, style)

    override suspend fun setColorMany(ids: List<Long>, color: Int) = dao.setColorMany(ids, color)

    private fun NovelBookmarkEntity.toDomain() = NovelBookmark(
        id = id,
        sourceId = sourceId,
        bookId = bookId,
        xpointer = xpointer,
        number = number,
        label = label,
        snippet = snippet,
        createdAt = createdAt,
        markerStyle = markerStyle,
        color = color,
    )
}
