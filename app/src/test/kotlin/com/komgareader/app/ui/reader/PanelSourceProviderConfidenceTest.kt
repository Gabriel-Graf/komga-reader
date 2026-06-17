package com.komgareader.app.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class PanelSourceProviderConfidenceTest {
    @Test
    fun parses_stored_confidence_with_default_fallback() {
        assertEquals(0.25f, resolveMinConfidence(null))
        assertEquals(0.4f, resolveMinConfidence("0.40"))
        assertEquals(0.25f, resolveMinConfidence("garbage"))
    }
}
