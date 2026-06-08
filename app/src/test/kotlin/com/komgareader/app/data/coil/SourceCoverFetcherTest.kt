package com.komgareader.app.data.coil

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SourceCoverFetcherTest {

    /** Minimale Quelle, die nur [coverBytes] echt implementiert. */
    private class FakeCoverSource(override val id: Long) : BrowsableSource {
        override val name = "Fake"
        override val kind = SourceKind.KOMGA

        override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray =
            "COVER:$remoteId:${if (isSeriesCover) "series" else "book"}".toByteArray()

        override suspend fun openPage(ref: PageRef): ByteArray = error("not used")
        override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> = error("not used")
        override suspend fun search(query: String, page: Int): PagedResult<Series> = error("not used")
        override suspend fun books(seriesRemoteId: String): List<Book> = error("not used")
        override suspend fun seriesDetail(seriesRemoteId: String): Series? = error("not used")
        override suspend fun pages(bookRemoteId: String): List<PageRef> = error("not used")
        override suspend fun downloadFile(bookRemoteId: String, onProgress: (Long, Long) -> Unit): ByteArray = error("not used")
        override suspend fun seriesIdOf(bookRemoteId: String): String = error("not used")
    }

    @Test
    fun `loadCoverBytes loest serien-cover ueber coverBytes der registrierten Quelle auf`() = runBlocking {
        val sources = SourceManager()
        sources.register(FakeCoverSource(id = 7L))

        val bytes = loadCoverBytes(SourceCover(sourceId = 7L, remoteId = "s1", isSeries = true), sources)

        assertEquals("COVER:s1:series", String(bytes))
    }

    @Test
    fun `loadCoverBytes loest buch-cover ueber coverBytes auf`() = runBlocking {
        val sources = SourceManager()
        sources.register(FakeCoverSource(id = 7L))

        val bytes = loadCoverBytes(SourceCover(sourceId = 7L, remoteId = "b1", isSeries = false), sources)

        assertEquals("COVER:b1:book", String(bytes))
    }

    @Test
    fun `loadCoverBytes wirft fuer eine nicht registrierte Quelle`() {
        val sources = SourceManager()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { loadCoverBytes(SourceCover(sourceId = 99L, remoteId = "s1", isSeries = true), sources) }
        }
    }
}
