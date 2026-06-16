package com.komgareader.source.local

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.DownloadedBook
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalSource internal constructor(
    override val id: Long,
    override val name: String,
    private val scanner: LocalFolderScanner,
    private val cache: LocalFileCache,
    private val parser: LocalMetadataParser = LocalMetadataParser(),
) : BrowsableSource {

    override val kind: SourceKind = SourceKind.LOCAL

    private val indexLock = Mutex()
    @Volatile private var cached: LocalIndex? = null

    private suspend fun index(): LocalIndex {
        cached?.let { return it }
        return indexLock.withLock {
            cached ?: withContext(Dispatchers.IO) { LocalLibraryMapper().map(scanner.scan()) }
                .also { cached = it }
        }
    }

    /** Drop the cached index so the next access rescans (on-start / manual reload). */
    suspend fun refresh() = indexLock.withLock { cached = null }

    /**
     * All indexed books as [DownloadedBook]s so the wiring layer can register local works in the
     * downloads table — they then carry the "downloaded" badge and read via the offline path like
     * any download. `localPath` is the real SAF document URI of the file (O(1), no findFile walk);
     * ids are the same opaque (encoded) remoteIds the rest of the app sees. `totalPages = 0` (the
     * reader gets the real count when it opens the document). Pure index read — no file I/O.
     */
    suspend fun asDownloadedBooks(): List<DownloadedBook> = index().series.flatMap { series ->
        series.books.map { book ->
            DownloadedBook(
                bookRemoteId = encode(book.remoteId),
                sourceId = id,
                seriesRemoteId = encode(series.remoteId),
                title = book.title,
                format = book.format.name.lowercase(),
                localPath = scanner.documentUri(book.remoteId).orEmpty(),
                totalPages = 0,
                seriesTitle = series.title,
                seriesCoverUrl = null,
            )
        }
    }

    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> =
        PagedResult(index().series.map { it.toSeries() }, hasNextPage = false)

    override suspend fun search(query: String, page: Int): PagedResult<Series> =
        PagedResult(
            index().series.filter { it.title.contains(query, ignoreCase = true) }.map { it.toSeries() },
            hasNextPage = false,
        )

    override suspend fun books(seriesRemoteId: String): List<Book> =
        index().series(decode(seriesRemoteId))?.let { s -> s.books.map { it.toBook(s.title) } }.orEmpty()

    override suspend fun seriesDetail(seriesRemoteId: String): Series? {
        val s = index().series(decode(seriesRemoteId)) ?: return null
        val base = s.toSeries()
        // Enrich from the first CBZ's ComicInfo only (one materialize, cached). Best-effort.
        val firstCbz = s.books.firstOrNull { it.format == BookFormat.CBZ } ?: return base
        val meta = runCatching {
            withContext(Dispatchers.IO) {
                cbzOf(firstCbz.remoteId)?.comicInfoBytes()?.let { parser.parse(it) }
            }
        }.getOrNull() ?: return base
        return base.copy(
            title = meta.series?.ifBlank { null } ?: base.title,
            summary = meta.summary,
            status = meta.status,
            genres = meta.genres,
        )
    }

    override suspend fun pages(bookRemoteId: String): List<PageRef> {
        val realPath = decode(bookRemoteId)
        val book = index().book(realPath) ?: return emptyList()
        if (book.format != BookFormat.CBZ) return emptyList() // PDF/CBR/EPUB → whole-file render
        val cbz = withContext(Dispatchers.IO) { cbzOf(realPath) } ?: return emptyList()
        val count = withContext(Dispatchers.IO) { cbz.pageCount() }
        // PageRef carries the opaque (encoded) id back so openPage roundtrips the same token.
        return (0 until count).map { i -> PageRef(index = i, bookRemoteId = bookRemoteId, pageNumber = i + 1, url = "") }
    }

    override suspend fun openPage(ref: PageRef): ByteArray {
        val cbz = withContext(Dispatchers.IO) { cbzOf(decode(ref.bookRemoteId)) }
            ?: throw UnsupportedOperationException("LocalSource streams CBZ pages only")
        return withContext(Dispatchers.IO) { cbz.pageBytes(ref.index) }
    }

    override suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        val file = materialize(decode(bookRemoteId)) ?: error("Local file not found: $bookRemoteId")
        file.readBytes().also { onProgress(it.size.toLong(), it.size.toLong()) }
    }

    override suspend fun seriesIdOf(bookRemoteId: String): String {
        val realPath = decode(bookRemoteId)
        val series = index().series.firstOrNull { s -> s.books.any { it.remoteId == realPath } }
        return series?.let { encode(it.remoteId) } ?: bookRemoteId
    }

    override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray {
        val realBookPath = if (isSeriesCover) {
            index().series(decode(remoteId))?.books?.firstOrNull()?.remoteId
        } else {
            decode(remoteId)
        }
        realBookPath ?: return ByteArray(0)
        return when (index().book(realBookPath)?.format) {
            // CBZ: first zip image. EPUB: embedded cover image (full-bleed, like the server cover) —
            // both are renderer-free zip reads. PDF/CBR need a render engine → empty here, the app
            // layer renders + precomputes those (LocalCoverStore).
            BookFormat.CBZ -> withContext(Dispatchers.IO) { cbzOf(realBookPath)?.coverBytes() } ?: ByteArray(0)
            BookFormat.EPUB -> withContext(Dispatchers.IO) {
                materialize(realBookPath)?.let { extractEpubCoverImage(it.readBytes()) } ?: ByteArray(0)
            }
            else -> ByteArray(0)
        }
    }

    // --- helpers (operate on REAL relative paths; the seam exposes only encoded ids) ---

    /**
     * Domain remoteIds must be opaque (no '/') because the app threads them through navigation
     * routes as single path segments. Local relative paths like "Berserk/v01.cbz" contain '/',
     * so we expose them URL-safe Base64-encoded and decode at every seam entry point.
     */
    private fun encode(realPath: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(realPath.toByteArray())

    private fun decode(id: String): String =
        runCatching { String(Base64.getUrlDecoder().decode(id)) }.getOrDefault(id)

    private suspend fun materialize(realPath: String): File? {
        val uri = scanner.uriOf(realPath) ?: return null
        return withContext(Dispatchers.IO) { cache.materialize(uri, realPath) }
    }

    private suspend fun cbzOf(realPath: String): CbzArchive? {
        val book = index().book(realPath) ?: return null
        if (book.format != BookFormat.CBZ) return null
        val file = materialize(realPath) ?: return null
        return CbzArchive(file)
    }

    private fun LocalSeries.toSeries(): Series =
        Series(id = 0L, sourceId = this@LocalSource.id, remoteId = encode(remoteId), title = title)

    private fun LocalBook.toBook(seriesTitle: String): Book = Book(
        id = 0L,
        sourceId = this@LocalSource.id,
        seriesId = 0L,
        remoteId = encode(remoteId),
        title = title,
        format = format,
        pageCount = 0, // real count comes from the opened Document (reader) / pages() for CBZ
        seriesTitle = seriesTitle,
        number = number,
        summary = summary,
    )
}
