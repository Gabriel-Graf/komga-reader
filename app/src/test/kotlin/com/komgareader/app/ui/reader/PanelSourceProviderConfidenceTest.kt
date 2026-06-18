package com.komgareader.app.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class PanelSourceProviderConfidenceTest {
    @Test
    fun parses_stored_confidence_with_default_fallback() {
        assertEquals(DEFAULT_MIN_CONFIDENCE, resolveMinConfidence(null))
        assertEquals(0.4f, resolveMinConfidence("0.40"))
        assertEquals(DEFAULT_MIN_CONFIDENCE, resolveMinConfidence("garbage"))
        // Numerically valid but out of [0,1] → clamped to the default, not used verbatim.
        assertEquals(DEFAULT_MIN_CONFIDENCE, resolveMinConfidence("1.5"))
        assertEquals(DEFAULT_MIN_CONFIDENCE, resolveMinConfidence("-0.2"))
    }
}
