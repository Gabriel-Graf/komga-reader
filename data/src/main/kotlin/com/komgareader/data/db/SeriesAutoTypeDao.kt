package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SeriesAutoTypeDao {
    @Query("SELECT * FROM series_auto_types WHERE sourceId = :sourceId AND seriesRemoteId = :seriesRemoteId")
    suspend fun get(sourceId: Long, seriesRemoteId: String): SeriesAutoTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: SeriesAutoTypeEntity)

    @Query("DELETE FROM series_auto_types WHERE sourceId = :sourceId AND seriesRemoteId = :seriesRemoteId")
    suspend fun delete(sourceId: Long, seriesRemoteId: String)
}
