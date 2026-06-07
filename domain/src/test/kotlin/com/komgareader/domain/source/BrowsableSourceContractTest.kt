package com.komgareader.domain.source

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

private class FakeBrowsable : BrowsableSource {
    override val id = 1L
    override val name = "f"
    override val kind = SourceKind.PLUGIN

    override suspend fun browse(page: Int, filter: SourceFilter) = PagedResult<Series>(emptyList(), false)
    override suspend fun search(query: String, page: Int) = PagedResult<Series>(emptyList(), false)
    override suspend fun books(seriesRemoteId: String) = emptyList<Book>()
    override suspend fun seriesDetail(seriesRemoteId: String): Series? = null
    override suspend fun pages(bookRemoteId: String) = buildPageRefs(bookRemoteId, 2)
    override suspend fun openPage(ref: PageRef) = byteArrayOf(ref.pageNumber.toByte())
    override suspend fun downloadFile(bookRemoteId: String) = "epub:$bookRemoteId".toByteArray()
    override suspend fun seriesIdOf(bookRemoteId: String) = "s-$bookRemoteId"
}

class BrowsableSourceContractTest {
    @Test
    fun `downloadFile und seriesIdOf erfuellbar ueber das interface`() = runTest {
        val s: BrowsableSource = FakeBrowsable()
        assertEquals("epub:b1", String(s.downloadFile("b1")))
        assertEquals("s-b1", s.seriesIdOf("b1"))
    }
}
