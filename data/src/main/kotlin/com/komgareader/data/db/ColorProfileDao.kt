package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorProfileDao {
    /** Built-ins zuerst, dann alphabetisch. */
    @Query("SELECT * FROM color_profiles ORDER BY builtIn DESC, name ASC")
    fun observeAll(): Flow<List<ColorProfileEntity>>

    @Query("SELECT * FROM color_profiles WHERE id = :id")
    fun observeById(id: Long): Flow<ColorProfileEntity?>

    @Upsert
    suspend fun upsert(entity: ColorProfileEntity): Long

    @Query("DELETE FROM color_profiles WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: Long)

    @Query("DELETE FROM color_profiles WHERE pluginPackage = :pkg AND builtIn = 0")
    suspend fun deleteByPluginPackage(pkg: String)
}
