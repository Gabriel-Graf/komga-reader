package com.komgareader.domain.usecase

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.ReadingDirection
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.ViewerType
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveViewerTypeTest {

    private val resolve = ResolveViewerType()

    private fun series(
        override: ContentType? = null,
        direction: ReadingDirection? = null,
    ) = Series(
        id = 0, sourceId = 0, remoteId = "S", title = "t",
        contentTypeOverride = override, readingDirection = direction,
    )

    private fun book(format: BookFormat) =
        Book(id = 0, sourceId = 0, seriesId = 0, remoteId = "B", title = "t", format = format, pageCount = 1)

    @Test
    fun `Stufe 1 — Serien-Override schlaegt alles`() {
        val result = resolve(series(override = ContentType.WEBTOON), book(BookFormat.CBZ), fallback = ContentType.MANGA)
        assertEquals(ViewerType.WEBTOON, result)
    }

    @Test
    fun `Stufe 2 — EPUB-Format ergibt EPUB`() {
        val result = resolve(series(direction = ReadingDirection.WEBTOON), book(BookFormat.EPUB), fallback = null)
        assertEquals(ViewerType.EPUB, result)
    }

    @Test
    fun `Stufe 3 — vertikale Leserichtung ergibt WEBTOON trotz Archiv-Format`() {
        val result = resolve(series(direction = ReadingDirection.VERTICAL), book(BookFormat.CBZ), fallback = null)
        assertEquals(ViewerType.WEBTOON, result)
    }

    @Test
    fun `Stufe 4 — Bibliotheks-Default WEBTOON ergibt WEBTOON trotz CBZ`() {
        // Der entscheidende Fall: Webtoons liegen als CBZ vor; das Bibliotheks-Tag
        // muss den Format-Default (PAGED) schlagen.
        val result = resolve(series(), book(BookFormat.CBZ), fallback = ContentType.WEBTOON)
        assertEquals(ViewerType.WEBTOON, result)
    }

    @Test
    fun `Stufe 4 — Bibliotheks-Default COMIC ergibt PAGED bei CBZ`() {
        val result = resolve(series(), book(BookFormat.CBZ), fallback = ContentType.COMIC)
        assertEquals(ViewerType.PAGED, result)
    }

    @Test
    fun `Stufe 5 — Archiv-Format ohne Bibliotheks-Default ergibt PAGED`() {
        val result = resolve(series(direction = ReadingDirection.LTR), book(BookFormat.CBR), fallback = null)
        assertEquals(ViewerType.PAGED, result)
    }

    @Test
    fun `Stufe 6 — kein Signal ergibt PAGED als Default`() {
        val result = resolve(series(), book(BookFormat.CBZ), fallback = null)
        assertEquals(ViewerType.PAGED, result)
    }
}
