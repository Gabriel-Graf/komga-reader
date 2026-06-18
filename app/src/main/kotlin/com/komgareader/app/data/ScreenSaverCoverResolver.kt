package com.komgareader.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.app.ui.reader.ViewerMode
import com.komgareader.data.cover.renderFirstPageCover
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ScreenSaverMode
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import com.komgareader.source.local.extractEpubCoverImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves and applies the device standby cover, off the reader-open path (driven by
 * [com.komgareader.app.work.ScreenSaverCoverWorker]). Two stages, the first being the crash fallback:
 *
 * 1. **Baseline:** the source's own cover — series poster for Webtoon, else the work's book cover —
 *    applied immediately. If anything below crashes, this image is already the standby.
 * 2. **Upgrade (only when the baseline is much smaller than the screen):** a high-resolution cover.
 *    Streamable image works expose the full first page via [BrowsableSource.openPage] (cheap, native
 *    resolution = the cover). Whole-file works without streamable pages (EPUB/PDF/CBR) are downloaded
 *    once and the cover is extracted: the embedded image for EPUB, MuPDF page-0 render otherwise.
 *
 * Webtoon keeps the series poster (a chapter's first page is the strip top, not a poster) and is not
 * upgraded via first page. All steps are wrapped so a failure never propagates past the worker.
 */
@Singleton
class ScreenSaverCoverResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sources: SourceManager,
    private val documentFactory: DocumentFactory,
    private val localCoverRenderer: LocalCoverRenderer,
    private val screenSaver: ScreenSaverManager,
    private val settings: SettingsRepository,
) {
    suspend fun refresh(sourceId: Long, bookRemoteId: String, viewerMode: ViewerMode, format: BookFormat) {
        val mode = runCatching { ScreenSaverMode.valueOf(settings.screenSaverMode.first()) }
            .getOrDefault(ScreenSaverMode.OFF)
        if (mode != ScreenSaverMode.BOOK_COVER) return
        val source = sources.get(sourceId) as? BrowsableSource ?: return

        val kind = coverKindFor(viewerMode)
        val baseline = baselineCover(source, bookRemoteId, kind)
        if (baseline != null && baseline.isNotEmpty()) {
            runCatching { screenSaver.applyBytes(baseline) } // crash fallback: standby is set before any upgrade work
        }

        if (kind == ScreenSaverCoverKind.SERIES) return // series poster is the cover; no first-page upgrade
        if (!needsHighResUpgrade(minEdgeOf(baseline), screenMinEdge())) return

        val hi = highResWorkCover(source, bookRemoteId, format)
        if (hi != null && hi.isNotEmpty()) {
            Log.i(TAG, "screensaver upgraded to high-res cover (${hi.size} bytes)")
            runCatching { screenSaver.applyBytes(hi) }
        }
    }

    /** Stage-1 cover: series poster (Webtoon) or the work's book cover, with the local render fallback. */
    private suspend fun baselineCover(source: BrowsableSource, bookRemoteId: String, kind: ScreenSaverCoverKind): ByteArray? =
        when (kind) {
            ScreenSaverCoverKind.SERIES -> {
                val seriesId = runCatching { source.seriesIdOf(bookRemoteId) }.getOrNull()
                val poster = seriesId?.let { runCatching { source.coverBytes(it, isSeriesCover = true) }.getOrNull() }
                poster?.takeIf { it.isNotEmpty() }
                    ?: seriesId?.let { runCatching { localCoverRenderer.render(SourceCover(source.id, it, isSeries = true)) }.getOrNull() }
            }
            ScreenSaverCoverKind.WORK -> {
                val cover = runCatching { source.coverBytes(bookRemoteId, isSeriesCover = false) }.getOrNull()
                cover?.takeIf { it.isNotEmpty() }
                    ?: runCatching { localCoverRenderer.render(SourceCover(source.id, bookRemoteId, isSeries = false)) }.getOrNull()
            }
        }

    /** Stage-2 high-res cover for a work: full first page if streamable, else whole-file extraction. */
    private suspend fun highResWorkCover(source: BrowsableSource, bookRemoteId: String, format: BookFormat): ByteArray? {
        runCatching { source.pages(bookRemoteId).firstOrNull() }.getOrNull()?.let { ref ->
            runCatching { source.openPage(ref) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val whole = runCatching { source.downloadFile(bookRemoteId) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: return null
        return if (format == BookFormat.EPUB) {
            runCatching { extractEpubCoverImage(whole) }.getOrNull()
        } else {
            runCatching { renderFirstPageCover(documentFactory, whole, ".${format.name.lowercase()}") }.getOrNull()
        }
    }

    /** Shorter edge (px) of the encoded image via a bounds-only decode; 0 if it can't be decoded. */
    private fun minEdgeOf(bytes: ByteArray?): Int {
        if (bytes == null || bytes.isEmpty()) return 0
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return 0
        return minOf(opts.outWidth, opts.outHeight)
    }

    private fun screenMinEdge(): Int {
        val dm = context.resources.displayMetrics
        return minOf(dm.widthPixels, dm.heightPixels)
    }

    private companion object {
        const val TAG = "ScreenSaverCover"
    }
}
