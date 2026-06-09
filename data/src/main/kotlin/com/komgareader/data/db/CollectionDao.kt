package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY id ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_members ORDER BY collectionId ASC, position ASC")
    fun observeMembers(): Flow<List<CollectionMemberEntity>>

    @Query("SELECT * FROM collection_sync_links WHERE collectionId = :collectionId")
    fun observeLinks(collectionId: Long): Flow<List<CollectionSyncLinkEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollection(id: Long): CollectionEntity?

    @Query("SELECT * FROM collection_members WHERE collectionId = :id ORDER BY position ASC")
    suspend fun getMembers(id: Long): List<CollectionMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(entity: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("DELETE FROM collection_members WHERE collectionId = :id")
    suspend fun clearMembers(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<CollectionMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(link: CollectionSyncLinkEntity)

    @Query("DELETE FROM collection_sync_links WHERE collectionId = :id")
    suspend fun clearLinks(id: Long)

    /** Mitglieder atomar ersetzen (kanonische Reihenfolge via position). */
    @Transaction
    suspend fun replaceMembers(collectionId: Long, members: List<CollectionMemberEntity>) {
        clearMembers(collectionId)
        insertMembers(members)
    }
}
