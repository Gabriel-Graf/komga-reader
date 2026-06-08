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

class SourcePageFetcherTest {

    /** Minimale Quelle, die nur [openPage] echt implementiert; alles andere ist ungenutzt. */
    private class FakePageSource(override val id: Long) : BrowsableSource {
        override val name = "Fake"
        override val kind = SourceKind.KOMGA

        override suspend fun openPage(ref: PageRef): ByteArray =
            "PAGE${ref.pageNumber}".toByteArray()

        override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> = error("not used")
        override suspend fun search(query: String, page: Int): PagedResult<Series> = error("not used")
        override suspend fun books(seriesRemoteId: String): List<Book> = error("not used")
        override suspend fun seriesDetail(seriesRemoteId: String): Series? = error("not used")
        override suspend fun pages(bookRemoteId: String): List<PageRef> = error("not used")
        override suspend fun downloadFile(bookRemoteId: String, onProgress: (Long, Long) -> Unit): ByteArray = error("not used")
        override suspend fun seriesIdOf(bookRemoteId: String): String = error("not used")
    }

    @Test
    fun `loadPageBytes loest Bytes ueber openPage der registrierten Quelle auf`() = runBlocking {
        val sources = SourceManager()
        sources.register(FakePageSource(id = 42L))

        val bytes = loadPageBytes(SourceImage(sourceId = 42L, bookRemoteId = "b1", pageNumber = 7), sources)

        assertEquals("PAGE7", String(bytes))
    }

    @Test
    fun `loadPageBytes wirft fuer eine nicht registrierte Quelle`() {
        val sources = SourceManager()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { loadPageBytes(SourceImage(sourceId = 99L, bookRemoteId = "b1", pageNumber = 1), sources) }
        }
    }
}
