package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KomgaSourceTest {

    private val server = MockWebServer()
    private fun source(): KomgaSource =
        KomgaSourceFactory.create(name = "Mein Komga", baseUrl = server.url("/api/v1/").toString(), apiKey = "k")

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `browse mappt Series-Seite und hasNextPage`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"content":[
              {"id":"S1","name":"Berserk","booksCount":3,"metadata":{"title":"Berserk","status":"ONGOING"}},
              {"id":"S2","name":"Saga","booksCount":9,"metadata":{"title":"","status":"ENDED"}}
            ],"last":false,"number":0,"totalPages":4}
        """.trimIndent()).addHeader("Content-Type", "application/json"))

        val result = source().browse(page = 0, filter = SourceFilter())

        assertEquals(2, result.items.size)
        assertEquals("S1", result.items[0].remoteId)
        assertEquals("Saga", result.items[1].title) // Fallback auf name
        assertTrue(result.hasNextPage) // last=false
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v1/series?"))
        assertEquals("k", req.getHeader("X-API-Key"))
    }

    @Test
    fun `search reicht die Query durch`() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[],"last":true,"number":0,"totalPages":0}"""))
        source().search(query = "luffy", page = 0)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("search=luffy"), "Pfad war: ${req.path}")
    }

    @Test
    fun `books mappt Buchliste mit Format und Seitenzahl`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"content":[
              {"id":"B1","seriesId":"S1","name":"v01","media":{"mediaType":"application/zip","pagesCount":220},"metadata":{"title":"Vol. 1"}}
            ],"last":true,"number":0,"totalPages":1}
        """.trimIndent()))

        val books = source().books("S1")
        assertEquals(1, books.size)
        assertEquals("B1", books[0].remoteId)
        assertEquals(BookFormat.CBZ, books[0].format)
        assertEquals(220, books[0].pageCount)
        assertEquals("Vol. 1", books[0].title)
    }

    @Test
    fun `pages liefert PageRefs auf den Seiten-Endpunkt`() = runTest {
        server.enqueue(MockResponse().setBody("""
            [{"number":1,"fileName":"001.jpg","mediaType":"image/jpeg"},
             {"number":2,"fileName":"002.jpg","mediaType":"image/jpeg"}]
        """.trimIndent()))

        val refs: List<PageRef> = source().pages("B1")
        assertEquals(2, refs.size)
        assertEquals(0, refs[0].index)
        assertTrue(refs[1].url.endsWith("/books/B1/pages/2"))
    }

    @Test
    fun `openPage lädt die rohen Seiten-Bytes`() = runTest {
        server.enqueue(MockResponse().setBody("BILDBYTES"))
        val ref = PageRef(index = 0, bookRemoteId = "B1", pageNumber = 1, url = server.url("/api/v1/books/B1/pages/1").toString())
        val bytes = source().openPage(ref)
        assertEquals("BILDBYTES", bytes.decodeToString())
    }

    @Test
    fun `pushProgress sendet PATCH mit page und completed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val progress = ReadProgress(bookId = 9, page = 55, totalPages = 220, updatedAt = 1)
        source().pushProgress("B1", progress)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertTrue(req.path!!.endsWith("/books/B1/read-progress"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"page\":55"), "Body war: $body")
        assertTrue(body.contains("\"completed\":false"))
    }

    @Test
    fun `pullProgress liest readProgress aus dem Buch`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"id":"B1","seriesId":"S1","name":"v01","media":{"mediaType":"application/zip","pagesCount":220},
             "readProgress":{"page":80,"completed":false}}
        """.trimIndent()))

        val progress = source().pullProgress("B1")!!
        assertEquals(80, progress.page)
        assertEquals(220, progress.totalPages)
    }

    @Test
    fun `seriesIdOf liest seriesId aus getBook`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"B1","seriesId":"S42","name":"v01","media":{"mediaType":"application/zip","pagesCount":10}}"""))
        assertEquals("S42", source().seriesIdOf("B1"))
        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/books/B1"))
    }

    @Test
    fun `listContainers mappt die Komga-Libraries`() = runTest {
        server.enqueue(MockResponse().setBody("""
            [{"id":"L1","name":"Manga"},{"id":"L2","name":"Comics"}]
        """.trimIndent()).addHeader("Content-Type", "application/json"))

        val containers = source().listContainers()

        assertEquals(2, containers.size)
        assertEquals("L1", containers[0].id)
        assertEquals("Comics", containers[1].name)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v1/libraries"), "Pfad war: ${req.path}")
    }

    @Test
    fun `browse mit containerIds setzt library_id-Filter`() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[],"last":true,"number":0,"totalPages":0}"""))
        source().browse(page = 0, filter = SourceFilter(containerIds = listOf("L1", "L2")))
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("library_id=L1"), "Pfad war: ${req.path}")
        assertTrue(req.path!!.contains("library_id=L2"), "Pfad war: ${req.path}")
    }
}
