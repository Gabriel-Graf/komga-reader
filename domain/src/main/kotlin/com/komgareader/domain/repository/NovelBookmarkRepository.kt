package com.komgareader.domain.repository

import com.komgareader.domain.model.NovelBookmark
import kotlinx.coroutines.flow.Flow

/** Persists local-only novel bookmarks (never synced). */
interface NovelBookmarkRepository {
    fun observe(sourceId: Long, bookId: String): Flow<List<NovelBookmark>>
    suspend fun add(bookmark: NovelBookmark)
    suspend fun remove(id: Long)
    suspend fun rename(id: Long, label: String?)

    /** Set a single bookmark's per-bookmark marker style ([com.komgareader.domain.model.BookmarkMarkerStyle] name). */
    suspend fun setMarkerStyle(id: Long, style: String)

    /** Set a single bookmark's marker content colour (ARGB). */
    suspend fun setColor(id: Long, color: Int)

    /** Delete several bookmarks in one transaction. */
    suspend fun removeMany(ids: List<Long>)

    /** Apply one marker style to several bookmarks in one transaction. */
    suspend fun setMarkerStyleMany(ids: List<Long>, style: String)

    /** Apply one colour to several bookmarks in one transaction. */
    suspend fun setColorMany(ids: List<Long>, color: Int)
}
