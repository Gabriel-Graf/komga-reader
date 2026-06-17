package com.komgareader.app.data

import android.graphics.Bitmap
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.domain.render.Document
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders pages of a **whole-file** document (downloaded book, local PDF/CBR, OPDS without PSE)
 * via the MuPDF [DocumentFactory], so the COMIC/WEBTOON/paged readers can show such books through
 * the same Coil page-model path as streamed sources (see [com.komgareader.app.data.coil.RenderedPageImage]).
 *
 * Holds **one open document at a time** (a reader reads one book): the first [prepare]/[render]
 * for a book opens + caches it; subsequent page renders reuse the same handle (no re-parsing the
 * whole file per page). Opening a different book evicts (closes) the previous one. All native
 * document access is serialized by a [Mutex] because MuPDF is not thread-safe and Coil fetches
 * several pages concurrently (prefetch).
 *
 * Bytes are resolved seam-respectingly: a local download via [LocalBookBytes], otherwise the
 * source's own `downloadFile` (whole-file). The owning [com.komgareader.app.ui.reader.ReaderViewModel]
 * calls [prepare] once to learn the page count, then Coil drives [render] per visible page.
 */
@Singleton
class RenderedPageStore @Inject constructor(
    private val documentFactory: DocumentFactory,
    private val downloads: DownloadRepository,
    private val localBookBytes: LocalBookBytes,
    private val sources: SourceManager,
) {
    private val mutex = Mutex()
    private var openKey: String? = null
    private var openDoc: Document? = null

    private fun key(sourceId: Long, bookRemoteId: String) = "$sourceId/$bookRemoteId"

    /** Opens (and caches) the document for this book and returns its page count. */
    suspend fun prepare(sourceId: Long, bookRemoteId: String, ext: String): Int =
        withContext(Dispatchers.IO) { mutex.withLock { document(sourceId, bookRemoteId, ext).pageCount() } }

    /** Renders page [index] (0-based) of the cached document to an ARGB bitmap. */
    suspend fun render(sourceId: Long, bookRemoteId: String, index: Int, ext: String): Bitmap =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val rendered = document(sourceId, bookRemoteId, ext).renderPage(index, zoom = 2f, rotation = 0)
                Bitmap.createBitmap(rendered.pixels, rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
            }
        }

    /** Closes the cached document if it belongs to this book (reader teardown). Idempotent. */
    suspend fun release(sourceId: Long, bookRemoteId: String) = mutex.withLock {
        if (openKey == key(sourceId, bookRemoteId)) {
            openDoc?.close()
            openDoc = null
            openKey = null
        }
    }

    private suspend fun document(sourceId: Long, bookRemoteId: String, ext: String): Document {
        val k = key(sourceId, bookRemoteId)
        openDoc?.let { if (openKey == k) return it }
        openDoc?.close()
        openDoc = null
        openKey = null
        val opened = documentFactory.open(loadBytes(sourceId, bookRemoteId), ext)
        openDoc = opened
        openKey = k
        return opened
    }

    private suspend fun loadBytes(sourceId: Long, bookRemoteId: String): ByteArray {
        downloads.get(bookRemoteId)?.let { return localBookBytes.bytesOf(it) }
        val source = sources.get(sourceId) as? BrowsableSource
            ?: error("Source $sourceId not registered for whole-file render")
        return source.downloadFile(bookRemoteId)
    }
}
