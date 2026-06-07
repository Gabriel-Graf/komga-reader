package com.komgareader.domain.color

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
}
