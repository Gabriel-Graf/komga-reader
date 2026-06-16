package com.komgareader.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.repository.DownloadedBook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent, **precomputed** cover store for the LOCAL formats that genuinely need a render engine:
 * **PDF/CBR** (their cover is a rasterized first page). CBZ and EPUB are renderer-free zip reads
 * handled inside `:source-local` (`coverBytes`) and never reach here. Covers are rendered once
 * (page 0 via [DocumentFactory]/MuPDF) and written to `filesDir/local-covers/`, so the library grid
 * loads instantly instead of rendering each cover synchronously inside Coil's fetch (which janks on
 * E-Ink and pops covers in one by one).
 *
 * Single owner of the render-and-cache logic: both the background prewarm ([prewarmAndPrune],
 * kicked off after the local download reconcile) and the on-demand Coil fallback
 * ([LocalCoverRenderer]) go through [get]. The cache key folds the file signature (size + mtime),
 * so replacing a local file re-renders and the stale file is pruned.
 */
@Singleton
class LocalCoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentFactory: DocumentFactory,
    private val localBookBytes: LocalBookBytes,
) {
    private val dir: File by lazy { File(context.filesDir, "local-covers").apply { mkdirs() } }

    // Bound concurrent MuPDF renders so a folder of many works doesn't thrash the device (E-Ink).
    private val renderGate = Semaphore(permits = 3)

    /**
     * Cover bytes for [book]: a cache hit reads the precomputed PNG; a miss renders page 0, writes
     * it, and returns it. `null` for formats handled by the source (CBZ/EPUB) or on render failure.
     */
    suspend fun get(book: DownloadedBook): ByteArray? {
        if (!needsAppRender(book.format)) return null
        val file = cacheFileFor(book) ?: return null
        return withContext(Dispatchers.IO) {
            if (file.isFile && file.length() > 0L) {
                runCatching { file.readBytes() }.getOrNull()
            } else {
                renderGate.withPermit { renderAndStore(book, file) }
            }
        }
    }

    /**
     * Background pass: render every still-missing local cover and delete stale files. Idempotent —
     * a cache hit does no work, so re-running after each local reconcile is cheap.
     */
    suspend fun prewarmAndPrune(books: List<DownloadedBook>) {
        val renderable = books.filter { needsAppRender(it.format) }
        val keepKeys = renderable.mapNotNull { cacheKeyFor(it) }.toSet()
        withContext(Dispatchers.IO) {
            val existing = dir.list()?.toSet().orEmpty()
            coverPrunePlan(existing, keepKeys).forEach { runCatching { File(dir, it).delete() } }
        }
        coroutineScope {
            renderable.forEach { book ->
                val file = cacheFileFor(book) ?: return@forEach
                if (file.isFile && file.length() > 0L) return@forEach
                launch(Dispatchers.IO) { renderGate.withPermit { renderAndStore(book, file) } }
            }
        }
    }

    private fun renderAndStore(book: DownloadedBook, file: File): ByteArray? = runCatching {
        val bytes = localBookBytes.bytesOf(book)
        // PDF/CBR: the cover is the rasterized first page (full-bleed). EPUB/CBZ never reach here.
        val cover = renderFirstPageCover(documentFactory, bytes, ".${book.format.lowercase()}")
            ?: return null
        File(dir, "${file.name}.tmp").let { tmp ->
            tmp.writeBytes(cover)
            tmp.renameTo(file)
        }
        cover
    }.getOrNull()

    private fun cacheKeyFor(book: DownloadedBook): String? {
        val signature = fileSignature(book.localPath) ?: return null
        return coverCacheKey(book.bookRemoteId, signature)
    }

    private fun cacheFileFor(book: DownloadedBook): File? =
        cacheKeyFor(book)?.let { File(dir, "$it.png") }

    /** `size:mtime` of the (content-URI or plain-path) file, or `null` if it can't be stat-ed. */
    private fun fileSignature(localPath: String): String? = runCatching {
        if (localPath.startsWith("content://")) {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(localPath)) ?: return null
            "${doc.length()}:${doc.lastModified()}"
        } else {
            val f = File(localPath)
            if (!f.isFile) return null
            "${f.length()}:${f.lastModified()}"
        }
    }.getOrNull()
}

/** Stable cache-file key for a local cover: hash of the book id folded with its file signature. */
fun coverCacheKey(bookRemoteId: String, signature: String): String =
    sha256Hex("$bookRemoteId|$signature")

/** Which existing cache files (by name) are no longer current and should be deleted. */
fun coverPrunePlan(existingFiles: Set<String>, keepKeys: Set<String>): Set<String> =
    existingFiles
        .filter { it.endsWith(".png") && it.removeSuffix(".png") !in keepKeys }
        .toSet()

/** Only PDF/CBR need an app-side render; CBZ/EPUB covers come renderer-free from `:source-local`. */
private fun needsAppRender(format: String): Boolean =
    format.equals("pdf", ignoreCase = true) || format.equals("cbr", ignoreCase = true)

private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
