package com.komgareader.app.data

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.data.cover.renderFirstPageCover
import com.komgareader.render.mupdf.MupdfDocumentFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof of the local non-CBZ cover fix: render the first page of a real PDF to PNG cover
 * bytes via MuPDF (the same engine the reader uses for local PDF/CBR/EPUB). Runs on the emulator
 * (MuPDF ships native libs for x86_64 + arm64). Exercises [renderFirstPageCover] — the render glue
 * that the renderer-free `:source-local` cannot provide.
 */
@RunWith(AndroidJUnit4::class)
class LocalCoverRendererInstrumentedTest {

    private fun assetBytes(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    @Test
    fun renders_first_page_cover_for_pdf() {
        val png = renderFirstPageCover(MupdfDocumentFactory(), assetBytes("sample.pdf"), ".pdf")
        assertNotNull("renderFirstPageCover returns PNG bytes for a PDF", png)
        assertTrue("PNG is non-trivial (> 1 KiB)", png!!.size > 1024)
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertNotNull("PNG decodes back to a bitmap", bmp)
        assertTrue("bitmap has real dimensions", bmp.width > 0 && bmp.height > 0)
    }
}
