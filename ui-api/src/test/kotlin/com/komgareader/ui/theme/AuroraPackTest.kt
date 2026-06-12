package com.komgareader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuroraPackTest {
    @Test fun `id ist aurora`() = assertEquals("aurora", AuroraPack.id)

    @Test fun `dark colorScheme trägt Slate-Grey + Cobalt`() {
        val cs = AuroraPack.colorScheme(dark = true)
        assertEquals(Color(0xFF15171C), cs.background)
        assertEquals(Color(0xFF1C1F26), cs.surface)
        assertEquals(Cobalt, cs.primary)
    }

    @Test fun `light colorScheme trägt Deeper-Grey + dunkles Dock`() {
        val cs = AuroraPack.colorScheme(dark = false)
        assertEquals(Color(0xFFCDD1D9), cs.background)
        assertEquals(Color(0xFF959CAA), cs.surfaceVariant)
        assertEquals(Cobalt, cs.primary)
    }

    @Test fun `designTokens = LCD-Klasse, Cobalt-Akzent, 16dp, Schatten`() {
        val t = AuroraPack.designTokens(dark = true)
        assertEquals(Cobalt, t.accent)
        assertEquals(16.dp, t.cornerRadius)
        assertTrue(t.usesShadows)
        // dark-Flag ändert die Aurora-Tokens nicht (LCD, statisch) — gegen naives Wiring absichern.
        assertEquals(Cobalt, AuroraPack.designTokens(dark = false).accent)
        assertTrue(AuroraPack.designTokens(dark = false).usesShadows)
    }

    @Test fun `shapes sind soft 16dp medium`() =
        assertEquals(RoundedCornerShape(16.dp), AuroraPack.shapes.medium)
}
