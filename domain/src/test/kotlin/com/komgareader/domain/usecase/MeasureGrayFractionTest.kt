package com.komgareader.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasureGrayFractionTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun `all-gray pixels yield 1`() {
        val px = intArrayOf(argb(10, 10, 10), argb(200, 200, 200), argb(0, 0, 0))
        assertEquals(1f, measureGrayFraction(px))
    }

    @Test fun `all-colorful pixels yield 0`() {
        val px = intArrayOf(argb(200, 10, 10), argb(10, 200, 10), argb(10, 10, 200))
        assertEquals(0f, measureGrayFraction(px))
    }

    @Test fun `half gray yields one half`() {
        val px = intArrayOf(argb(50, 50, 50), argb(200, 10, 10))
        assertEquals(0.5f, measureGrayFraction(px))
    }

    @Test fun `near-gray within eps counts as gray`() {
        val px = intArrayOf(argb(100, 108, 95)) // span 13 < 16
        assertEquals(1f, measureGrayFraction(px))
    }

    @Test fun `empty array yields zero`() {
        assertEquals(0f, measureGrayFraction(intArrayOf()))
    }
}
