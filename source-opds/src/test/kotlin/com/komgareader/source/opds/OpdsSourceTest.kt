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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    private val pseFeed = """<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:pse="http://vaemendis.net/opds-pse/ns">
  <entry><title>Berserk 01</title><id>urn:bk:1</id>
    <link rel="http://opds-spec.org/image/thumbnail" href="/cover/bk.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition" href="/dl/bk.cbz" type="application/x-cbz"/>
    <link rel="http://vaemendis.net/opds-pse/stream" href="/books/bk/pages/{pageNumber}?zero_based=true" type="image/jpeg" pse:count="3"/>
  </entry>
</feed>"""

    /** Zwei PSE-Bücher mit unterschiedlichen Stream-Vorlagen — prüft die Cache-Disambiguierung pro remoteId. */
    private val twoPseFeed = """<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:pse="http://vaemendis.net/opds-pse/ns">
  <entry><title>Berserk 01</title><id>urn:bk:1</id>
    <link rel="http://vaemendis.net/opds-pse/stream" href="/books/bk/pages/{pageNumber}?zero_based=true" type="image/jpeg" pse:count="3"/>
  </entry>
  <entry><title>Gantz 01</title><id>urn:gz:1</id>
    <link rel="http://vaemendis.net/opds-pse/stream" href="/comics/gz/img/{pageNumber}.jpg" type="image/jpeg" pse:count="5"/>
  </entry>
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
    fun `openPage wirft UnsupportedOperationException ohne PSE`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))
        val ref = com.komgareader.domain.source.PageRef(
            index = 0, bookRemoteId = "urn:vs:1", pageNumber = 1, url = ""
        )
        val ex = assertFailsWith<UnsupportedOperationException> {
            source().openPage(ref)
        }
        // Aus dem richtigen Grund geworfen (fehlendes PSE), nicht durch einen Feed-Fehler.
        assertTrue(ex.message!!.contains("PSE"), "Message sollte PSE nennen: ${ex.message}")
    }

    @Test
    fun `openPage holt Vorlage bei kaltem Cache nach`() = runTest {
        // Kein vorheriges pages() → Cache kalt → openPage muss den Feed selbst holen (Fallback-Zweig).
        server.enqueue(MockResponse().setBody(pseFeed).addHeader("Content-Type", "application/atom+xml"))
        server.enqueue(MockResponse().setBody("KALT_SEITE"))

        val ref = com.komgareader.domain.source.PageRef(
            index = 0, bookRemoteId = "urn:bk:1", pageNumber = 1, url = ""
        )
        val bytes = source().openPage(ref)

        assertEquals("KALT_SEITE", bytes.decodeToString())
        server.takeRequest() // Feed-Nachholung (Fallback)
        val pageRequest = server.takeRequest()
        assertTrue(pageRequest.path!!.endsWith("/books/bk/pages/0?zero_based=true"), "Seiten-Pfad: ${pageRequest.path}")
    }

    @Test
    fun `openPage wählt die richtige Vorlage bei mehreren PSE-Einträgen`() = runTest {
        server.enqueue(MockResponse().setBody(twoPseFeed).addHeader("Content-Type", "application/atom+xml"))
        server.enqueue(MockResponse().setBody("GZ_SEITE"))

        val src = source()
        src.pages("urn:bk:1") // wärmt den Cache mit BEIDEN Vorlagen
        val ref = com.komgareader.domain.source.PageRef(
            index = 0, bookRemoteId = "urn:gz:1", pageNumber = 1, url = ""
        )
        val bytes = src.openPage(ref)

        assertEquals("GZ_SEITE", bytes.decodeToString())
        server.takeRequest() // Feed (durch pages)
        val pageRequest = server.takeRequest()
        assertTrue(pageRequest.path!!.endsWith("/comics/gz/img/0.jpg"), "Seiten-Pfad: ${pageRequest.path}")
    }

    @Test
    fun `pages ohne PSE liefert leere Liste`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))
        val refs = source().pages("urn:vs:1")
        assertEquals(0, refs.size)
    }

    @Test
    fun `pages liefert PSE-Seitenrefs mit 0-basierten URLs`() = runTest {
        server.enqueue(MockResponse().setBody(pseFeed).addHeader("Content-Type", "application/atom+xml"))

        val refs = source().pages("urn:bk:1")

        assertEquals(3, refs.size)
        assertEquals(listOf(1, 2, 3), refs.map { it.pageNumber })
        assertTrue(refs[0].url.endsWith("/books/bk/pages/0?zero_based=true"), "url[0]: ${refs[0].url}")
        assertTrue(refs[1].url.endsWith("/books/bk/pages/1?zero_based=true"), "url[1]: ${refs[1].url}")
        assertTrue(refs[2].url.endsWith("/books/bk/pages/2?zero_based=true"), "url[2]: ${refs[2].url}")
    }

    @Test
    fun `openPage streamt PSE-Seite 0-basiert`() = runTest {
        server.enqueue(MockResponse().setBody(pseFeed).addHeader("Content-Type", "application/atom+xml"))
        server.enqueue(MockResponse().setBody("SEITE_2_BYTES"))

        val src = source()
        src.pages("urn:bk:1") // wärmt den PSE-Template-Cache
        val ref = com.komgareader.domain.source.PageRef(
            index = 1, bookRemoteId = "urn:bk:1", pageNumber = 2, url = ""
        )
        val bytes = src.openPage(ref)

        assertEquals("SEITE_2_BYTES", bytes.decodeToString())
        server.takeRequest() // Feed-Abruf (durch pages)
        val pageRequest = server.takeRequest()
        assertTrue(pageRequest.path!!.endsWith("/books/bk/pages/1?zero_based=true"), "Seiten-Pfad: ${pageRequest.path}")
    }

    @Test
    fun `books übernimmt pseCount als pageCount`() = runTest {
        server.enqueue(MockResponse().setBody(pseFeed).addHeader("Content-Type", "application/atom+xml"))

        val books = source().books("urn:bk:1")

        assertEquals(3, books[0].pageCount)
    }

    @Test
    fun `feed-Request trägt Authorization-Header wenn Credentials gesetzt`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))
        val source = OpdsSourceFactory.create(
            name = "Auth-Test",
            catalogUrl = server.url("/opds/v1.2/catalog").toString(),
            username = "user",
            password = "secret",
        )
        source.browse(page = 0, filter = SourceFilter())
        val authHeader = server.takeRequest().getHeader("Authorization")
        assertNotNull(authHeader, "Authorization-Header fehlt bei gesetzten Credentials")
        assertEquals(okhttp3.Credentials.basic("user", "secret"), authHeader)
    }

    @Test
    fun `feed-Request ohne Credentials hat keinen Authorization-Header`() = runTest {
        server.enqueue(MockResponse().setBody(exampleFeed).addHeader("Content-Type", "application/atom+xml"))
        source().browse(page = 0, filter = SourceFilter())
        assertNull(server.takeRequest().getHeader("Authorization"), "Authorization-Header darf ohne Credentials nicht gesetzt sein")
    }
}
