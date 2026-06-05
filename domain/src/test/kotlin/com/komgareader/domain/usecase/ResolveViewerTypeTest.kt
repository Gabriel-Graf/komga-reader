package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ViewerType
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveViewerTypeTest {

    private val resolve = ResolveViewerType()
    private val shelf = Shelf(id = 1, name = "R", contentType = ContentType.COMIC, sourceIds = listOf(5))
    private fun series(override: ContentType? = null) =
        Series(id = 9, sourceId = 5, remoteId = "r", title = "T", contentTypeOverride = override)

    @Test
    fun `Comic-Regal ohne Override ergibt PAGED`() {
        assertEquals(ViewerType.PAGED, resolve(series(), shelf))
    }

    @Test
    fun `Webtoon-Regal ergibt WEBTOON`() {
        val webtoonShelf = shelf.copy(contentType = ContentType.WEBTOON)
        assertEquals(ViewerType.WEBTOON, resolve(series(), webtoonShelf))
    }

    @Test
    fun `Novel-Regal ergibt EPUB`() {
        val novelShelf = shelf.copy(contentType = ContentType.NOVEL)
        assertEquals(ViewerType.EPUB, resolve(series(), novelShelf))
    }

    @Test
    fun `Serien-Override schlaegt den Regal-Typ`() {
        assertEquals(ViewerType.WEBTOON, resolve(series(ContentType.WEBTOON), shelf))
    }
}
