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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Liefert den echten Anzeigetitel zu (sourceId, kind, remoteId) oder null (→ Fallback remoteId).
     *  SERIES via BrowsableSource.seriesDetail; BOOK noch nicht (Follow-up). */
    private val titleResolver: suspend (sourceId: Long, kind: CollectionKind, remoteId: String) -> String?,
) {
    @Inject constructor(repo: CollectionRepository, active: ActiveSource) : this(
        repo,
        { id -> active.collectionSource(id) },
        { active.allCollectionSources() },
        { sourceId, kind, remoteId -> resolveMemberTitle(active, sourceId, kind, remoteId) },
    )

    private val syncMutex = Mutex()

    /** Echter Titel des Werks oder Fallback auf die remoteId (die zugleich der Sync-Link bleibt). */
    private suspend fun resolveTitle(sourceId: Long, kind: CollectionKind, remoteId: String): String =
        titleResolver(sourceId, kind, remoteId)?.takeIf { it.isNotBlank() } ?: remoteId

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
     * Voller bidirektionaler Sync (Start/Tab/Button): push + pull + Discovery + vanished.
     * Gibt am Server verschwundene (früher synchrone) Sammlungen zurück — die UI bestätigt deren
     * lokale Löschung. Discovery + Pull laufen stumm.
     */
    suspend fun fullSync(): List<VanishedCollection> = runSync(allowPush = true, collectVanished = true)

    /**
     * Server-Connect-Sync: NUR pullen (Discovery + Server-gewinnt-Overwrite). Pusht NICHT und
     * meldet KEINE vanished — beim Verbinden eines Servers werden dessen Sammlungen lediglich
     * in die App gezogen, lokale Sammlungen werden nie auf den (frisch verbundenen) Server gedrückt.
     */
    suspend fun pullOnlySync() {
        runSync(allowPush = false, collectVanished = false)
    }

    private suspend fun runSync(allowPush: Boolean, collectVanished: Boolean): List<VanishedCollection> =
        syncMutex.withLock {
            val collections = repo.collections.first()
            val links = collections.associate { it.id to repo.syncLinks(it.id).first() }
            val srcs = allSources()

            val vanished = mutableListOf<VanishedCollection>()
            for (kind in CollectionKind.values()) {
                val kindCollections = collections.filter { it.kind == kind }
                // Eine UNERREICHBARE Quelle (offline / Listen-Fehler) wird WEGGELASSEN, nicht als leer
                // eingesetzt: sonst meldet planCollectionSync alle ihre früher synchronen Sammlungen als
                // „vanished" (falsches „Server hat gelöscht", nur weil keine Verbindung). Nur eine
                // erreichbare Quelle, die wirklich `[]` liefert, zählt als echte Leere. planCollectionSync
                // überspringt Quellen, die nicht in der Map sind (`remotePerSource[sourceId] ?: continue`).
                val remotePerSource = srcs.mapNotNull { (id, src) ->
                    runCatching { src.listCollections(kind) }.getOrNull()?.let { id to it }
                }.toMap()
                val plan = planCollectionSync(
                    local = kindCollections,
                    links = links.filterKeys { id -> kindCollections.any { it.id == id } },
                    remotePerSource = remotePerSource,
                    kind = kind,
                )
                executePlan(plan, kind, allowPush = allowPush)
                if (collectVanished) vanished += plan.vanished
            }

            // Titel-Heilung: Altbestand-Mitglieder, deren Titel noch die remoteId ist (vor der
            // Titel-Auflösung entdeckt), echten Titel nachladen — ohne den Sync-Link zu verändern.
            val current = repo.collections.first()
            for (c in current) {
                if (c.members.none { it.title == it.remoteId }) continue
                val healed = c.members.map { m ->
                    if (m.title == m.remoteId) m.copy(title = resolveTitle(m.sourceId, c.kind, m.remoteId)) else m
                }
                if (healed != c.members) repo.updateMemberTitles(c.id, healed)
            }

            vanished.distinctBy { it.collectionId }
        }

    private suspend fun executePlan(plan: SyncPlan, kind: CollectionKind, allowPush: Boolean) {
        // 1) Discovery: Server-Sammlung lokal anlegen. Komgas listCollections liefert nur Member-IDs,
        //    keine Titel — den echten Anzeigetitel je Mitglied über die Quelle auflösen (Fallback: remoteId).
        for (d in plan.createLocal) {
            val newId = repo.create(d.remote.name, d.kind)
            val members = d.remote.memberRemoteIds.map { rid ->
                CollectionMember(d.sourceId, rid, resolveTitle(d.sourceId, d.kind, rid))
            }
            repo.setMembers(newId, members)   // markiert Link DIRTY/remoteId=null …
            writeLink(newId, d.sourceId, d.remote.remoteId, SyncStatus.SYNCED, dirty = false, updatedAt = d.remote.updatedAt)
        }
        // 2) Pull-Overwrite: Server-Subset gewinnt für diese Quelle. mergeSubsets.titleFor ist NICHT
        //    suspend → Titel vorab auflösen (vorhandener lokaler Titel gewinnt, sonst über die Quelle,
        //    sonst remoteId).
        for (p in plan.pullOverwrite) {
            val current = repo.get(p.collectionId) ?: continue
            val titles = p.serverMemberRemoteIds.associateWith { rid ->
                current.members.firstOrNull { it.sourceId == p.sourceId && it.remoteId == rid }?.title
                    ?: resolveTitle(p.sourceId, kind, rid)
            }
            val merged = mergeSubsets(current.members, mapOf(p.sourceId to p.serverMemberRemoteIds)) { _, rid ->
                titles[rid] ?: rid
            }
            repo.setMembers(p.collectionId, merged)   // … nullt den Link → direkt mit Server-Stand korrigieren:
            writeLink(p.collectionId, p.sourceId, p.remoteId, SyncStatus.SYNCED, dirty = false, updatedAt = p.serverUpdatedAt)
        }
        // 3) Push: lokaler Stand zum Server (nur im bidirektionalen Sync — beim reinen Connect-Pull
        //    übersprungen). Hinweis: push() stempelt den Link mit der Geräte-Uhr; liegt die Server-Uhr
        //    minimal vor, löst der nächste fullSync genau EINEN idempotenten Pull aus, der den Link auf
        //    den Server-Zeitstempel ankert (selbst-korrigierend, kein Ping-Pong).
        if (allowPush) {
            for (id in plan.pushLocal) {
                repo.get(id)?.let { push(it) }
            }
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

/** Default-Titel-Auflösung über die agnostische Quelle: SERIES via [seriesDetail], sonst null
 *  (BOOK ist Follow-up). Top-level, damit der delegierende Konstruktor sie ohne Instanz-Zugriff
 *  aus einer Lambda heraus aufrufen kann. */
private suspend fun resolveMemberTitle(
    active: ActiveSource,
    sourceId: Long,
    kind: CollectionKind,
    remoteId: String,
): String? =
    if (kind == CollectionKind.SERIES) {
        runCatching { active.get(sourceId)?.seriesDetail(remoteId)?.title }.getOrNull()
    } else {
        null
    }
