package com.komgareader.source.local

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import java.io.File
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

    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> =
        PagedResult(index().series.map { it.toSeries() }, hasNextPage = false)

    override suspend fun search(query: String, page: Int): PagedResult<Series> =
        PagedResult(
            index().series.filter { it.title.contains(query, ignoreCase = true) }.map { it.toSeries() },
            hasNextPage = false,
        )

    override suspend fun books(seriesRemoteId: String): List<Book> =
        index().series(seriesRemoteId)?.let { s -> s.books.map { it.toBook(s.title) } }.orEmpty()

    override suspend fun seriesDetail(seriesRemoteId: String): Series? {
        val s = index().series(seriesRemoteId) ?: return null
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
        val book = index().book(bookRemoteId) ?: return emptyList()
        if (book.format != BookFormat.CBZ) return emptyList() // PDF/CBR/EPUB → whole-file render
        val cbz = withContext(Dispatchers.IO) { cbzOf(bookRemoteId) } ?: return emptyList()
        val count = withContext(Dispatchers.IO) { cbz.pageCount() }
        return (0 until count).map { i -> PageRef(index = i, bookRemoteId = bookRemoteId, pageNumber = i + 1, url = "") }
    }

    override suspend fun openPage(ref: PageRef): ByteArray {
        val cbz = withContext(Dispatchers.IO) { cbzOf(ref.bookRemoteId) }
            ?: throw UnsupportedOperationException("LocalSource streams CBZ pages only")
        return withContext(Dispatchers.IO) { cbz.pageBytes(ref.index) }
    }

    override suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        val file = materialize(bookRemoteId) ?: error("Local file not found: $bookRemoteId")
        file.readBytes().also { onProgress(it.size.toLong(), it.size.toLong()) }
    }

    override suspend fun seriesIdOf(bookRemoteId: String): String =
        index().series.firstOrNull { s -> s.books.any { it.remoteId == bookRemoteId } }?.remoteId
            ?: bookRemoteId

    override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray {
        val bookId = if (isSeriesCover) index().series(remoteId)?.books?.firstOrNull()?.remoteId else remoteId
        bookId ?: return ByteArray(0)
        val cbz = withContext(Dispatchers.IO) { cbzOf(bookId) } ?: return ByteArray(0)
        return withContext(Dispatchers.IO) { cbz.coverBytes() }
    }

    // --- helpers ---
    private suspend fun materialize(bookRemoteId: String): File? {
        val uri = scanner.uriOf(bookRemoteId) ?: return null
        return withContext(Dispatchers.IO) { cache.materialize(uri, bookRemoteId) }
    }

    private suspend fun cbzOf(bookRemoteId: String): CbzArchive? {
        val book = index().book(bookRemoteId) ?: return null
        if (book.format != BookFormat.CBZ) return null
        val file = materialize(bookRemoteId) ?: return null
        return CbzArchive(file)
    }

    private fun LocalSeries.toSeries(): Series =
        Series(id = 0L, sourceId = this@LocalSource.id, remoteId = remoteId, title = title)

    private fun LocalBook.toBook(seriesTitle: String): Book = Book(
        id = 0L,
        sourceId = this@LocalSource.id,
        seriesId = 0L,
        remoteId = remoteId,
        title = title,
        format = format,
        pageCount = 0, // real count comes from the opened Document (reader) / pages() for CBZ
        seriesTitle = seriesTitle,
        number = number,
        summary = summary,
    )
}
