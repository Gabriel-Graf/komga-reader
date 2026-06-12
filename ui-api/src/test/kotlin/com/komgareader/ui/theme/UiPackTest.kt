package com.komgareader.ui.theme

import androidx.compose.ui.graphics.Color
import com.komgareader.domain.model.DisplayBehavior
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * `packFor` ist die pure Auswahl Geräteklasse → [UiPack] über die zwei orthogonalen Achsen. Geprüft
 * wird, dass jede der drei realen Klassen ihr eigenes Built-in-Pack bekommt und die sinnlose Kombi
 * `(motion, !accent)` sicher auf mono fällt — die einzige Invariante ohne anderes Netz.
 */
class UiPackTest {

    private val mono = DisplayBehavior(allowsMotion = false, allowsAccentColor = false)
    private val kaleido = DisplayBehavior(allowsMotion = false, allowsAccentColor = true)
    private val lcd = DisplayBehavior(allowsMotion = true, allowsAccentColor = true)

    @Test
    fun `jede geräteklasse bekommt ihr eigenes pack`() {
        assertSame(MonoEinkPack, packFor(mono))
        assertSame(KaleidoPack, packFor(kaleido))
        assertSame(AuroraPack, packFor(lcd))
    }

    @Test
    fun `sinnlose kombi (bewegung, kein akzent) fällt sicher auf mono`() {
        val nonsensical = DisplayBehavior(allowsMotion = true, allowsAccentColor = false)
        assertSame(MonoEinkPack, packFor(nonsensical))
    }

    @Test
    fun `packs tragen das designToken-mapping ihrer klasse`() {
        // Mono/Kaleido/Lcd delegieren ihre Tokens an designTokensFor der eigenen Klasse (direkt getestet).
        // Die LCD-Klasse wird zur Laufzeit von AuroraPack bedient (eigene Cobalt-Tokens) — s. packFor-Test;
        // LcdPack bleibt hier als eigenständiges Pack mit konsistentem Mapping geprüft.
        assertEquals(designTokensFor(mono, dark = false), MonoEinkPack.designTokens(false))
        assertEquals(designTokensFor(kaleido, dark = false), KaleidoPack.designTokens(false))
        assertEquals(designTokensFor(lcd, dark = true), LcdPack.designTokens(true))
    }

    @Test
    fun `registry löst packs per id auf und kennt unbekannte nicht`() {
        assertSame(MonoEinkPack, UiPackRegistry.byId("mono-eink"))
        assertSame(KaleidoPack, UiPackRegistry.byId("kaleido"))
        assertSame(AuroraPack, UiPackRegistry.byId("aurora"))
        assertSame(LcdPack, UiPackRegistry.byId("lcd"))
        assertEquals(null, UiPackRegistry.byId("does-not-exist"))
    }

    @Test
    fun `registry forBehavior liefert dasselbe pack wie packFor`() {
        // Die Registry ist der Einhängepunkt, ändert aber heute nichts an der Geräteklassen-Auswahl.
        assertSame(packFor(mono), UiPackRegistry.forBehavior(mono))
        assertSame(packFor(kaleido), UiPackRegistry.forBehavior(kaleido))
        assertSame(packFor(lcd), UiPackRegistry.forBehavior(lcd))
    }

    @Test
    fun `registry all listet genau die vier built-in packs`() {
        assertEquals(listOf("mono-eink", "kaleido", "aurora", "lcd"), UiPackRegistry.all().map { it.id })
    }

    @Test
    fun `colorScheme-primary trennt die drei klassen (mono S-W, kaleido gedämpft, lcd voll)`() {
        assertEquals(Color.Black, MonoEinkPack.colorScheme(dark = false).primary)
        assertEquals(Color.White, MonoEinkPack.colorScheme(dark = true).primary)
        assertEquals(AccentMuted, KaleidoPack.colorScheme(dark = false).primary)
        assertEquals(AccentVividLight, LcdPack.colorScheme(dark = false).primary)
        assertEquals(AccentVividDark, LcdPack.colorScheme(dark = true).primary)
    }

    @Test
    fun `look-träger trennen e-ink von lcd (shapes flach-knapp vs weich, typo schwer vs leicht)`() {
        // Beide E-Ink-Packs teilen knappe Kanten; LCD ist weicher.
        assertSame(MonoEinkPack.shapes, KaleidoPack.shapes)
        assertNotSame(MonoEinkPack.shapes, LcdPack.shapes)
        // E-Ink hebt Schriftgewichte (Lesbarkeit ohne Sub-Pixel); LCD bleibt leichter.
        assertNotEquals(
            MonoEinkPack.typography.bodyMedium.fontWeight,
            LcdPack.typography.bodyMedium.fontWeight,
        )
    }
}
