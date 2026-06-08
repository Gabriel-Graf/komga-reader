package com.komgareader.app.ui.reader

import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.SourceRegistration
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.DownloadedBook
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceManager
import com.komgareader.domain.source.SyncingSource
import com.komgareader.domain.usecase.NovelProgressMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpubBytesLoaderTest {

    /**
     * Quellen-agnostische Test-Quelle: implementiert nur die für den Loader relevanten
     * Methoden ([downloadFile] über [BrowsableSource], [pullProgress] über [SyncingSource]).
     * Beweist, dass der Loader über die Naht arbeitet — kein Komga-Wissen nötig.
     */
    private class FakeSource(
        override val id: Long = 7L,
        private val fileBytes: ByteArray = ByteArray(0),
        private val progress: ReadProgress? = null,
    ) : BrowsableSource, SyncingSource {
        override val name = "Fake"
        override val kind = SourceKind.KOMGA

        override suspend fun downloadFile(bookRemoteId: String, onProgress: (Long, Long) -> Unit): ByteArray = fileBytes
        override suspend fun pullProgress(bookRemoteId: String): ReadProgress? = progress

        override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> = error("not used")
        override suspend fun search(query: String, page: Int): PagedResult<Series> = error("not used")
        override suspend fun books(seriesRemoteId: String): List<Book> = error("not used")
        override suspend fun seriesDetail(seriesRemoteId: String): Series? = error("not used")
        override suspend fun pages(bookRemoteId: String): List<PageRef> = error("not used")
        override suspend fun openPage(ref: PageRef): ByteArray = error("not used")
        override suspend fun seriesIdOf(bookRemoteId: String): String = error("not used")
        override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray = error("not used")
        override suspend fun pushProgress(bookRemoteId: String, progress: ReadProgress) = error("not used")
        override suspend fun setRead(bookRemoteId: String, read: Boolean, pageCount: Int) = error("not used")
    }

    private class FakeServerRepository : ServerRepository {
        override val configs: Flow<List<ServerConfig>> = flowOf(emptyList())
        override val config: Flow<ServerConfig?> = flowOf(null)
        override suspend fun save(config: ServerConfig) = error("not used")
        override suspend fun remove(id: Long) = error("not used")
        override suspend fun clear() = error("not used")
    }

    /** [ActiveSource] ist `open`; der Test liefert eine feste Quelle, statt eine echte aufzubauen. */
    private class FakeActiveSource(private val source: BrowsableSource?) : ActiveSource(
        sources = SourceManager(),
        servers = FakeServerRepository(),
        registration = SourceRegistration(SourceManager(), KomgaSourceProvider()),
    ) {
        override suspend fun current(): BrowsableSource? = source
    }

    private class FakeDownloads(private val book: DownloadedBook? = null) : DownloadRepository {
        override val downloads: Flow<List<DownloadedBook>> = flowOf(emptyList())
        override suspend fun get(bookRemoteId: String): DownloadedBook? = book
        override suspend fun put(book: DownloadedBook) = error("not used")
        override suspend fun remove(bookRemoteId: String) = error("not used")
    }

    private class FakeLocalBookBytes(private val bytes: ByteArray) : LocalBookBytes {
        override fun bytesOf(book: DownloadedBook): ByteArray = bytes
    }

    private fun loader(
        source: BrowsableSource?,
        downloads: DownloadRepository = FakeDownloads(),
        local: LocalBookBytes = FakeLocalBookBytes(ByteArray(0)),
    ): EpubBytesLoader = EpubBytesLoader(
        active = FakeActiveSource(source),
        downloadRepository = downloads,
        localBookBytes = local,
        novelProgressMapper = NovelProgressMapper(),
    )

    @Test
    fun `load mit forceStream liefert die downloadFile-Bytes der aktiven Quelle`() = runBlocking {
        val loader = loader(FakeSource(fileBytes = "EPUB".toByteArray()))

        val bytes = loader.load(bookId = "b1", forceStream = true)

        assertEquals("EPUB", String(bytes))
    }

    @Test
    fun `load ohne forceStream bevorzugt den lokalen Download`() = runBlocking {
        val local = DownloadedBook(
            bookRemoteId = "b1", sourceId = 7L, seriesRemoteId = "s1",
            title = "T", format = "epub", localPath = "/tmp/x.epub", totalPages = 1,
        )
        val loader = loader(
            source = FakeSource(fileBytes = "STREAM".toByteArray()),
            downloads = FakeDownloads(book = local),
            local = FakeLocalBookBytes("LOCAL".toByteArray()),
        )

        val bytes = loader.load(bookId = "b1", forceStream = false)

        assertEquals("LOCAL", String(bytes))
    }

    @Test
    fun `startProgressFraction bildet pullProgress auf relative Position ab`() = runBlocking {
        // page 26 von totalPages 51 → (26-1)/(51-1) = 25/50 = 0.5
        val loader = loader(
            FakeSource(progress = ReadProgress(bookId = 0, page = 26, totalPages = 51, updatedAt = 0)),
        )

        val fraction = loader.startProgressFraction(bookId = "b1")

        assertEquals(0.5f, fraction)
    }

    @Test
    fun `startProgressFraction ist 0 ohne aktive Quelle`() = runBlocking {
        val loader = loader(source = null)

        assertEquals(0f, loader.startProgressFraction(bookId = "b1"))
    }
}
