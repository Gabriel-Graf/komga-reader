package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    /** Alle konfigurierten Server-Verbindungen, stabil nach Rowid sortiert. */
    @Query("SELECT * FROM server ORDER BY id")
    fun observeAll(): Flow<List<ServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ServerEntity)

    /** Höchste vergebene Rowid (0 wenn leer) — für die Vergabe der nächsten Id. */
    @Query("SELECT COALESCE(MAX(id), 0) FROM server")
    suspend fun maxId(): Long

    @Query("DELETE FROM server WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM server")
    suspend fun clear()
}
