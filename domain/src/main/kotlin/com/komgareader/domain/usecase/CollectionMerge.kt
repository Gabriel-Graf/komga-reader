package com.komgareader.domain.usecase

import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus

/** Mitglieder nach Quelle gruppieren, Reihenfolge je Quelle erhalten. */
fun groupBySource(members: List<CollectionMember>): Map<Long, List<CollectionMember>> =
    members.groupBy { it.sourceId }

/**
 * Merge: kanonische App-Liste mit den von sync-fähigen Quellen gemeldeten Subsets abgleichen.
 * - Mitglieder einer Quelle, die im Map vorkommt, werden auf deren gemeldete remoteIds gefiltert
 *   (entfernte verschwinden), in kanonischer Reihenfolge.
 * - Neue remoteIds einer Quelle (im Map, aber nicht kanonisch) werden hinten angehängt.
 * - Mitglieder von Quellen, die NICHT im Map stehen (nicht sync-fähig), bleiben unangetastet.
 */
fun mergeSubsets(
    canonical: List<CollectionMember>,
    perSourceRemoteIds: Map<Long, List<String>>,
    titleFor: (sourceId: Long, remoteId: String) -> String,
): List<CollectionMember> {
    val result = mutableListOf<CollectionMember>()
    val seen = mutableSetOf<Pair<Long, String>>()
    for (member in canonical) {
        val reported = perSourceRemoteIds[member.sourceId]
        if (reported == null) {
            result += member
            seen += member.sourceId to member.remoteId
        } else if (member.remoteId in reported) {
            result += member
            seen += member.sourceId to member.remoteId
        }
    }
    for ((sourceId, remoteIds) in perSourceRemoteIds) {
        for (remoteId in remoteIds) {
            if ((sourceId to remoteId) !in seen) {
                result += CollectionMember(sourceId, remoteId, titleFor(sourceId, remoteId))
                seen += sourceId to remoteId
            }
        }
    }
    return result
}

/** Status pro Quelle aus Capability + dirty ableiten. */
fun deriveStatus(syncable: Boolean, canWrite: Boolean, dirty: Boolean): SyncStatus = when {
    !syncable -> SyncStatus.UNSUPPORTED
    !canWrite -> SyncStatus.FORBIDDEN
    dirty -> SyncStatus.DIRTY
    else -> SyncStatus.SYNCED
}
