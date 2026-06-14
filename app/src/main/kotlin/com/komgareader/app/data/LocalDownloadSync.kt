package com.komgareader.app.data

import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.source.SourceId
import com.komgareader.source.local.LocalSource
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the local-folder source (`SourceKind.LOCAL`) into the downloads table so its works carry
 * the "downloaded" badge and read through the offline path like any download (user decision
 * 2026-06-14: local works are downloaded works).
 *
 * Reconciles, not just inserts: every local book is upserted; rows for the local source whose files
 * no longer exist are removed. When the local folder is gone, all its rows are cleared. Idempotent —
 * safe to run on every app-start / server-change / manual reload.
 *
 * The wiring layer is allowed to know the concrete [LocalSource] type (same as [SourceRegistration]).
 */
@Singleton
class LocalDownloadSync @Inject constructor(
    private val active: ActiveSource,
    private val downloads: DownloadRepository,
) {
    suspend fun sync() {
        val source = active.get(SourceId.LOCAL) as? LocalSource
        source?.refresh() // pick up files added/removed since the last scan
        val books = source?.asDownloadedBooks().orEmpty()
        val desiredIds = books.mapTo(mutableSetOf()) { it.bookRemoteId }

        // Remove stale local rows (file/folder gone) — only ours (sourceId == LOCAL), never servers'.
        downloads.downloads.first()
            .filter { it.sourceId == SourceId.LOCAL && it.bookRemoteId !in desiredIds }
            .forEach { downloads.remove(it.bookRemoteId) }

        // Upsert the current local works.
        books.forEach { downloads.put(it) }
    }
}
