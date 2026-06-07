package com.komgareader.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals

class ReflowConfigTest {
    @Test fun `default config hat lesbare startwerte`() {
        val c = ReflowConfig.DEFAULT
        assertEquals(1.0f, c.lineHeight)
        assertEquals(TextAlign.JUSTIFY, c.textAlign)
        assertEquals(Hyphenation.Off, c.hyphenation)
    }
}
