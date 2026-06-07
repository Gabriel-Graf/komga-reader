package com.komgareader.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reiner Mapper-Test: die persistierten Primitive (Strings/Floats aus den
 * Settings) werden verlustfrei und engine-neutral in eine [ReflowConfig]
 * übersetzt. Getestet: Defaults, jeder Preset/Enum-Zweig und der Off-Fall.
 */
class NovelSettingsTest {

    @Test fun `defaults ergeben die DEFAULT-aequivalente Config`() {
        val cfg = NovelSettings().toReflowConfig()
        assertEquals(1.0f, cfg.fontSizeEm)
        assertEquals(1.0f, cfg.lineHeight)
        assertEquals(Margins.NORMAL, cfg.margin)
        assertEquals("DejaVuSans", cfg.fontFamily)
        assertEquals(TextAlign.JUSTIFY, cfg.textAlign)
        assertEquals(Hyphenation.Off, cfg.hyphenation)
    }

    @Test fun `font- und zeilengroesse werden durchgereicht`() {
        val cfg = NovelSettings(fontSizeEm = 1.4f, lineHeight = 1.5f).toReflowConfig()
        assertEquals(1.4f, cfg.fontSizeEm)
        assertEquals(1.5f, cfg.lineHeight)
    }

    @Test fun `margin-preset NARROW ergibt schmale Raender`() {
        val cfg = NovelSettings(marginPreset = "NARROW").toReflowConfig()
        assertEquals(Margins(12, 12, 12, 12), cfg.margin)
    }

    @Test fun `margin-preset NORMAL ergibt normale Raender`() {
        val cfg = NovelSettings(marginPreset = "NORMAL").toReflowConfig()
        assertEquals(Margins(24, 24, 24, 24), cfg.margin)
    }

    @Test fun `margin-preset WIDE ergibt breite Raender`() {
        val cfg = NovelSettings(marginPreset = "WIDE").toReflowConfig()
        assertEquals(Margins(48, 48, 48, 48), cfg.margin)
    }

    @Test fun `unbekanntes margin-preset faellt auf NORMAL zurueck`() {
        val cfg = NovelSettings(marginPreset = "ХYZ").toReflowConfig()
        assertEquals(Margins.NORMAL, cfg.margin)
    }

    @Test fun `textAlign LEFT wird zum LEFT-Enum`() {
        val cfg = NovelSettings(textAlign = "LEFT").toReflowConfig()
        assertEquals(TextAlign.LEFT, cfg.textAlign)
    }

    @Test fun `textAlign JUSTIFY wird zum JUSTIFY-Enum`() {
        val cfg = NovelSettings(textAlign = "JUSTIFY").toReflowConfig()
        assertEquals(TextAlign.JUSTIFY, cfg.textAlign)
    }

    @Test fun `leere Trennsprache ergibt Hyphenation Off`() {
        val cfg = NovelSettings(hyphenationLang = "").toReflowConfig()
        assertEquals(Hyphenation.Off, cfg.hyphenation)
    }

    @Test fun `gesetzte Trennsprache ergibt Hyphenation Language`() {
        val cfg = NovelSettings(hyphenationLang = "de").toReflowConfig()
        assertEquals(Hyphenation.Language("de"), cfg.hyphenation)
    }

    @Test fun `fontFamily wird durchgereicht`() {
        val cfg = NovelSettings(fontFamily = "Literata").toReflowConfig()
        assertEquals("Literata", cfg.fontFamily)
    }
}
