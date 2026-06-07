package com.komgareader.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sichert die zentrale Schrift-Registry ab: die gebündelten Familiennamen sind
 * exakt die registrierten FreeType-Namen (Engine wählt per exaktem Abgleich),
 * der Default ist die erste Schrift, und [NovelFonts.byFamily] fällt sauber zurück.
 */
class NovelFontsTest {

    @Test fun `enthaelt die drei gebuendelten Lese-Fonts mit exakten Familiennamen`() {
        val families = NovelFonts.ALL.map { it.family }
        assertEquals(listOf("DejaVu Sans", "Literata", "Bitter"), families)
    }

    @Test fun `jeder Eintrag verweist auf ein Font-Asset und traegt ein Label`() {
        NovelFonts.ALL.forEach { font ->
            assertTrue(font.asset.startsWith("fonts/"), "Asset-Pfad: ${font.asset}")
            assertTrue(font.asset.endsWith(".ttf"), "TTF erwartet: ${font.asset}")
            assertTrue(font.label.isNotBlank(), "Label gesetzt für ${font.family}")
        }
    }

    @Test fun `DEFAULT ist die erste gebuendelte Schrift`() {
        assertEquals("DejaVu Sans", NovelFonts.DEFAULT)
        assertEquals(NovelFonts.ALL.first().family, NovelFonts.DEFAULT)
    }

    @Test fun `byFamily liefert den passenden Eintrag`() {
        assertEquals("Literata", NovelFonts.byFamily("Literata").family)
    }

    @Test fun `byFamily faellt bei unbekannter Familie auf die erste Schrift zurueck`() {
        // z. B. ein veralteter persistierter Wert wie "DejaVuSans" (ohne Leerzeichen).
        assertEquals(NovelFonts.ALL.first(), NovelFonts.byFamily("DejaVuSans"))
    }
}
