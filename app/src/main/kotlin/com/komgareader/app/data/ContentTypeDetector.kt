package com.komgareader.app.data

import android.graphics.BitmapFactory
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ContentSignals
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.PageSample
import com.komgareader.domain.repository.SeriesAutoTypeRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.usecase.SuggestContentType
import com.komgareader.domain.usecase.measureGrayFraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imperative shell for content-type auto-detection (seam-respecting: pages flow through
 * [BrowsableSource.openPage]). Samples a few interior pages, decodes them downsampled,
 * measures grayscale fraction + aspect via the pure domain helpers, and persists the
 * [SuggestContentType] verdict. Idempotent: skips when a fresh-version row already exists.
 * Runs off the read path (caller launches it in the background).
 */
@Singleton
class ContentTypeDetector @Inject constructor(
    private val autoTypes: SeriesAutoTypeRepository,
) {
    private val suggest = SuggestContentType()

    /**
     * Samples + persists the suggestion if not already done at the current version. Returns the
     * persisted verdict (non-null) so the caller can refresh its viewer-type resolution as soon as
     * detection lands — `null` means nothing actionable changed (already detected, no image archive,
     * sampling failed, or an ambiguous verdict that produces no suggestion).
     */
    suspend fun detectIfNeeded(source: BrowsableSource, seriesRemoteId: String, books: List<Book>): ContentType? {
        if (autoTypes.detectorVersion(source.id, seriesRemoteId) == DETECTOR_VERSION) return null
        val book = books.firstOrNull { it.format.isImageArchive() } ?: return null
        val signals = runCatching { sample(source, book) }.getOrNull() ?: return null
        val verdict = suggest(signals)
        autoTypes.set(source.id, seriesRemoteId, verdict, DETECTOR_VERSION)
        return verdict
    }

    private suspend fun sample(source: BrowsableSource, book: Book): ContentSignals = withContext(Dispatchers.IO) {
        val refs = source.pages(book.remoteId)
        if (refs.isEmpty()) return@withContext ContentSignals(emptyList())
        // Skip cover (index 0): covers are often coloured even in B/W manga.
        val interior = refs.drop(1)
        if (interior.isEmpty()) return@withContext ContentSignals(emptyList())
        val picked = listOf(0.25, 0.5, 0.75)
            .map { (it * (interior.size - 1)).toInt() }
            .distinct()
            .map { interior[it] }
        val samples = picked.mapNotNull { ref ->
            val bytes = runCatching { source.openPage(ref) }.getOrNull() ?: return@mapNotNull null
            decodeSample(bytes)
        }
        ContentSignals(samples)
    }

    private fun decodeSample(bytes: ByteArray): PageSample? {
        // Bounds first to size the downsample, then decode small for cheap pixel stats.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val sample = maxOf(1, maxOf(w, h) / STATS_LONG_EDGE_PX)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return try {
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            PageSample(widthPx = w, heightPx = h, grayFraction = measureGrayFraction(pixels))
        } finally {
            bmp.recycle()
        }
    }

    private fun BookFormat.isImageArchive() =
        this == BookFormat.CBZ || this == BookFormat.CBR || this == BookFormat.PDF

    companion object {
        const val DETECTOR_VERSION = 1
        private const val STATS_LONG_EDGE_PX = 200
    }
}
