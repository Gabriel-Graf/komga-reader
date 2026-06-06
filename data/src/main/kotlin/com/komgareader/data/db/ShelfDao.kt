package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    @Query("SELECT * FROM shelves ORDER BY id ASC")
    fun observeAll(): Flow<List<ShelfEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ShelfEntity): Long

    @Query("DELETE FROM shelves WHERE id = :id")
    suspend fun deleteById(id: Long)
}
