package com.komgareader.domain.usecase

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.RemoteCollection
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionSyncLink

/** Eine am Server entdeckte, lokal noch fehlende Sammlung (Discovery / Pull). */
data class DiscoveredCollection(
    val sourceId: Long,
    val kind: CollectionKind,
    val remote: RemoteCollection,
)

/** Server gewinnt für genau diese (Sammlung, Quelle): deren Subset überschreibt lokal. */
data class PullOverwrite(
    val collectionId: Long,
    val sourceId: Long,
    val serverMemberRemoteIds: List<String>,
    val remoteId: String,        // gematchte Server-remoteId → in den Link zurückschreiben
    val serverUpdatedAt: Long,   // Server-lastModified (UTC) → nach dem Pull in den Link schreiben
)

/** Lokal vorhanden, war schon synced, am Server jetzt weg → Nutzer-Bestätigung nötig. */
data class VanishedCollection(
    val collectionId: Long,
    val name: String,
)

data class SyncPlan(
    val createLocal: List<DiscoveredCollection>,
    val pushLocal: List<Long>,
    val pullOverwrite: List<PullOverwrite>,
    val vanished: List<VanishedCollection>,
)

/**
 * Reiner Sync-Planer (Last-Write-Wins per UTC-Zeitstempel). Pro (Sammlung, Quelle)-Link:
 *  - beide vorhanden: Server `updatedAt` > lokaler `link.updatedAt` → pull, sonst push.
 *  - nur Server (kein lokaler Match per remoteId/Name): createLocal (Discovery).
 *  - nur lokal, nie synced: push (am Server anlegen).
 *  - nur lokal, war synced, am Server weg: vanished.
 *
 * @param remotePerSource pro Quelle die Server-Liste EINER kind-Familie. Die Shell ruft je kind
 *  getrennt auf; daher trägt [DiscoveredCollection.kind] hier das Default SERIES und wird von der
 *  Shell beim Ausführen mit dem tatsächlichen kind überschrieben.
 */
fun planCollectionSync(
    local: List<UserCollection>,
    links: Map<Long, List<CollectionSyncLink>>,
    remotePerSource: Map<Long, List<RemoteCollection>>,
): SyncPlan {
    val createLocal = mutableListOf<DiscoveredCollection>()
    val pushLocal = mutableSetOf<Long>()
    val pullOverwrite = mutableListOf<PullOverwrite>()
    val vanished = mutableListOf<VanishedCollection>()

    // Protokolliert welche Server-remoteIds pro Quelle bereits einem lokalen Match zugeordnet wurden,
    // damit sie nicht zusätzlich als Discovery-Kandidaten auftauchen.
    val matchedRemoteIds = mutableMapOf<Long, MutableSet<String>>()

    for (collection in local) {
        val colLinks = links[collection.id].orEmpty()
        // Alle Quellen, die für diese Collection relevant sind (aus Mitgliedern und vorhandenen Links)
        val sourceIds = (collection.members.map { it.sourceId } + colLinks.map { it.sourceId }).toSet()
        for (sourceId in sourceIds) {
            val remotes = remotePerSource[sourceId] ?: continue
            val link = colLinks.firstOrNull { it.sourceId == sourceId }

            // Zuerst per bekannter remoteId matchen, sonst per Name (Erstverbindung ohne remoteId)
            val match = remotes.firstOrNull { it.remoteId == link?.remoteCollectionId }
                ?: remotes.firstOrNull { it.name == collection.name }

            if (match != null) {
                matchedRemoteIds.getOrPut(sourceId) { mutableSetOf() } += match.remoteId
                val localUpdated = link?.updatedAt ?: Long.MIN_VALUE
                if (match.updatedAt > localUpdated) {
                    // Server ist neuer → pull
                    pullOverwrite += PullOverwrite(
                        collectionId = collection.id,
                        sourceId = sourceId,
                        serverMemberRemoteIds = match.memberRemoteIds,
                        remoteId = match.remoteId,
                        serverUpdatedAt = match.updatedAt,
                    )
                } else {
                    // Lokal ist neuer oder gleichwertig (Tie-Break: lokal gewinnt)
                    pushLocal += collection.id
                }
            } else {
                if (link?.remoteCollectionId != null) {
                    // War synced, am Server verschwunden → Nutzer muss entscheiden
                    vanished += VanishedCollection(collection.id, collection.name)
                } else {
                    // Noch nie am Server angelegt → jetzt pushen
                    pushLocal += collection.id
                }
            }
        }
    }

    // Alle Server-Sammlungen ohne lokalen Match → neu lokal anlegen (Discovery)
    for ((sourceId, remotes) in remotePerSource) {
        val matched = matchedRemoteIds[sourceId].orEmpty()
        for (remote in remotes) {
            if (remote.remoteId !in matched) {
                createLocal += DiscoveredCollection(sourceId, CollectionKind.SERIES, remote)
            }
        }
    }

    return SyncPlan(
        createLocal = createLocal,
        pushLocal = pushLocal.toList(),
        pullOverwrite = pullOverwrite,
        vanished = vanished.distinctBy { it.collectionId },
    )
}
