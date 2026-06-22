package com.komgareader.app.data

import com.komgareader.app.di.ApplicationScope
import com.komgareader.data.cover.LocalCoverStore
import com.komgareader.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-renders + persists the covers of downloaded works in the background after a download, so the
 * offline detail page loads them instantly instead of rendering page 0 on the first view (the slow
 * path the user sees as "covers take long to load"). Mirrors the LOCAL-source prewarm in
 * [LocalDownloadSync], but for server downloads (Komga & Co.), which have no sync trigger.
 *
 * Non-blocking: launched on the [ApplicationScope] so it never holds up the download itself. The
 * underlying [LocalCoverStore.prewarmAndPrune] is idempotent — already-cached covers are a fast
 * cache hit, only the new ones render.
 */
@Singleton
class DownloadCoverPrewarmer @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    private val coverStore: LocalCoverStore,
    private val downloads: DownloadRepository,
) {
    fun prewarm() {
        appScope.launch { coverStore.prewarmAndPrune(downloads.downloads.first()) }
    }
}
