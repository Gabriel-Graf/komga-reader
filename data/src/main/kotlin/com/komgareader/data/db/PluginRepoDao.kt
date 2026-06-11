package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginRepoDao {
    @Query("SELECT * FROM plugin_repos ORDER BY id ASC")
    fun observeAll(): Flow<List<PluginRepoEntity>>

    @Query("SELECT * FROM plugin_repos ORDER BY id ASC")
    suspend fun getAll(): List<PluginRepoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PluginRepoEntity): Long

    @Query("UPDATE plugin_repos SET name = :name WHERE url = :url")
    suspend fun setName(url: String, name: String?)

    @Query("DELETE FROM plugin_repos WHERE id = :id")
    suspend fun delete(id: Long)
}
