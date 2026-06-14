package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO für den Roman-Lesefortschritt (Xpointer-Anker + grober Anteil). Offline-first:
 * [upsert] schreibt mit `dirty = true`, [dirtyEntries] liefert den Sync-Rückstand für
 * den %-Push zu Komga, [markClean] quittiert einen erfolgreichen Push.
 */
@Dao
interface NovelProgressDao {

    @Query("SELECT * FROM novel_progress")
    fun observeAll(): Flow<List<NovelProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: NovelProgressEntity)

    @Query("SELECT * FROM novel_progress WHERE sourceId = :sourceId AND bookId = :bookId")
    suspend fun get(sourceId: Long, bookId: String): NovelProgressEntity?

    @Query("SELECT * FROM novel_progress WHERE dirty = 1")
    suspend fun dirtyEntries(): List<NovelProgressEntity>

    @Query("UPDATE novel_progress SET dirty = 0 WHERE sourceId = :sourceId AND bookId = :bookId")
    suspend fun markClean(sourceId: Long, bookId: String)

    @Query("SELECT * FROM novel_progress")
    fun observeAll(): Flow<List<NovelProgressEntity>>
}
