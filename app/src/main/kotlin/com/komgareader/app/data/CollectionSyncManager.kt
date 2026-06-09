package com.komgareader.app.data

import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.CollectionSyncLink
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.usecase.groupBySource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestriert den server-agnostischen Teil-Sync einer Collection: pro Quelle das Subset
 * (Replace-Semantik) upserten, Status pro Quelle in den Repository-Link schreiben. Hält
 * KEINE HTTP-Details (die liegen im Quellen-Impl); Quellen-Auflösung über [resolver]
 * (in Prod = ActiveSource::collectionSource).
 */
@Singleton
class CollectionSyncManager(
    private val repo: CollectionRepository,
    private val resolver: suspend (sourceId: Long) -> CollectionSyncSource?,
) {
    @Inject constructor(repo: CollectionRepository, active: ActiveSource) :
        this(repo, { id -> active.collectionSource(id) })

    /** Pusht die kanonische Collection in alle betroffenen Quellen (best-effort, pro Quelle). */
    suspend fun push(collection: UserCollection) {
        val perSource = groupBySource(collection.members)
        for ((sourceId, members) in perSource) {
            val source = resolver(sourceId)
            if (source == null) {
                writeLink(collection.id, sourceId, null, SyncStatus.UNSUPPORTED, dirty = true)
                continue
            }
            if (!source.canWriteCollections()) {
                writeLink(collection.id, sourceId, null, SyncStatus.FORBIDDEN, dirty = true)
                continue
            }
            val remoteIds = members.map { it.remoteId }
            val result = runCatching {
                val adopt = source.listCollections(collection.kind).firstOrNull { it.name == collection.name }
                if (adopt != null) {
                    source.updateCollection(collection.kind, adopt.remoteId, collection.name, remoteIds)
                    adopt.remoteId
                } else {
                    source.createCollection(collection.kind, collection.name, remoteIds).remoteId
                }
            }
            result.fold(
                onSuccess = { remoteId -> writeLink(collection.id, sourceId, remoteId, SyncStatus.SYNCED, dirty = false) },
                onFailure = { writeLink(collection.id, sourceId, null, SyncStatus.FORBIDDEN, dirty = true) },
            )
        }
    }

    private suspend fun writeLink(collectionId: Long, sourceId: Long, remoteId: String?, status: SyncStatus, dirty: Boolean) {
        repo.updateSyncLink(CollectionSyncLink(collectionId, sourceId, remoteId, status, dirty))
    }
}
