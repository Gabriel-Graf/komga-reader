package com.komgareader.domain.model

/**
 * Ein benanntes E-Ink-Farbfilter-Profil. Quellen-/geräteneutral: nur Zahlen, die zu einer
 * ColorMatrix werden (siehe [com.komgareader.domain.color.buildColorMatrix]).
 *
 * @param saturation 1.0 = neutral, 0.0 = Graustufen, >1 = kräftiger (Kaleido-Ausgleich).
 * @param contrast   1.0 = neutral; skaliert um den Mittelwert.
 * @param brightness 0.0 = neutral; linearer Offset.
 * @param builtIn    mitgeliefert → nicht editier-/löschbar (nur duplizierbar).
 */
data class ColorProfile(
    val id: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val builtIn: Boolean,
) {
    /** True, wenn das Profil nichts verändert (kein Filter nötig). */
    val isNeutral: Boolean get() = saturation == 1f && contrast == 1f && brightness == 0f

    companion object {
        /** Fallback, wenn kein aktives Profil existiert: kein Filter. */
        val OFF = ColorProfile(id = 1L, name = "Aus", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = true)
    }
}
