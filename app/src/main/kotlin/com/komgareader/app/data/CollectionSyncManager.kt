package com.komgareader.app.data

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.CollectionSyncLink
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.usecase.SyncPlan
import com.komgareader.domain.usecase.VanishedCollection
import com.komgareader.domain.usecase.groupBySource
import com.komgareader.domain.usecase.mergeSubsets
import com.komgareader.domain.usecase.planCollectionSync
import kotlinx.coroutines.flow.first
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
    private val allSources: suspend () -> List<Pair<Long, CollectionSyncSource>>,
) {
    @Inject constructor(repo: CollectionRepository, active: ActiveSource) :
        this(repo, { id -> active.collectionSource(id) }, { active.allCollectionSources() })

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

    /**
     * Voller bidirektionaler Sync: Server-Sammlungen je Quelle und kind listen, Plan rechnen, ausführen.
     * Gibt am Server verschwundene (früher synchrone) Sammlungen zurück — die UI bestätigt deren
     * lokale Löschung. Discovery + Pull laufen stumm.
     */
    suspend fun fullSync(): List<VanishedCollection> {
        val collections = repo.collections.first()
        val links = collections.associate { it.id to repo.syncLinks(it.id).first() }
        val srcs = allSources()

        val vanished = mutableListOf<VanishedCollection>()
        for (kind in CollectionKind.values()) {
            val kindCollections = collections.filter { it.kind == kind }
            val remotePerSource = srcs.associate { (id, src) ->
                id to runCatching { src.listCollections(kind) }.getOrDefault(emptyList())
            }
            val plan = planCollectionSync(
                local = kindCollections,
                links = links.filterKeys { id -> kindCollections.any { it.id == id } },
                remotePerSource = remotePerSource,
                kind = kind,
            )
            executePlan(plan)
            vanished += plan.vanished
        }
        return vanished.distinctBy { it.collectionId }
    }

    private suspend fun executePlan(plan: SyncPlan) {
        // 1) Discovery: Server-Sammlung lokal anlegen.
        for (d in plan.createLocal) {
            val newId = repo.create(d.remote.name, d.kind)
            val members = d.remote.memberRemoteIds.map { CollectionMember(d.sourceId, it, it) }
            repo.setMembers(newId, members)   // markiert Link DIRTY/remoteId=null …
            writeLink(newId, d.sourceId, d.remote.remoteId, SyncStatus.SYNCED, dirty = false, updatedAt = d.remote.updatedAt)
        }
        // 2) Pull-Overwrite: Server-Subset gewinnt für diese Quelle.
        for (p in plan.pullOverwrite) {
            val current = repo.get(p.collectionId) ?: continue
            val merged = mergeSubsets(current.members, mapOf(p.sourceId to p.serverMemberRemoteIds)) { _, rid ->
                current.members.firstOrNull { it.sourceId == p.sourceId && it.remoteId == rid }?.title ?: rid
            }
            repo.setMembers(p.collectionId, merged)   // … nullt den Link → direkt mit Server-Stand korrigieren:
            writeLink(p.collectionId, p.sourceId, p.remoteId, SyncStatus.SYNCED, dirty = false, updatedAt = p.serverUpdatedAt)
        }
        // 3) Push: lokaler Stand zum Server (best-effort, unverändert).
        for (id in plan.pushLocal) {
            repo.get(id)?.let { push(it) }
        }
        // vanished: NICHT automatisch löschen — die UI bestätigt.
    }

    /** Löscht die Server-Collections in allen sync-fähigen Quellen (best-effort). */
    suspend fun deleteEverywhere(collection: UserCollection) {
        for (sourceId in collection.members.map { it.sourceId }.toSet()) {
            val source = resolver(sourceId) ?: continue
            if (!source.canWriteCollections()) continue
            runCatching {
                source.listCollections(collection.kind).firstOrNull { it.name == collection.name }
                    ?.let { source.deleteCollection(collection.kind, it.remoteId) }
            }
        }
    }

    private suspend fun writeLink(
        collectionId: Long,
        sourceId: Long,
        remoteId: String?,
        status: SyncStatus,
        dirty: Boolean,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        repo.updateSyncLink(CollectionSyncLink(collectionId, sourceId, remoteId, status, dirty, updatedAt))
    }
}
