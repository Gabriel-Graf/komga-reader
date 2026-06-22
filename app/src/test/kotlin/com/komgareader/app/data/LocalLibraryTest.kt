package com.komgareader.app.data

import com.komgareader.app.data.coil.SourceCover
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DownloadState
import com.komgareader.domain.repository.DownloadedBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalLibraryTest {

    private fun dl(
        book: String,
        series: String = "S1",
        src: Long = 7,
        title: String = book,
        fmt: String = "CBZ",
        number: String? = null,
        summary: String? = null,
        status: String? = null,
        genres: List<String> = emptyList(),
    ) = DownloadedBook(
        bookRemoteId = book,
        sourceId = src,
        seriesRemoteId = series,
        title = title,
        format = fmt,
        localPath = "/dl/$book.$fmt",
        totalPages = 10,
        seriesTitle = "Series",
        number = number,
        seriesSummary = summary,
        seriesStatus = status,
        seriesGenres = genres,
    )

    // --- localBooks (offline book list of a downloaded server series) ---

    @Test fun `localBooks returns only the downloaded books of that series and source`() {
        val downloads = listOf(
            dl("b1", series = "S1", src = 7),
            dl("b2", series = "S2", src = 7), // other series
            dl("b3", series = "S1", src = 9), // other source
        )
        assertEquals(listOf("b1"), downloads.localBooks("S1", 7).map { it.remoteId })
    }

    @Test fun `localBooks maps the fields and marks the book LOCAL`() {
        val b = listOf(dl("b1", title = "Vol. 1", fmt = "epub")).localBooks("S1", 7).single()
        assertEquals(7L, b.sourceId)
        assertEquals("b1", b.remoteId)
        assertEquals("Vol. 1", b.title)
        assertEquals(BookFormat.EPUB, b.format)
        assertEquals(10, b.pageCount)
        assertEquals(DownloadState.LOCAL, b.downloadState)
    }

    @Test fun `localBooks sorts volumes in natural order (10 after 2)`() {
        val downloads = listOf(
            dl("b10", title = "Vol. 10"),
            dl("b2", title = "Vol. 2"),
            dl("b1", title = "Vol. 1"),
        )
        assertEquals(listOf("b1", "b2", "b10"), downloads.localBooks("S1", 7).map { it.remoteId })
    }

    @Test fun `localBooks sorts by band number, not the title string`() {
        // Titles don't carry the order (chapter names); the band number does. Sort must follow number.
        val downloads = listOf(
            dl("b3", title = "Apple", number = "3"),
            dl("b1", title = "Zebra", number = "1"),
            dl("b10", title = "Mango", number = "10"),
            dl("b2", title = "Banana", number = "2"),
        )
        assertEquals(listOf("b1", "b2", "b3", "b10"), downloads.localBooks("S1", 7).map { it.remoteId })
    }

    @Test fun `localBooks carries the band number onto the Book`() {
        val b = listOf(dl("b1", number = "5")).localBooks("S1", 7).single()
        assertEquals("5", b.number)
    }

    @Test fun `localBooks falls back to the title when no number is stored (old downloads)`() {
        val downloads = listOf(dl("b10", title = "Vol. 10"), dl("b2", title = "Vol. 2"))
        assertEquals(listOf("b2", "b10"), downloads.localBooks("S1", 7).map { it.remoteId })
    }

    @Test fun `localBooks tolerates unknown format strings`() {
        val b = listOf(dl("b1", fmt = "zip")).localBooks("S1", 7).single()
        assertEquals(BookFormat.CBZ, b.format)
    }

    @Test fun `localBooks is empty when nothing of that series is downloaded`() {
        val downloads = listOf(dl("b1", series = "S2"), dl("b2", series = "S3"))
        assertEquals(emptyList(), downloads.localBooks("S1", 7))
    }

    // --- coverBookFor (which downloaded book backs a SourceCover offline) ---

    @Test fun `coverBookFor matches a series cover to the first downloaded book of that series`() {
        val downloads = listOf(
            dl("b1", series = "S1", src = 7),
            dl("b2", series = "S1", src = 7),
        )
        val hit = downloads.coverBookFor(SourceCover(sourceId = 7, remoteId = "S1", isSeries = true))
        assertEquals("b1", hit?.bookRemoteId)
    }

    @Test fun `coverBookFor series cover picks the naturally-first volume regardless of list order`() {
        // DB / download order is not guaranteed sorted; the series cover must still be vol. 1's.
        val downloads = listOf(
            dl("b2", title = "Vol. 2"),
            dl("b10", title = "Vol. 10"),
            dl("b1", title = "Vol. 1"),
        )
        val hit = downloads.coverBookFor(SourceCover(sourceId = 7, remoteId = "S1", isSeries = true))
        assertEquals("b1", hit?.bookRemoteId)
    }

    @Test fun `coverBookFor matches a book cover to that exact book`() {
        val downloads = listOf(dl("b1", series = "S1", src = 7), dl("b2", series = "S1", src = 7))
        val hit = downloads.coverBookFor(SourceCover(sourceId = 7, remoteId = "b2", isSeries = false))
        assertEquals("b2", hit?.bookRemoteId)
    }

    @Test fun `coverBookFor returns null when no download matches the source`() {
        val downloads = listOf(dl("b1", series = "S1", src = 7))
        assertNull(downloads.coverBookFor(SourceCover(sourceId = 9, remoteId = "S1", isSeries = true)))
    }

    // --- localSeriesDetail (offline series metadata: description/status/genres) ---

    @Test fun `localSeriesDetail builds series metadata from the downloaded books`() {
        val downloads = listOf(
            dl("b1", summary = "An epic tale.", status = "ONGOING", genres = listOf("Action", "Drama")),
            dl("b2", summary = "An epic tale.", status = "ONGOING", genres = listOf("Action", "Drama")),
        )
        val series = downloads.localSeriesDetail("S1", 7)
        assertEquals("An epic tale.", series?.summary)
        assertEquals("ONGOING", series?.status)
        assertEquals(listOf("Action", "Drama"), series?.genres)
        assertEquals("S1", series?.remoteId)
    }

    @Test fun `localSeriesDetail is null when nothing of that series is downloaded`() {
        assertNull(listOf(dl("b1", series = "S2")).localSeriesDetail("S1", 7))
    }
}
