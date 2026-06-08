package com.komgareader.app

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import com.komgareader.render.mupdf.MupdfDocumentFactory
import com.komgareader.source.opds.OpdsSourceFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * OPDS-Canary (Lackmustest aus `source-agnostic-integration.md`): browsen **und lesen** über
 * eine **OPDS**-Quelle, end-to-end auf dem Gerät, **ohne eine Zeile Komga-Code** im Pfad.
 *
 * Beweist die Naht: Die Quelle wird nur als [BrowsableSource] über den [SourceManager] benutzt;
 * `downloadFile` liefert die Bytes über den Acquisition-Link; MuPDF (Naht B) rendert sie. OPDS
 * ist in dieser App download-/reflow-only — gelesen wird also über den Download-Pfad, nicht über
 * `openPage`. Genau das deckt dieser Test ab.
 */
@RunWith(AndroidJUnit4::class)
class OpdsCanaryInstrumentedTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    /** Minimale, gültige CBZ (ZIP mit einem PNG) — MuPDF kann sie als 1-seitiges Dokument öffnen. */
    private fun cbzWithOnePage(): ByteArray {
        val png = ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("001.png"))
                zip.write(png)
                zip.closeEntry()
            }
            bytes.toByteArray()
        }
    }

    @Test
    fun opds_browse_and_read_works_without_komga() = runBlocking {
        val catalog = """<?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>Canary Saga 01</title><id>urn:canary:1</id>
                <link rel="http://opds-spec.org/image/thumbnail" href="/cover/1.png" type="image/png"/>
                <link rel="http://opds-spec.org/acquisition" href="/dl/1.cbz" type="application/x-cbz"/>
              </entry>
            </feed>""".trimIndent()

        // browse() liest den Katalog; downloadFile() folgt dem Acquisition-Link → CBZ-Bytes.
        server.enqueue(MockResponse().setBody(catalog))                                  // browse
        server.enqueue(MockResponse().setBody(catalog))                                  // downloadFile: Eintrag auflösen
        server.enqueue(MockResponse().setBody(Buffer().write(cbzWithOnePage())))         // downloadFile: Bytes

        // Die Quelle wird NUR über die Naht benutzt — registriert im SourceManager, getypt als BrowsableSource.
        val sources = SourceManager()
        val opds = OpdsSourceFactory.create(name = "Canary", catalogUrl = server.url("/opds").toString())
        sources.register(opds)
        val source = sources.get(opds.id) as BrowsableSource

        // 1) Browsen über OPDS.
        val series = source.browse(0, com.komgareader.domain.source.SourceFilter()).items
        assertEquals(1, series.size)
        assertEquals("Canary Saga 01", series.first().title)

        // 2) Lesen: Bytes über die Naht, Rendern über MuPDF (Naht B) — kein Komga im Pfad.
        val fileBytes = source.downloadFile(series.first().remoteId)
        val doc = MupdfDocumentFactory().open(fileBytes, ".cbz")
        assertTrue("CBZ sollte mindestens eine Seite haben", doc.pageCount() >= 1)
        val page = doc.renderPage(0, zoom = 1f, rotation = 0)
        assertTrue("gerenderte Seite hat Pixel", page.width > 0 && page.height > 0)
        doc.close()
    }
}
