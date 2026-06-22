package com.komgareader.app.data

import com.komgareader.app.di.ApplicationScope
import com.komgareader.data.cover.SourceCoverCache
import com.komgareader.data.cover.sourceCoverKey
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.repository.CollectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the cover bytes of every collection member while online, so the collections-tab collage and
 * the collection-detail grid still show covers offline. Collection members are series that need not be
 * downloaded, so their server cover is blank without network and [LocalCoverStore] (downloaded files
 * only) can't help — this fills the gap. Mirrors [DownloadCoverPrewarmer], but persists the source's
 * own [com.komgareader.domain.source.BrowsableSource.coverBytes] (Seam A) keyed by the member.
 *
 * Source-agnostic: resolves each member's source via [ActiveSource] (no Komga knowledge). Non-blocking
 * (launched on [ApplicationScope]); idempotent — already-cached covers are skipped, only cold ones fetch.
 * Prunes covers of members no longer in any collection.
 */
@Singleton
class CollectionCoverPrewarmer @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    private val collections: CollectionRepository,
    private val active: ActiveSource,
    private val cache: SourceCoverCache,
) {
    fun prewarm() {
        appScope.launch { run() }
    }

    private suspend fun run() {
        val members = collections.collections.first()
            .flatMap { col -> col.members.map { it to (col.kind == CollectionKind.SERIES) } }
            .distinctBy { (m, isSeries) -> Triple(m.sourceId, m.remoteId, isSeries) }
        // Prune to the covers we still want (all current members), then fill the cold ones.
        cache.keepOnly(members.mapTo(mutableSetOf()) { (m, isSeries) -> sourceCoverKey(m.sourceId, m.remoteId, isSeries) })
        for ((member, isSeries) in members) {
            if (cache.has(member.sourceId, member.remoteId, isSeries)) continue
            val source = runCatching { active.get(member.sourceId) }.getOrNull() ?: continue
            val bytes = runCatching {
                withContext(Dispatchers.IO) { source.coverBytes(member.remoteId, isSeriesCover = isSeries) }
            }.getOrNull() ?: continue
            cache.putIfAbsent(member.sourceId, member.remoteId, isSeries, bytes)
        }
    }
}
