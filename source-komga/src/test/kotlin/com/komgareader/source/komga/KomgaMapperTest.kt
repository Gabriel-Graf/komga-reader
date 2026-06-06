package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DownloadState
import com.komgareader.domain.model.ReadingDirection
import com.komgareader.source.komga.dto.BookDto
import com.komgareader.source.komga.dto.BookMediaDto
import com.komgareader.source.komga.dto.BookMetadataDto
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
    fun `Series übernimmt Summary, Status und Genres aus Metadaten`() {
        val dto = SeriesDto(
            id = "S1", name = "Berserk",
            metadata = SeriesMetadataDto(
                title = "Berserk",
                status = "ONGOING",
                summary = "Guts zieht durch eine düstere Welt.",
                genres = listOf("Dark Fantasy", "Seinen"),
            ),
        )
        val series = mapper.toSeries(dto)
        assertEquals("Guts zieht durch eine düstere Welt.", series.summary)
        assertEquals("ONGOING", series.status)
        assertEquals(listOf("Dark Fantasy", "Seinen"), series.genres)
    }

    @Test
    fun `Series-Summary und -Status sind null wenn leer`() {
        val dto = SeriesDto(id = "S2", name = "Leer")
        val series = mapper.toSeries(dto)
        assertNull(series.summary)
        assertNull(series.status)
        assertEquals(emptyList(), series.genres)
    }

    @Test
    fun `Book übernimmt Summary und Kapitelnummer`() {
        val dto = BookDto(
            id = "B1", seriesId = "S1", name = "Vol. 1",
            media = BookMediaDto(mediaType = "application/zip", pagesCount = 10),
            metadata = BookMetadataDto(summary = "Erster Band.", number = "1"),
        )
        val book = mapper.toBook(dto)
        assertEquals("Erster Band.", book.summary)
        assertEquals("1", book.number)
    }

    @Test
    fun `Book-Summary und -Nummer sind null wenn leer`() {
        val dto = BookDto(
            id = "B2", seriesId = "S1", name = "Vol. 2",
            media = BookMediaDto(mediaType = "application/zip", pagesCount = 10),
        )
        val book = mapper.toBook(dto)
        assertNull(book.summary)
        assertNull(book.number)
    }

    @Test
    fun `Book übernimmt Read-Progress vom Server`() {
        val inProgress = mapper.toBook(
            BookDto(
                id = "B1", seriesId = "S1", name = "v01",
                media = BookMediaDto(mediaType = "application/zip", pagesCount = 187),
                readProgress = ReadProgressDto(page = 45, completed = false),
            ),
        )
        val finished = mapper.toBook(
            BookDto(
                id = "B2", seriesId = "S1", name = "v02",
                media = BookMediaDto(mediaType = "application/zip", pagesCount = 187),
                readProgress = ReadProgressDto(page = 187, completed = true),
            ),
        )
        assertEquals(45, inProgress.lastReadPage)
        assertEquals(false, inProgress.readCompleted)
        assertEquals(187, finished.lastReadPage)
        assertEquals(true, finished.readCompleted)
    }

    @Test
    fun `Book ohne Read-Progress ist ungelesen`() {
        val dto = BookDto(
            id = "B3", seriesId = "S1", name = "v03",
            media = BookMediaDto(mediaType = "application/zip", pagesCount = 10),
        )
        val book = mapper.toBook(dto)
        assertNull(book.lastReadPage)
        assertEquals(false, book.readCompleted)
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
    fun `PageRefs aus Seitenzahl bauen denselben Endpunkt ohne PageDtos`() {
        val refs = mapper.toPageRefs(bookRemoteId = "B7", pageCount = 3)
        assertEquals(3, refs.size)
        assertEquals(0, refs[0].index)
        assertEquals(1, refs[0].pageNumber)
        assertEquals("https://nas.local/api/v1/books/B7/pages/1", refs[0].url)
        assertEquals("https://nas.local/api/v1/books/B7/pages/3", refs[2].url)
    }

    @Test
    fun `PageRefs aus Seitenzahl 0 ergeben leere Liste`() {
        assertEquals(emptyList(), mapper.toPageRefs(bookRemoteId = "B0", pageCount = 0))
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

    @Test
    fun `Series-readingDirection wird aus Komga-Wert gemappt`() {
        val webtoon = mapper.toSeries(
            SeriesDto(id = "S1", name = "Tower", metadata = SeriesMetadataDto(readingDirection = "WEBTOON")),
        )
        val rtl = mapper.toSeries(
            SeriesDto(id = "S2", name = "Berserk", metadata = SeriesMetadataDto(readingDirection = "RIGHT_TO_LEFT")),
        )
        assertEquals(ReadingDirection.WEBTOON, webtoon.readingDirection)
        assertEquals(ReadingDirection.RTL, rtl.readingDirection)
    }

    @Test
    fun `Series-readingDirection ist null bei leer oder unbekannt`() {
        val leer = mapper.toSeries(SeriesDto(id = "S3", name = "X"))
        val unbekannt = mapper.toSeries(
            SeriesDto(id = "S4", name = "Y", metadata = SeriesMetadataDto(readingDirection = "DIAGONAL")),
        )
        assertNull(leer.readingDirection)
        assertNull(unbekannt.readingDirection)
    }
}
