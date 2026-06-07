package com.komgareader.domain.model

/**
 * Ein benanntes E-Ink-Farbfilter-Profil. Quellen-/geräteneutral: nur Zahlen.
 * Lineare Felder (saturation/contrast/brightness) werden zur GPU-ColorMatrix
 * (siehe [com.komgareader.domain.color.buildColorMatrix]) — auf Cover UND Reader-Seiten.
 * Phase-2-Felder (Levels/Gamma/Unsharp/Dither) laufen nur beim Lesen durch den
 * Pixel-Kernel (siehe [com.komgareader.domain.color.applyPixelPipeline]).
 *
 * @param saturation 1.0 = neutral, 0.0 = Graustufen, >1 = kräftiger.
 * @param contrast   1.0 = neutral; skaliert um den Mittelwert.
 * @param brightness 0.0 = neutral; linearer Offset.
 * @param blackPoint 0.0 = neutral; Tonwert-Eingang Schwarzpunkt (0.0..0.4).
 * @param whitePoint 1.0 = neutral; Tonwert-Eingang Weißpunkt (0.6..1.0).
 * @param gamma      1.0 = neutral; >1 hebt Mitteltöne.
 * @param sharpenAmount 0.0 = neutral; Stärke der Unsharp-Mask.
 * @param sharpenRadius  Box-Blur-Radius in px (1..3).
 * @param ditherMode  NONE = aus.
 * @param ditherLevels Stufen pro Kanal (2..64), nur wirksam bei ditherMode != NONE.
 * @param builtIn    mitgeliefert → nicht editier-/löschbar.
 */
data class ColorProfile(
    val id: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val blackPoint: Float = 0f,
    val whitePoint: Float = 1f,
    val gamma: Float = 1f,
    val sharpenAmount: Float = 0f,
    val sharpenRadius: Int = 1,
    val ditherMode: DitherMode = DitherMode.NONE,
    val ditherLevels: Int = 16,
    val builtIn: Boolean,
) {
    /** True, wenn die linearen Werte nichts verändern (GPU-Matrix-Pfad, Cover). */
    val isLinearNeutral: Boolean get() = saturation == 1f && contrast == 1f && brightness == 0f

    /** True, wenn mindestens eine Phase-2-Operation gesetzt ist (Reader-Pixel-Kernel nötig). */
    val needsPixelPipeline: Boolean get() =
        blackPoint > 0f || whitePoint < 1f || gamma != 1f ||
            sharpenAmount > 0f || ditherMode != DitherMode.NONE

    /** True, wenn das Profil gar nichts verändert (kein Filter nötig). */
    val isNeutral: Boolean get() = isLinearNeutral && !needsPixelPipeline

    companion object {
        /** Fallback, wenn kein aktives Profil existiert: kein Filter. */
        val OFF = ColorProfile(id = 1L, name = "Aus", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = true)
    }
}
