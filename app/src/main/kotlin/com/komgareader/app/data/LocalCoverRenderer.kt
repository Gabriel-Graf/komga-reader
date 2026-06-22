package com.komgareader.app.data

import com.komgareader.app.data.coil.SourceCover
import com.komgareader.data.cover.LocalCoverStore
import com.komgareader.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces a cover for a downloaded work from its local file, as a fallback in the Coil cover path
 * ([com.komgareader.app.data.coil.SourceCoverFetcher]) when the source's own `coverBytes` come back
 * empty. Two cases hit this:
 *  - a **renderer-free LOCAL** PDF/EPUB/CBR (`:source-local` can only zip out a CBZ's first image), and
 *  - **any server download read offline** (Komga & Co.): with no network the source cover is empty too,
 *    so the cover is rendered from the downloaded file instead of showing a blank tile.
 *
 * The render itself ([LocalCoverStore]) needs an engine ([DocumentFactory]/MuPDF), which by module rule
 * lives only in the app/data layer, not in `:source-local`. Returns `null` when no download matches the
 * cover or on any failure — the caller then keeps the (empty) primary bytes (no cover, as before).
 */
@Singleton
class LocalCoverRenderer @Inject constructor(
    private val downloads: DownloadRepository,
    private val coverStore: LocalCoverStore,
) {
    suspend fun render(model: SourceCover): ByteArray? {
        // The downloaded book backing this cover — series cover = its first book, book cover = that book.
        val book = downloads.downloads.first().coverBookFor(model) ?: return null
        // Reads the precomputed cover if present, renders + persists on a cold miss. See [LocalCoverStore].
        return coverStore.get(book)
    }
}
