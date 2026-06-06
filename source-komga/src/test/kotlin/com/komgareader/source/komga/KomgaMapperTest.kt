package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DownloadState
import com.komgareader.source.komga.dto.BookDto
import com.komgareader.source.komga.dto.BookMediaDto
import com.komgareader.source.komga.dto.PageDto
import com.komgareader.source.komga.dto.ReadProgressDto
import com.komgareader.source.komga.dto.SeriesDto
import com.komgareader.source.komga.dto.SeriesMetadataDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KomgaMapperTest {

    private val mapper = KomgaMapper(sourceId = 77, baseUrl = "https://nas.local/api/v1/")

    @Test
    fun `Series übernimmt remoteId, Titel und baut Cover-URL`() {
        val dto = SeriesDto(id = "S1", name = "Berserk", metadata = SeriesMetadataDto(title = "Berserk Deluxe"))
        val series = mapper.toSeries(dto)
        assertEquals(77, series.sourceId)
        assertEquals("S1", series.remoteId)
        assertEquals("Berserk Deluxe", series.title) // metadata.title hat Vorrang
        assertEquals("https://nas.local/api/v1/series/S1/thumbnail", series.coverUrl)
    }

    @Test
    fun `Series fällt auf name zurück wenn metadata-title leer`() {
        val dto = SeriesDto(id = "S2", name = "Solo Leveling")
        assertEquals("Solo Leveling", mapper.toSeries(dto).title)
    }

    @Test
    fun `Book mappt Format, Seitenzahl und remoteIds`() {
        val dto = BookDto(
            id = "B1", seriesId = "S1", name = "Vol. 1",
            media = BookMediaDto(mediaType = "application/zip", pagesCount = 220),
        )
        val book = mapper.toBook(dto)
        assertEquals("B1", book.remoteId)
        assertEquals(77, book.sourceId)
        assertEquals(BookFormat.CBZ, book.format)
        assertEquals(220, book.pageCount)
        assertEquals(DownloadState.REMOTE, book.downloadState)
    }

    @Test
    fun `PageRefs verweisen auf den Seiten-Endpunkt (1-basiert)`() {
        val pages = listOf(PageDto(number = 1), PageDto(number = 2))
        val refs = mapper.toPageRefs(bookRemoteId = "B1", pages = pages)
        assertEquals(2, refs.size)
        assertEquals(0, refs[0].index) // 0-basierter interner Index
        assertEquals("https://nas.local/api/v1/books/B1/pages/1", refs[0].url)
        assertEquals("https://nas.local/api/v1/books/B1/pages/2", refs[1].url)
    }

    @Test
    fun `ReadProgress wird übernommen wenn vorhanden`() {
        val dto = BookDto(
            id = "B1", seriesId = "S1", name = "x",
            media = BookMediaDto(mediaType = "application/zip", pagesCount = 100),
            readProgress = ReadProgressDto(page = 42, completed = false),
        )
        val progress = mapper.toReadProgress(dto, localBookId = 9, updatedAt = 123)!!
        assertEquals(9, progress.bookId)
        assertEquals(42, progress.page)
        assertEquals(100, progress.totalPages)
        assertEquals(false, progress.completed)
        assertEquals(123, progress.updatedAt)
    }

    @Test
    fun `ReadProgress ist null wenn Buch nie geöffnet`() {
        val dto = BookDto(id = "B1", seriesId = "S1", name = "x",
            media = BookMediaDto(mediaType = "application/pdf", pagesCount = 10))
        assertNull(mapper.toReadProgress(dto, localBookId = 9, updatedAt = 1))
    }
}
