package com.komgareader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
)

@Entity(
    tableName = "collection_members",
    indices = [Index("collectionId")],
)
data class CollectionMemberEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val collectionId: Long,
    val sourceId: Long,
    val remoteId: String,
    val title: String,
    val position: Int,
)

@Entity(
    tableName = "collection_sync_links",
    primaryKeys = ["collectionId", "sourceId"],
)
data class CollectionSyncLinkEntity(
    val collectionId: Long,
    val sourceId: Long,
    val remoteCollectionId: String?,
    val status: String,
    val dirty: Boolean,
    val updatedAt: Long,
)
