package com.komgareader.domain.eink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EinkCapabilitiesTest {
    @Test fun `brightnessRange defaults to null`() {
        val caps = EinkCapabilities(hasEink = false, canColor = true, canInvert = true)
        assertNull(caps.brightnessRange)
    }

    @Test fun `a frontlight device advertises a range`() {
        val caps = EinkCapabilities(hasEink = true, canColor = true, canInvert = true, brightnessRange = 0..255)
        assertEquals(0..255, caps.brightnessRange)
    }
}
