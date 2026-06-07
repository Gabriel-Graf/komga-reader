package com.komgareader.domain.color

import com.komgareader.domain.model.ColorProfile
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 256-Eintrag-Lookup für Gamma-Korrektur: out = 255 * (in/255)^(1/gamma).
 * gamma == 1 → Identität. gamma > 1 hebt Mitteltöne (Kaleido wirkt flau/dunkel).
 */
fun buildGammaLut(gamma: Float): IntArray {
    val lut = IntArray(256)
    if (gamma == 1f) {
        for (i in 0..255) lut[i] = i
        return lut
    }
    val invGamma = 1.0 / gamma
    for (i in 0..255) {
        lut[i] = (255.0 * (i / 255.0).pow(invGamma)).roundToInt().coerceIn(0, 255)
    }
    return lut
}

/**
 * Verarbeitet ARGB-Pixel ([px], Format (A<<24)|(R<<16)|(G<<8)|B) in-place mit der vollen
 * Filter-Pipeline: linear (Sat→Kontrast→Helligkeit) → Levels → Gamma → Unsharp-Mask →
 * Dithering. Neutrale Stufen werden übersprungen. Pure Kotlin, ohne Android.
 */
fun applyPixelPipeline(px: IntArray, width: Int, height: Int, profile: ColorProfile) {
    if (!profile.isLinearNeutral) applyLinear(px, profile.saturation, profile.contrast, profile.brightness)
    if (profile.blackPoint > 0f || profile.whitePoint < 1f || profile.gamma != 1f) {
        applyToneLut(px, buildToneLut(profile.blackPoint, profile.whitePoint, profile.gamma))
    }
}

/** Levels (Schwarz-/Weißpunkt-Streckung) gefolgt von Gamma, als kombinierte 256-LUT. */
internal fun buildToneLut(blackPoint: Float, whitePoint: Float, gamma: Float): IntArray {
    val bp = (blackPoint * 255f)
    val wp = (whitePoint * 255f)
    val span = (wp - bp).coerceAtLeast(1f)
    val gammaLut = buildGammaLut(gamma)
    val lut = IntArray(256)
    for (i in 0..255) {
        val leveled = (((i - bp) / span) * 255f).roundToInt().coerceIn(0, 255)
        lut[i] = gammaLut[leveled]
    }
    return lut
}

private fun applyToneLut(px: IntArray, lut: IntArray) {
    for (idx in px.indices) {
        val p = px[idx]
        val a = p and -0x1000000
        val r = lut[(p shr 16) and 0xFF]
        val g = lut[(p shr 8) and 0xFF]
        val b = lut[p and 0xFF]
        px[idx] = a or (r shl 16) or (g shl 8) or b
    }
}

/** Wendet die lineare ColorMatrix (siehe [buildColorMatrix]) pro Pixel an, geklemmt 0..255. */
private fun applyLinear(px: IntArray, saturation: Float, contrast: Float, brightness: Float) {
    val m = buildColorMatrix(saturation, contrast, brightness)
    for (idx in px.indices) {
        val p = px[idx]
        val a = p and -0x1000000
        val rIn = (p shr 16) and 0xFF
        val gIn = (p shr 8) and 0xFF
        val bIn = p and 0xFF
        val r = (m[0] * rIn + m[1] * gIn + m[2] * bIn + m[4]).roundToInt().coerceIn(0, 255)
        val g = (m[5] * rIn + m[6] * gIn + m[7] * bIn + m[9]).roundToInt().coerceIn(0, 255)
        val b = (m[10] * rIn + m[11] * gIn + m[12] * bIn + m[14]).roundToInt().coerceIn(0, 255)
        px[idx] = a or (r shl 16) or (g shl 8) or b
    }
}
