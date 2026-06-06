package com.komgareader.source.komga

import com.komgareader.domain.model.ReadProgress
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-End: ein simulierter Komga-Server liefert eine Serie → Bücher → Seiten →
 * Bytes, und nimmt einen Fortschritts-Push entgegen. Beweist die ganze Quelle als
 * Kette, nicht nur einzelne Calls.
 */
class KomgaE2ETest {

    private val server = MockWebServer()

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `browse bis Fortschritts-Push als durchgehender Fluss`() = runTest {
        // 1. Serie browsen
        server.enqueue(MockResponse().setBody("""{"content":[{"id":"S1","name":"Berserk","metadata":{"title":"Berserk"}}],"last":true,"number":0,"totalPages":1}"""))
        // 2. Bücher der Serie
        server.enqueue(MockResponse().setBody("""{"content":[{"id":"B1","seriesId":"S1","name":"v01","media":{"mediaType":"application/zip","pagesCount":2},"metadata":{"title":"Vol. 1"}}],"last":true,"number":0,"totalPages":1}"""))
        // 3. Seiten des Buchs
        server.enqueue(MockResponse().setBody("""[{"number":1,"mediaType":"image/jpeg"},{"number":2,"mediaType":"image/jpeg"}]"""))
        // 4. Bytes der ersten Seite
        server.enqueue(MockResponse().setBody("PAGE1"))
        // 5. Fortschritt push (204)
        server.enqueue(MockResponse().setResponseCode(204))

        val source = KomgaSourceFactory.create("Mein Komga", server.url("/api/v1/").toString(), "k")

        val series = source.browse(0, com.komgareader.domain.source.SourceFilter()).items.single()
        assertEquals("Berserk", series.title)

        val book = source.books(series.remoteId).single()
        assertEquals(2, book.pageCount)

        val pages = source.pages(book.remoteId)
        assertEquals(2, pages.size)

        val bytes = source.openPage(pages.first())
        assertEquals("PAGE1", bytes.decodeToString())

        source.pushProgress(book.remoteId, ReadProgress(bookId = 1, page = 1, totalPages = 2, updatedAt = 1))

        // Verifiziere die fünf Requests in Reihenfolge
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/series?"))
        assertTrue(server.takeRequest().path!!.endsWith("/series/S1/books?unpaged=true"))
        assertTrue(server.takeRequest().path!!.endsWith("/books/B1/pages"))
        assertTrue(server.takeRequest().path!!.endsWith("/books/B1/pages/1"))
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertTrue(patch.path!!.endsWith("/books/B1/read-progress"))
    }
}
