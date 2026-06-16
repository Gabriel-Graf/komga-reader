package com.komgareader.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        // Default = registrierter Familienname der ersten gebündelten Schrift ("DejaVu Sans").
        assertEquals(NovelFonts.DEFAULT, cfg.fontFamily)
        assertEquals("DejaVu Sans", cfg.fontFamily)
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
        assertEquals(Margins(25, 25, 25, 25), cfg.margin)
    }

    @Test fun `margin-preset WIDE ergibt breite Raender`() {
        val cfg = NovelSettings(marginPreset = "WIDE").toReflowConfig()
        assertEquals(Margins(50, 50, 50, 50), cfg.margin)
    }

    @Test fun `margin-preset SNUG ergibt gelistete 20px-Raender`() {
        val cfg = NovelSettings(marginPreset = NovelSettings.MARGIN_SNUG).toReflowConfig()
        assertEquals(Margins(20, 20, 20, 20), cfg.margin)
    }

    @Test fun `margin-preset RELAXED ergibt gelistete 40px-Raender`() {
        val cfg = NovelSettings(marginPreset = NovelSettings.MARGIN_RELAXED).toReflowConfig()
        assertEquals(Margins(40, 40, 40, 40), cfg.margin)
    }

    @Test fun `margin-preset XWIDE ergibt 60px-Raender`() {
        // NB: 60 must be crengine-listed on device; if it collapses, drop XWIDE to a listed value.
        val cfg = NovelSettings(marginPreset = NovelSettings.MARGIN_XWIDE).toReflowConfig()
        assertEquals(Margins(60, 60, 60, 60), cfg.margin)
    }

    @Test fun `unbekanntes margin-preset faellt auf NORMAL zurueck`() {
        val cfg = NovelSettings(marginPreset = "ХYZ").toReflowConfig()
        assertEquals(Margins.NORMAL, cfg.margin)
    }

    @Test fun `MARGIN_STEPS sind aufsteigend nach Rand-px geordnet`() {
        val px = NovelSettings.MARGIN_STEPS.map { NovelSettings.marginFor(it).left }
        assertEquals(listOf(12, 20, 25, 40, 50, 60), px)
    }

    @Test fun `jeder MARGIN_STEP mappt auf seinen eigenen gelisteten px-Wert`() {
        // Every step resolves to a distinct value via its own when-branch (none silently
        // falls through to the NORMAL default), so the list has as many distinct px as entries.
        val px = NovelSettings.MARGIN_STEPS.map { NovelSettings.marginFor(it).left }
        assertEquals(NovelSettings.MARGIN_STEPS.size, px.distinct().size)
    }

    @Test fun `landmarks sind eine Teilmenge der MARGIN_STEPS`() {
        assertTrue(NovelSettings.MARGIN_STEPS.containsAll(NovelSettings.MARGIN_LANDMARKS))
    }

    @Test fun `NARROW NORMAL WIDE XWIDE sind die benannten landmarks`() {
        assertEquals(
            setOf(
                NovelSettings.MARGIN_NARROW,
                NovelSettings.MARGIN_NORMAL,
                NovelSettings.MARGIN_WIDE,
                NovelSettings.MARGIN_XWIDE,
            ),
            NovelSettings.MARGIN_LANDMARKS,
        )
    }

    @Test fun `presetForMargin ist die verlustfreie Umkehr von marginFor fuer jeden Step`() {
        // Round-trip: every step's px maps back to exactly that step (the bug was SNUG/RELAXED/XWIDE
        // round-tripping to NORMAL, so the margin slider snapped back instead of holding the pick).
        NovelSettings.MARGIN_STEPS.forEach { step ->
            assertEquals(step, NovelSettings.presetForMargin(NovelSettings.marginFor(step)))
        }
    }

    @Test fun `presetForMargin faellt bei unbekannten px auf NORMAL zurueck`() {
        assertEquals(NovelSettings.MARGIN_NORMAL, NovelSettings.presetForMargin(Margins(7, 7, 7, 7)))
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
