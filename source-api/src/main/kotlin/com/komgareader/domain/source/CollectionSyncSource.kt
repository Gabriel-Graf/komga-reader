package com.komgareader.domain.source

import com.komgareader.domain.model.CollectionKind

/** Eine vom Server gehaltene Collection/Read-List (innerhalb EINER Quelle). */
data class RemoteCollection(
    val remoteId: String,
    val name: String,
    val memberRemoteIds: List<String>,
    val updatedAt: Long,   // UTC epoch millis (GMT), niemals zonenbehaftet
)

/**
 * Optionale Capability (Naht A): Quelle kann Nutzer-Collections server-seitig schreiben.
 * Quellen ohne Schreibpfad (OPDS, Stub) implementieren das **nicht** → die UI hält deren
 * Mitglieder rein lokal. [kind] wählt im Impl die Endpunkt-Familie (Komga: collections vs
 * readlists). Mitgliedschaft wird per Voll-Liste gesetzt (Replace-Semantik).
 */
interface CollectionSyncSource : MediaSource {
    /** Darf diese Sitzung wirklich schreiben? (Komga: Rolle ADMIN). Bei false nur lesen/lokal. */
    suspend fun canWriteCollections(): Boolean
    suspend fun listCollections(kind: CollectionKind): List<RemoteCollection>
    suspend fun createCollection(kind: CollectionKind, name: String, memberRemoteIds: List<String>): RemoteCollection
    suspend fun updateCollection(kind: CollectionKind, remoteId: String, name: String, memberRemoteIds: List<String>)
    suspend fun deleteCollection(kind: CollectionKind, remoteId: String)
}
