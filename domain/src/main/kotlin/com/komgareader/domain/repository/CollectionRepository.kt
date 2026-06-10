package com.komgareader.domain.repository

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import kotlinx.coroutines.flow.Flow

/** Pro-Quelle-Sync-Verknüpfung einer Collection (App-seitige Wahrheit über den Sync-Stand). */
data class CollectionSyncLink(
    val collectionId: Long,
    val sourceId: Long,
    val remoteCollectionId: String?,
    val status: SyncStatus,
    val dirty: Boolean,
    val updatedAt: Long,   // UTC-Epochenmillis: lokale Änderungszeit bzw. zuletzt abgeglichener Server-Stand
)

interface CollectionRepository {
    val collections: Flow<List<UserCollection>>
    fun syncLinks(collectionId: Long): Flow<List<CollectionSyncLink>>

    suspend fun create(name: String, kind: CollectionKind): Long
    suspend fun rename(collectionId: Long, name: String)
    suspend fun delete(collectionId: Long)

    /** Setzt die geordnete Mitgliederliste (kanonisch) + markiert betroffene Quellen dirty. */
    suspend fun setMembers(collectionId: Long, members: List<CollectionMember>)
    suspend fun addMember(collectionId: Long, member: CollectionMember)
    suspend fun removeMember(collectionId: Long, sourceId: Long, remoteId: String)

    /** Sync-Engine schreibt Ergebnis zurück. */
    suspend fun updateSyncLink(link: CollectionSyncLink)
    suspend fun get(collectionId: Long): UserCollection?

    /**
     * Entfernt alle lokalen Sammlungs-Daten einer Quelle (beim Abmelden eines Servers): Mitglieder
     * und Sync-Links dieser [sourceId]. Sammlungen, die dadurch komplett leer werden UND von dieser
     * Quelle berührt waren, werden ganz gelöscht; Sammlungen mit Mitgliedern anderer Quellen bleiben.
     */
    suspend fun removeSource(sourceId: Long)
}
