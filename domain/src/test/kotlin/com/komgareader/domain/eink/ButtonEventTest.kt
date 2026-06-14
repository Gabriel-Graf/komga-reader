package com.komgareader.domain.eink

import kotlin.test.Test
import kotlin.test.assertEquals

class ButtonEventTest {
    @Test
    fun `press defaults to SHORT for source compatibility`() {
        val e = ButtonEvent(HardwareButton.PAGE_NEXT)
        assertEquals(PressKind.SHORT, e.press)
    }

    @Test
    fun `long press is carried explicitly`() {
        val e = ButtonEvent(HardwareButton.VOLUME_UP, PressKind.LONG)
        assertEquals(PressKind.LONG, e.press)
        assertEquals(HardwareButton.VOLUME_UP, e.button)
    }
}
