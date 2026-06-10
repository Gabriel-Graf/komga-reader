package com.komgareader.domain.model

import com.komgareader.domain.eink.EinkCapabilities

/**
 * Geräte-Verhalten auf zwei **orthogonalen** Achsen — die ein einzelnes `isEink`-Flag nicht
 * trägt (siehe `big-picture-and-goals.md`, Abschnitt „Geräteklassen sind nicht binär"):
 *
 * | Klasse              | allowsMotion | allowsAccentColor |
 * |---------------------|--------------|-------------------|
 * | mono E-Ink          | false        | false             |
 * | Farb-E-Ink (Kaleido)| false        | false             |
 * | LCD-Phone/-Tablet   | true         | true              |
 *
 * [allowsMotion] = false ⇒ keine Animationen/Übergänge (E-Ink-Ghosting). [allowsAccentColor]
 * = false ⇒ monochrom/Schwarz-Weiß-Akzent. `LocalEinkMode` bleibt als dünne abgeleitete
 * Brücke `!allowsMotion`, damit bestehende Consumer unberührt bleiben.
 *
 * **User-Entscheidung (auf echter Go Color 7 verifiziert):** der **E-Ink-Modus ist monochrom** —
 * auch auf Kaleido ist die UI-Akzentfarbe **Schwarz** (gedämpftes Indigo wirkte falsch). Akzentfarbe
 * gibt es nur im Smartphone-Modus. Die Achse [allowsAccentColor] bleibt im Modell (für ein künftiges
 * Farb-E-Ink-Profil), wird vom E-Ink-Modus aber nicht mehr gesetzt. Die **Cover-Farbe** auf Kaleido
 * regelt unabhängig der Farbfilter, nicht dieser Akzent.
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
 * Leitet das [DisplayBehavior] aus dem User-Anzeige-Modus [mode] ab. Beide Achsen folgen der
 * bewussten User-Wahl: **E-Ink-Modus = keine Bewegung, monochrom (Schwarz-Akzent)** — auch auf
 * farbfähiger Hardware; Smartphone-Modus = Bewegung + Akzentfarbe. [capabilities] fließt bewusst
 * **nicht** mehr in die Akzentfarbe ein (E-Ink soll Schwarz sein, nicht gedämpftes Indigo).
 */
fun displayBehaviorFor(mode: DisplayMode, @Suppress("UNUSED_PARAMETER") capabilities: EinkCapabilities): DisplayBehavior =
    when (mode) {
        DisplayMode.SMARTPHONE -> DisplayBehavior(allowsMotion = true, allowsAccentColor = true)
        DisplayMode.EINK -> DisplayBehavior(allowsMotion = false, allowsAccentColor = false)
    }
