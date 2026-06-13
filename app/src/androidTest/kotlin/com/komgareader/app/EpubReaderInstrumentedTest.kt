package com.komgareader.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.render.mupdf.MupdfDocumentFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubReaderInstrumentedTest {

    @Test
    fun rendert_epub_von_komga() = runTest {
        val source = KomgaSourceProvider().from(LocalTestServer.config(name = "T"))!!
        val bytes = source.downloadFile("0QKW4K6NW233C")     // Novels/mistborn.epub
        assertTrue("epub bytes", bytes.size > 500)
        val doc = MupdfDocumentFactory().open(bytes, ".epub")
        assertTrue("seiten > 0", doc.pageCount() > 0)
        val page = doc.renderPage(0, 2f, 0)
        val dark = page.pixels.count { pixel ->
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            (r + g + b) / 3 < 80
        }
        assertTrue("nicht leer: $dark", dark > 50)
        doc.close()
    }
}
