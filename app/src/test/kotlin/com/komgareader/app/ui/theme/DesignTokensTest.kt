package com.komgareader.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.komgareader.domain.model.DisplayBehavior
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `designTokensFor` ist die pure Abbildung Geräteklasse → Skin-Token. Die drei Klassen trennen
 * über die zwei orthogonalen Achsen: mono `(!motion,!accent)`, Kaleido `(!motion,accent)`,
 * LCD `(motion,accent)`. Geprüft wird das **Mapping** gegen die benannten Konstanten — nicht
 * konkrete Hex-Werte, damit die Marken-Akzent-Identität (P0.2) frei tunebar bleibt.
 */
class DesignTokensTest {

    private val mono = DisplayBehavior(allowsMotion = false, allowsAccentColor = false)
    private val kaleido = DisplayBehavior(allowsMotion = false, allowsAccentColor = true)
    private val lcd = DisplayBehavior(allowsMotion = true, allowsAccentColor = true)

    @Test
    fun `mono eink ist schwarz-weiss, flach, ohne elevation`() {
        val light = designTokensFor(mono, dark = false)
        assertEquals(Color.Black, light.accent)
        assertEquals(Color.White, light.onAccent)
        assertEquals(false, light.usesShadows)
        assertEquals(0.dp, light.cardElevation)
        assertEquals(Color.White, designTokensFor(mono, dark = true).accent)
        assertEquals(Color.Black, designTokensFor(mono, dark = true).onAccent)
    }

    @Test
    fun `kaleido traegt gedaempften akzent, bleibt aber flach (e-ink)`() {
        val k = designTokensFor(kaleido, dark = false)
        assertEquals(AccentMuted, k.accent)
        assertEquals(AccentMuted, designTokensFor(kaleido, dark = true).accent)
        assertEquals(false, k.usesShadows)
        assertEquals(0.dp, k.cardElevation)
    }

    @Test
    fun `lcd traegt vollen akzent, darf schatten und weichere kanten`() {
        val light = designTokensFor(lcd, dark = false)
        assertEquals(AccentVividLight, light.accent)
        assertEquals(AccentVividDark, designTokensFor(lcd, dark = true).accent)
        assertEquals(true, light.usesShadows)
        assertTrue(light.cardElevation > 0.dp) { "LCD soll Elevation haben" }
        assertTrue(light.cornerRadius > designTokensFor(mono, dark = false).cornerRadius) {
            "LCD-Kanten sollen weicher sein als E-Ink"
        }
    }

    @Test
    fun `akzent-achse ist von der bewegungs-achse unabhaengig`() {
        // mono und kaleido teilen !motion, unterscheiden sich aber im Akzent → die Achsen sind getrennt.
        val monoAccent = designTokensFor(mono, dark = false).accent
        val kaleidoAccent = designTokensFor(kaleido, dark = false).accent
        assertTrue(monoAccent != kaleidoAccent) { "Akzent-Achse darf nicht an Bewegung gekoppelt sein" }
    }

    @Test
    fun `sinnlose kombi (bewegung, kein akzent) faellt sicher auf mono`() {
        // (motion, !accent) ist keine reale Geräteklasse; DesignTokens dokumentiert den sicheren
        // Fallback auf den mono-Zweig. Schützt die Invariante gegen ein Umordnen der when-Arme.
        val nonsensical = DisplayBehavior(allowsMotion = true, allowsAccentColor = false)
        assertEquals(designTokensFor(mono, dark = false), designTokensFor(nonsensical, dark = false))
        assertEquals(designTokensFor(mono, dark = true), designTokensFor(nonsensical, dark = true))
    }
}
