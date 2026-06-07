package com.komgareader.domain.color

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PixelPipelineTest {

    @Test
    fun `Gamma 1 ergibt die Identitäts-LUT`() {
        val lut = buildGammaLut(1f)
        assertEquals(256, lut.size)
        for (i in 0..255) assertEquals(i, lut[i], "Index $i")
    }

    @Test
    fun `Gamma-LUT fixiert Endpunkte`() {
        val lut = buildGammaLut(2.2f)
        assertEquals(0, lut[0])
        assertEquals(255, lut[255])
    }

    @Test
    fun `Gamma größer 1 hebt die Mitteltöne`() {
        val lut = buildGammaLut(2.2f)
        assertTrue(lut[128] > 128, "lut[128]=${lut[128]} sollte > 128 sein")
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    private fun profile(
        saturation: Float = 1f, contrast: Float = 1f, brightness: Float = 0f,
        blackPoint: Float = 0f, whitePoint: Float = 1f, gamma: Float = 1f,
        sharpenAmount: Float = 0f, sharpenRadius: Int = 1,
        ditherMode: com.komgareader.domain.model.DitherMode = com.komgareader.domain.model.DitherMode.NONE,
        ditherLevels: Int = 16,
    ) = com.komgareader.domain.model.ColorProfile(
        id = 1, name = "p", saturation = saturation, contrast = contrast, brightness = brightness,
        blackPoint = blackPoint, whitePoint = whitePoint, gamma = gamma,
        sharpenAmount = sharpenAmount, sharpenRadius = sharpenRadius,
        ditherMode = ditherMode, ditherLevels = ditherLevels, builtIn = false,
    )

    @Test
    fun `neutrales Profil lässt Pixel unverändert`() {
        val px = intArrayOf(argb(10, 128, 240), argb(0, 0, 0), argb(255, 255, 255))
        val copy = px.copyOf()
        applyPixelPipeline(px, width = 3, height = 1, profile = profile())
        assertTrue(px.contentEquals(copy))
    }

    @Test
    fun `Levels clippt unter Schwarzpunkt auf 0 und über Weißpunkt auf 255`() {
        val px = intArrayOf(argb(40, 128, 230))
        applyPixelPipeline(px, 1, 1, profile(blackPoint = 0.2f, whitePoint = 0.8f))
        assertEquals(0, r(px[0]), "40 < 51 => 0")
        assertEquals(255, b(px[0]), "230 > 204 => 255")
        assertTrue(g(px[0]) in 120..136, "g=${g(px[0])}")
    }

    @Test
    fun `linearer Teil entspricht buildColorMatrix`() {
        val p = profile(saturation = 1.4f, contrast = 1.15f, brightness = 0.05f)
        val m = buildColorMatrix(1.4f, 1.15f, 0.05f)
        val rIn = 90; val gIn = 130; val bIn = 200
        val px = intArrayOf(argb(rIn, gIn, bIn))
        applyPixelPipeline(px, 1, 1, p)
        val expR = (m[0] * rIn + m[1] * gIn + m[2] * bIn + m[4]).roundToInt().coerceIn(0, 255)
        assertEquals(expR, r(px[0]), "R via Matrix")
    }

    @Test
    fun `Gamma hebt einen mittelgrauen Pixel`() {
        val px = intArrayOf(argb(128, 128, 128))
        applyPixelPipeline(px, 1, 1, profile(gamma = 2.2f))
        assertTrue(r(px[0]) > 128 && g(px[0]) > 128 && b(px[0]) > 128)
    }
}
