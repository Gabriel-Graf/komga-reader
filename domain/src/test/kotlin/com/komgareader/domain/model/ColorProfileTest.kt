package com.komgareader.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorProfileTest {

    private fun base() = ColorProfile(
        id = 9, name = "T", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = false,
    )

    @Test
    fun `neutrales Profil ist isNeutral und braucht keine Pixel-Pipeline`() {
        val p = base()
        assertTrue(p.isLinearNeutral)
        assertFalse(p.needsPixelPipeline)
        assertTrue(p.isNeutral)
    }

    @Test
    fun `nur lineare Werte gesetzt braucht keine Pixel-Pipeline`() {
        val p = base().copy(saturation = 1.4f)
        assertFalse(p.isLinearNeutral)
        assertFalse(p.needsPixelPipeline)
        assertFalse(p.isNeutral)
    }

    @Test
    fun `Gamma ungleich 1 braucht die Pixel-Pipeline`() {
        assertTrue(base().copy(gamma = 1.2f).needsPixelPipeline)
    }

    @Test
    fun `Levels Unsharp und Dither lösen die Pixel-Pipeline aus`() {
        assertTrue(base().copy(blackPoint = 0.05f).needsPixelPipeline)
        assertTrue(base().copy(whitePoint = 0.9f).needsPixelPipeline)
        assertTrue(base().copy(sharpenAmount = 0.5f).needsPixelPipeline)
        assertTrue(base().copy(ditherMode = DitherMode.FLOYD_STEINBERG).needsPixelPipeline)
    }
}
