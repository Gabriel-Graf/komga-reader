package com.komgareader.app.data

import com.komgareader.app.data.coil.SourceCover
import com.komgareader.data.cover.LocalCoverStore
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.source.SourceId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces a cover for LOCAL works whose format the **renderer-free** `:source-local` cannot extract.
 * `LocalSource.coverBytes` only zips out the first image of a **CBZ**; PDF/EPUB/CBR need a render
 * engine, which by module rule lives only in the app layer ([DocumentFactory]/MuPDF), not in
 * `:source-local`. So the cover for those formats is rendered here, as a fallback in the Coil cover
 * path ([com.komgareader.app.data.coil.SourceCoverFetcher]).
 *
 * Returns `null` for non-local works, for CBZ (already covered by the source), or on any failure —
 * the caller then keeps the (empty) primary bytes (no cover, unchanged behaviour for those cases).
 */
@Singleton
class LocalCoverRenderer @Inject constructor(
    private val downloads: DownloadRepository,
    private val coverStore: LocalCoverStore,
) {
    suspend fun render(model: SourceCover): ByteArray? {
        if (model.sourceId != SourceId.LOCAL) return null
        val local = downloads.downloads.first().filter { it.sourceId == SourceId.LOCAL }
        // Series cover = first book of that series; book cover = that book.
        val book = if (model.isSeries) {
            local.firstOrNull { it.seriesRemoteId == model.remoteId }
        } else {
            local.firstOrNull { it.bookRemoteId == model.remoteId }
        } ?: return null
        // Reads the precomputed cover if present, renders + persists on a cold miss (CBZ → null,
        // already covered by LocalSource.coverBytes). See [LocalCoverStore].
        return coverStore.get(book)
    }
}
