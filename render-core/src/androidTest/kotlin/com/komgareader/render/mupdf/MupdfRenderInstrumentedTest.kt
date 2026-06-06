package com.komgareader.render.mupdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E auf dem Gerät: rendert je eine echte Fixture pro Format mit MuPDF und prüft,
 * dass Seite 0 plausible Maße hat und nicht leer ist (dunkle Pixel vorhanden).
 */
@RunWith(AndroidJUnit4::class)
class MupdfRenderInstrumentedTest {

    private val factory = MupdfDocumentFactory()

    private fun assetBytes(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    private fun renderFirstPage(asset: String, hint: String) {
        factory.open(assetBytes(asset), hint).use { doc ->
            assertTrue("Seitenzahl > 0 für $asset", doc.pageCount() > 0)
            val page = doc.renderPage(index = 0, zoom = 2f, rotation = 0)
            assertTrue("Breite > 100 für $asset", page.width > 100)
            assertTrue("Höhe > 100 für $asset", page.height > 100)
            val darkPixels = page.pixels.count { argb ->
                val r = (argb shr 16) and 0xff; val g = (argb shr 8) and 0xff; val b = argb and 0xff
                (r + g + b) / 3 < 80
            }
            assertTrue("nicht leer (dunkle Pixel) für $asset, war $darkPixels", darkPixels > 300)
        }
    }

    @Test fun rendert_cbz() = renderFirstPage("sample.cbz", ".cbz")
    @Test fun rendert_pdf() = renderFirstPage("sample.pdf", ".pdf")
    @Test fun rendert_epub() = renderFirstPage("sample.epub", ".epub")
}
