package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SeriesOverrideDao {
    @Query(
        "SELECT contentType FROM series_overrides WHERE sourceId = :sourceId AND seriesRemoteId = :seriesRemoteId",
    )
    suspend fun get(sourceId: Long, seriesRemoteId: String): String?

    @Query("SELECT * FROM series_overrides WHERE sourceId = :sourceId")
    suspend fun getAll(sourceId: Long): List<SeriesOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: SeriesOverrideEntity)

    @Query("DELETE FROM series_overrides WHERE sourceId = :sourceId AND seriesRemoteId = :seriesRemoteId")
    suspend fun delete(sourceId: Long, seriesRemoteId: String)
}
