package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** DAO for the append-only reading-session log. Time aggregates are computed from [observeAll]. */
@Dao
interface ReadingSessionDao {
    @Insert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_session")
    fun observeAll(): Flow<List<ReadingSessionEntity>>
}
