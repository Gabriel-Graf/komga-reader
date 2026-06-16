package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for local-only novel bookmarks. [observe] streams the bookmarks of one book
 * (ordered by their stable [NovelBookmarkEntity.number]); never synced to a server.
 */
@Dao
interface NovelBookmarkDao {

    @Insert
    suspend fun insert(entry: NovelBookmarkEntity): Long

    @Query("SELECT * FROM novel_bookmark WHERE sourceId = :sourceId AND bookId = :bookId ORDER BY number")
    fun observe(sourceId: Long, bookId: String): Flow<List<NovelBookmarkEntity>>

    @Query("DELETE FROM novel_bookmark WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE novel_bookmark SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String?)

    @Query("UPDATE novel_bookmark SET markerStyle = :style WHERE id = :id")
    suspend fun setMarkerStyle(id: Long, style: String)

    @Query("UPDATE novel_bookmark SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Int)

    @Query("DELETE FROM novel_bookmark WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)

    @Query("UPDATE novel_bookmark SET markerStyle = :style WHERE id IN (:ids)")
    suspend fun setMarkerStyleMany(ids: List<Long>, style: String)

    @Query("UPDATE novel_bookmark SET color = :color WHERE id IN (:ids)")
    suspend fun setColorMany(ids: List<Long>, color: Int)
}
