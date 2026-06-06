package com.komgareader.source.opds

import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpdsSourceTest {

    private val server = MockWebServer()

    private fun source(): OpdsSource =
        OpdsSourceFactory.create(name = "Test-Katalog", catalogUrl = server.url("/opds/v1.2/catalog").toString())

    @AfterTest fun tearDown() = server.shutdown()

    private val exampleFeed = """<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <entry><title>Vinland Saga 01</title><id>urn:vs:1</id>
    <link rel="http://opds-spec.org/image/thumbnail" href="/cover/1.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition" href="/dl/1.cbz" type="application/x-cbz"/></entry>
  <entry><title>Mistborn</title><id>urn:mb:1</id>
    <link rel="http://opds-spec.org/acquisition" href="/dl/mb.epub" type="application/epub+zip"/></entry>
</feed>"""

    @Test
    fun `browse parst zwei Series aus dem Feed`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))

        val result = source().browse(page = 0, filter = SourceFilter())

        assertEquals(2, result.items.size)
        assertEquals("Vinland Saga 01", result.items[0].title)
        assertEquals("urn:vs:1", result.items[0].remoteId)
        assertEquals("Mistborn", result.items[1].title)
        assertEquals("urn:mb:1", result.items[1].remoteId)
    }

    @Test
    fun `browse setzt coverUrl absolut`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))

        val result = source().browse(page = 0, filter = SourceFilter())

        val coverUrl = result.items[0].coverUrl
        assertTrue(coverUrl != null && coverUrl.startsWith("http"), "coverUrl sollte absolut sein: $coverUrl")
        assertTrue(coverUrl.endsWith("/cover/1.jpg"), "coverUrl: $coverUrl")
    }

    @Test
    fun `browse hat hasNextPage false (MVP)`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))

        val result = source().browse(page = 0, filter = SourceFilter())

        assertEquals(false, result.hasNextPage)
    }

    @Test
    fun `books findet Buch per remoteId`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))

        val books = source().books("urn:vs:1")

        assertEquals(1, books.size)
        assertEquals("urn:vs:1", books[0].remoteId)
        assertEquals("Vinland Saga 01", books[0].title)
        assertEquals(BookFormat.CBZ, books[0].format)
        assertEquals(0, books[0].pageCount)
    }

    @Test
    fun `downloadFile liefert Bytes des Acquisition-Links`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))
        server.enqueue(MockResponse().setBody("CBZ_INHALT"))

        val bytes = source().downloadFile("urn:vs:1")

        assertEquals("CBZ_INHALT", bytes.decodeToString())
        val feedRequest = server.takeRequest()
        assertTrue(feedRequest.path!!.contains("catalog"))
        val dlRequest = server.takeRequest()
        assertTrue(dlRequest.path!!.endsWith("/dl/1.cbz"), "Download-Pfad: ${dlRequest.path}")
    }

    @Test
    fun `openPage wirft UnsupportedOperationException`() = runTest {
        val ref = com.komgareader.domain.source.PageRef(
            index = 0, bookRemoteId = "urn:vs:1", pageNumber = 1, url = "http://example.com/p1"
        )
        assertFailsWith<UnsupportedOperationException> {
            source().openPage(ref)
        }
    }

    @Test
    fun `pages liefert leere Liste`() = runTest {
        val refs = source().pages("urn:vs:1")
        assertEquals(0, refs.size)
    }
}
