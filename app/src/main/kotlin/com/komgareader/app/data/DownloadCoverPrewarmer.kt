package com.komgareader.app.data

import com.komgareader.app.di.ApplicationScope
import com.komgareader.data.cover.LocalCoverStore
import com.komgareader.data.cover.SourceCoverCache
import com.komgareader.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warms the covers of downloaded works in the background after a download, so the offline grid and
 * detail page show them **instantly** instead of going blank or rendering page 0 on the first view
 * (the slow path the user saw as "covers take long to load"). Runs right after the download, while
 * the source is still online. Mirrors [CollectionCoverPrewarmer] (for collection members), but for
 * downloaded works; server downloads (Komga & Co.) have no sync trigger of their own.
 *
 * Two layers, both idempotent:
 *  - **Server cover bytes** ([SourceCoverCache]) for each work's series + book cover, fetched via the
 *    source ([ActiveSource], Seam A). This is the cover the user saw online and the one the cover tile
 *    requests first — cached for **every format** (the gap that left CBZ/EPUB covers slow offline,
 *    since [LocalCoverStore] only app-renders PDF/CBR).
 *  - **File-rendered covers** ([LocalCoverStore]) as the offline fallback when the source cover is
 *    unreachable and the format is app-renderable.
 *
 * Non-blocking: launched on the [ApplicationScope] so it never holds up the download itself.
 */
@Singleton
class DownloadCoverPrewarmer @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    private val coverStore: LocalCoverStore,
    private val downloads: DownloadRepository,
    private val active: ActiveSource,
    private val sourceCovers: SourceCoverCache,
) {
    fun prewarm() {
        appScope.launch {
            val all = downloads.downloads.first()
            cacheServerCovers(all)
            coverStore.prewarmAndPrune(all)
        }
    }

    /** Fetch + persist each downloaded work's server cover (series + book) while still online. */
    private suspend fun cacheServerCovers(all: List<com.komgareader.domain.repository.DownloadedBook>) {
        for (req in all.coverRequests()) {
            if (sourceCovers.has(req.sourceId, req.remoteId, req.isSeries)) continue
            val source = runCatching { active.get(req.sourceId) }.getOrNull() ?: continue
            val bytes = runCatching {
                withContext(Dispatchers.IO) { source.coverBytes(req.remoteId, isSeriesCover = req.isSeries) }
            }.getOrNull() ?: continue
            sourceCovers.putIfAbsent(req.sourceId, req.remoteId, req.isSeries, bytes)
        }
    }
}
