package com.komgareader.domain.model

import com.komgareader.domain.eink.EinkCapabilities

/**
 * Geräte-Verhalten auf zwei **orthogonalen** Achsen — die ein einzelnes `isEink`-Flag nicht
 * trägt (siehe `big-picture-and-goals.md`, Abschnitt „Geräteklassen sind nicht binär"):
 *
 * | Klasse              | allowsMotion | allowsAccentColor |
 * |---------------------|--------------|-------------------|
 * | mono E-Ink          | false        | false             |
 * | Farb-E-Ink (Kaleido)| false        | true (gedämpft)   |
 * | LCD-Phone/-Tablet   | true         | true              |
 *
 * [allowsMotion] = false ⇒ keine Animationen/Übergänge (E-Ink-Ghosting). [allowsAccentColor]
 * = false ⇒ monochrom/Schwarz-Weiß-Akzent. `LocalEinkMode` bleibt als dünne abgeleitete
 * Brücke `!allowsMotion`, damit bestehende Consumer unberührt bleiben.
 */
data class DisplayBehavior(
    val allowsMotion: Boolean,
    val allowsAccentColor: Boolean,
) {
    companion object {
        /** Sicherster Default vor der ersten Auflösung: mono E-Ink (keine Bewegung, kein Akzent). */
        val MONO_EINK = DisplayBehavior(allowsMotion = false, allowsAccentColor = false)
    }
}

/**
 * Leitet das [DisplayBehavior] aus dem User-Anzeige-Modus [mode] und den Geräte-[capabilities]
 * ab. Bewegung folgt der bewussten User-Wahl ([DisplayMode.EINK] = aus), Akzentfarbe folgt der
 * **Hardware** ([EinkCapabilities.canColor]) — so wird Farb-E-Ink (Kaleido: kein Motion, aber
 * Farbe) ausdrückbar, ohne mono-E-Ink oder Smartphone zu verändern.
 */
fun displayBehaviorFor(mode: DisplayMode, capabilities: EinkCapabilities): DisplayBehavior =
    when (mode) {
        DisplayMode.SMARTPHONE -> DisplayBehavior(allowsMotion = true, allowsAccentColor = true)
        DisplayMode.EINK -> DisplayBehavior(allowsMotion = false, allowsAccentColor = capabilities.canColor)
    }
