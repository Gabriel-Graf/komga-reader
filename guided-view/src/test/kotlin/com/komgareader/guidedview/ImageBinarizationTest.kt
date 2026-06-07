package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageBinarizationTest {
    private fun page(vararg lum: Int): RenderedPage {
        val px = IntArray(lum.size) { val v = lum[it]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return RenderedPage(lum.size, 1, px)
    }

    @Test
    fun `Luminanz aus ARGB`() {
        assertEquals(0, ImageBinarization.luminance(0xFF000000.toInt()))
        assertEquals(255, ImageBinarization.luminance(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `Otsu trennt zwei klar getrennte Populationen`() {
        val p = page(10, 10, 10, 10, 240, 240, 240, 240)
        val t = ImageBinarization.otsuThreshold(p)
        assertTrue(t in 10..239, "Schwelle war $t")
    }

    @Test
    fun `backgroundMask markiert helle Pixel als Hintergrund`() {
        val p = page(10, 240)
        val mask = ImageBinarization.backgroundMask(p, threshold = 128)
        assertEquals(false, mask[0])
        assertEquals(true, mask[1])
    }
}
