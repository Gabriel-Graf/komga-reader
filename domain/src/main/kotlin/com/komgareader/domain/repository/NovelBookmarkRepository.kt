package com.komgareader.domain.repository

import com.komgareader.domain.model.NovelBookmark
import kotlinx.coroutines.flow.Flow

/** Persists local-only novel bookmarks (never synced). */
interface NovelBookmarkRepository {
    fun observe(sourceId: Long, bookId: String): Flow<List<NovelBookmark>>
    suspend fun add(bookmark: NovelBookmark)
    suspend fun remove(id: Long)
    suspend fun rename(id: Long, label: String?)
}
