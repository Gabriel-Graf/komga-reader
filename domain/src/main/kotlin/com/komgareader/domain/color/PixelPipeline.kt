package com.komgareader.domain.color

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
