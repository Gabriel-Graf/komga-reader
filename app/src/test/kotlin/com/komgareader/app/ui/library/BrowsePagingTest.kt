package com.komgareader.app.ui.library

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowsePagingTest {

    /** Minimal source whose browse() returns the configured pages and reports hasNextPage. */
    private class PagedFakeSource(private val pages: List<List<Series>>) : BrowsableSource {
        override val id = 1L
        override val name = "fake"
        override val kind = SourceKind.OPDS

        override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> =
            PagedResult(
                items = pages.getOrElse(page) { emptyList() },
                hasNextPage = page < pages.lastIndex,
            )

        override suspend fun search(query: String, page: Int): PagedResult<Series> = error("unused")
        override suspend fun books(seriesRemoteId: String): List<Book> = error("unused")
        override suspend fun seriesDetail(seriesRemoteId: String): Series? = error("unused")
        override suspend fun pages(bookRemoteId: String): List<PageRef> = error("unused")
        override suspend fun openPage(ref: PageRef): ByteArray = error("unused")
        override suspend fun downloadFile(bookRemoteId: String, onProgress: (Long, Long) -> Unit): ByteArray = error("unused")
        override suspend fun seriesIdOf(bookRemoteId: String): String = error("unused")
        override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray = error("unused")
    }

    private fun series(remoteId: String) =
        Series(id = 0L, sourceId = 1L, remoteId = remoteId, title = remoteId, coverUrl = null)

    @Test
    fun `sammelt Serien über alle Seiten`() = runTest {
        val source = PagedFakeSource(
            listOf(
                listOf(series("a"), series("b")),
                listOf(series("c")),
                listOf(series("d"), series("e")),
            ),
        )

        val all = browseAllSeries(source)

        assertEquals(listOf("a", "b", "c", "d", "e"), all.map { it.remoteId })
    }

    @Test
    fun `einzelne Seite ohne Folgeseite liefert genau diese`() = runTest {
        val source = PagedFakeSource(listOf(listOf(series("a"), series("b"))))

        assertEquals(listOf("a", "b"), browseAllSeries(source).map { it.remoteId })
    }

    @Test
    fun `maxPages begrenzt eine endlose Quelle`() = runTest {
        // hasNextPage ist immer true (page < lastIndex einer faktisch unendlichen Liste simuliert
        // über eine Quelle, die jede Seite als nicht-letzte meldet).
        val endless = object : BrowsableSource by PagedFakeSource(emptyList()) {
            override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> =
                PagedResult(items = listOf(series("p$page")), hasNextPage = true)
        }

        val all = browseAllSeries(endless, maxPages = 3)

        assertEquals(listOf("p0", "p1", "p2"), all.map { it.remoteId })
    }
}
