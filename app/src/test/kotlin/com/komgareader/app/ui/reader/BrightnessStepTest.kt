package com.komgareader.app.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrightnessStepTest {
    @Test fun `top of bar is max`() =
        assertEquals(255, brightnessForFraction(yFractionFromTop = 0f, range = 0..255, steps = 16))
    @Test fun `bottom of bar is min`() =
        assertEquals(0, brightnessForFraction(yFractionFromTop = 1f, range = 0..255, steps = 16))
    @Test fun `middle snaps to a discrete step`() =
        assertEquals(127, brightnessForFraction(yFractionFromTop = 0.5f, range = 0..255, steps = 16))
    @Test fun `out of bounds clamps`() {
        assertEquals(255, brightnessForFraction(-0.2f, 0..255, 16))
        assertEquals(0, brightnessForFraction(1.4f, 0..255, 16))
    }
}
