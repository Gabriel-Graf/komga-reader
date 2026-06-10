package com.komgareader.domain.model

import com.komgareader.domain.eink.EinkCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Beweist die **zwei orthogonalen Achsen** der Geräteklasse (Bewegung ⟂ Akzentfarbe).
 * Ein einzelnes `isEink`-Boolean kann mono-E-Ink, Farb-E-Ink (Kaleido) und LCD nicht
 * unterscheiden — [displayBehaviorFor] leitet beide Flags getrennt ab.
 */
class DisplayBehaviorTest {

    private val mono = EinkCapabilities(hasEink = true, canColor = false, canInvert = true)
    private val kaleido = EinkCapabilities(hasEink = true, canColor = true, canInvert = true)
    private val lcd = EinkCapabilities(hasEink = false, canColor = true, canInvert = true)

    @Test fun `mono e-ink - keine Bewegung, keine Akzentfarbe`() {
        assertEquals(
            DisplayBehavior(allowsMotion = false, allowsAccentColor = false),
            displayBehaviorFor(DisplayMode.EINK, mono),
        )
    }

    @Test fun `e-ink-modus ist monochrom - auch auf Kaleido kein Akzent (User-Entscheidung)`() {
        // E-Ink-Modus = Schwarz/Weiß-Akzent, unabhängig von der Farbfähigkeit der Hardware.
        // Akzentfarbe nur im Smartphone-Modus. (Cover-Farbe regelt der Farbfilter separat.)
        assertEquals(
            DisplayBehavior(allowsMotion = false, allowsAccentColor = false),
            displayBehaviorFor(DisplayMode.EINK, kaleido),
        )
    }

    @Test fun `smartphone - Bewegung und Akzentfarbe, unabhaengig von den caps`() {
        assertEquals(
            DisplayBehavior(allowsMotion = true, allowsAccentColor = true),
            displayBehaviorFor(DisplayMode.SMARTPHONE, mono),
        )
        assertEquals(
            DisplayBehavior(allowsMotion = true, allowsAccentColor = true),
            displayBehaviorFor(DisplayMode.SMARTPHONE, lcd),
        )
    }
}
