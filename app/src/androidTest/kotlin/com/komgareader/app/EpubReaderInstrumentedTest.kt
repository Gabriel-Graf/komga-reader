package com.komgareader.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.render.mupdf.MupdfDocumentFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubReaderInstrumentedTest {

    @Test
    fun rendert_epub_von_komga() = runTest {
        val source = KomgaSourceProvider().from(
            ServerConfig(
                name = "T",
                baseUrl = "http://10.0.2.2:25600/api/v1/",
                apiKey = "2243c9f4ecc5404992ddf8eba4bf6488",
            ),
        )!!
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
