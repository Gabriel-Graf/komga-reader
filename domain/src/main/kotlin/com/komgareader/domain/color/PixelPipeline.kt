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
    if (profile.sharpenAmount > 0f) applyUnsharp(px, width, height, profile.sharpenAmount, profile.sharpenRadius)
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

/**
 * Unsharp-Mask: out = clamp(in + amount * (in - blur)). [radius] = Box-Blur-Radius in px.
 * Arbeitet pro Kanal auf einer Blur-Kopie der Eingangswerte (kein In-place-Feedback).
 */
private fun applyUnsharp(px: IntArray, width: Int, height: Int, amount: Float, radius: Int) {
    val n = px.size
    val rIn = IntArray(n); val gIn = IntArray(n); val bIn = IntArray(n)
    for (i in 0 until n) {
        val p = px[i]
        rIn[i] = (p shr 16) and 0xFF; gIn[i] = (p shr 8) and 0xFF; bIn[i] = p and 0xFF
    }
    val rBlur = boxBlur(rIn, width, height, radius)
    val gBlur = boxBlur(gIn, width, height, radius)
    val bBlur = boxBlur(bIn, width, height, radius)
    for (i in 0 until n) {
        val a = px[i] and -0x1000000
        val r = (rIn[i] + amount * (rIn[i] - rBlur[i])).roundToInt().coerceIn(0, 255)
        val g = (gIn[i] + amount * (gIn[i] - gBlur[i])).roundToInt().coerceIn(0, 255)
        val b = (bIn[i] + amount * (bIn[i] - bBlur[i])).roundToInt().coerceIn(0, 255)
        px[i] = a or (r shl 16) or (g shl 8) or b
    }
}

/** Einfacher (separabler) Box-Blur über ein Kanal-Array. */
private fun boxBlur(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
    val tmp = IntArray(src.size)
    val out = IntArray(src.size)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            var sum = 0; var cnt = 0
            for (dx in -radius..radius) {
                val xx = x + dx
                if (xx in 0 until width) { sum += src[row + xx]; cnt++ }
            }
            tmp[row + x] = sum / cnt
        }
    }
    for (y in 0 until height) {
        for (x in 0 until width) {
            var sum = 0; var cnt = 0
            for (dy in -radius..radius) {
                val yy = y + dy
                if (yy in 0 until height) { sum += tmp[yy * width + x]; cnt++ }
            }
            out[y * width + x] = sum / cnt
        }
    }
    return out
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
