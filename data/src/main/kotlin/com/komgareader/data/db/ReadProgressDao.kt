package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadProgressDao {
    @Query("SELECT * FROM read_progress")
    fun observeAll(): Flow<List<ReadProgressEntity>>

    @Query("SELECT * FROM read_progress WHERE bookRemoteId = :id")
    suspend fun get(id: String): ReadProgressEntity?

    @Query("SELECT * FROM read_progress WHERE dirty = 1")
    suspend fun dirty(): List<ReadProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: ReadProgressEntity)

    @Query("UPDATE read_progress SET dirty = 0 WHERE bookRemoteId = :id")
    suspend fun markClean(id: String)
}
