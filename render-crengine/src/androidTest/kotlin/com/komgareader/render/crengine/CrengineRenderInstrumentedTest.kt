package com.komgareader.render.crengine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 1c success gate: crengine-ng reflows the bundled sample EPUB and
 * rasterises page 0 into a Bitmap. Asserts the page is non-uniform (real text
 * was drawn — not an all-white / all-one-colour buffer).
 */
@RunWith(AndroidJUnit4::class)
class CrengineRenderInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private fun copyAssetToFile(asset: String): File {
        val out = File(context.cacheDir, asset.substringAfterLast('/'))
        out.parentFile?.mkdirs()
        context.assets.open(asset).use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun assetBytes(name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }

    @Test
    fun rendert_reflowte_epub_seite_mit_text() {
        val fontFile = copyAssetToFile("fonts/DejaVuSans.ttf")
        assertTrue("Font-Manager init + Font registriert", CrengineNative.nativeInit(fontFile.absolutePath))

        val handle = CrengineNative.nativeOpen(assetBytes("sample.epub"), "sample.epub")
        assertTrue("EPUB geöffnet (handle != 0)", handle != 0L)

        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            CrengineNative.nativeRenderPage(handle, 0, width, height, bitmap)

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val distinctColors = pixels.toHashSet().size
            assertTrue("Seite ist nicht uniform (mehrere Farben), war $distinctColors", distinctColors > 1)

            val darkPixels = pixels.count { argb ->
                val r = (argb shr 16) and 0xff
                val g = (argb shr 8) and 0xff
                val b = argb and 0xff
                (r + g + b) / 3 < 80
            }
            assertTrue("gerenderter Text (dunkle Pixel vorhanden), war $darkPixels", darkPixels > 300)
        } finally {
            bitmap.recycle()
            CrengineNative.nativeClose(handle)
        }
    }
}
