package com.komgareader.source.opds

import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-End-Test: ein simulierter OPDS-Katalog liefert einen Feed → browse →
 * erste Serie → downloadFile → Bytes. Beweist den vollständigen Fluss durch die
 * OPDS-Quelle ohne echtes Netzwerk.
 */
class OpdsE2ETest {

    private val server = MockWebServer()

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `browse bis downloadFile als durchgehender Fluss`() = runTest {
        // 1. Feed für browse
        server.enqueue(MockResponse().setBody("""<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <entry><title>Vinland Saga 01</title><id>urn:vs:1</id>
    <link rel="http://opds-spec.org/image/thumbnail" href="/cover/1.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition" href="/dl/1.cbz" type="application/x-cbz"/></entry>
  <entry><title>Mistborn</title><id>urn:mb:1</id>
    <link rel="http://opds-spec.org/acquisition" href="/dl/mb.epub" type="application/epub+zip"/></entry>
</feed>""").addHeader("Content-Type", "application/atom+xml"))

        // 2. Die eigentliche Datei — kein zweiter Feed-Abruf nötig: browse hat den
        //    Eintrag bereits gecacht, downloadFile löst ihn ohne Re-Fetch auf.
        server.enqueue(MockResponse().setBody("CBZ_DATEI_INHALT"))

        val source = OpdsSourceFactory.create(
            name = "E2E-Katalog",
            catalogUrl = server.url("/opds/v1.2/catalog").toString()
        )

        // browse: zwei Serien werden aus dem Feed gelesen
        val browseResult = source.browse(page = 0, filter = SourceFilter())
        assertEquals(2, browseResult.items.size)
        assertEquals(false, browseResult.hasNextPage)

        val ersteSerie = browseResult.items.first()
        assertEquals("Vinland Saga 01", ersteSerie.title)
        assertEquals("urn:vs:1", ersteSerie.remoteId)
        assertNotNull(ersteSerie.coverUrl)
        assertTrue(ersteSerie.coverUrl!!.contains("/cover/1.jpg"))

        // downloadFile: liefert die rohen Bytes des Acquisition-Links
        val bytes = source.downloadFile(ersteSerie.remoteId)
        assertEquals("CBZ_DATEI_INHALT", bytes.decodeToString())

        // Requests prüfen: nur Feed (browse) + Download — der Eintrags-Cache spart den Re-Fetch.
        val feedRequest = server.takeRequest()
        assertTrue(feedRequest.path!!.contains("catalog"), "Erster Request: ${feedRequest.path}")
        val downloadRequest = server.takeRequest()
        assertTrue(downloadRequest.path!!.endsWith("/dl/1.cbz"), "Download-Pfad: ${downloadRequest.path}")
    }
}
