package com.komgareader.domain.usecase

/**
 * Fraction (0f..1f) of ARGB [pixels] that are effectively grayscale: channel span
 * `max(r,g,b) - min(r,g,b)` below [satEps]. Pure; usable on any pixel buffer. Empty
 * buffer yields 0f (no evidence either way — callers treat as "undecidable").
 */
fun measureGrayFraction(pixels: IntArray, satEps: Int = 16): Float {
    if (pixels.isEmpty()) return 0f
    var gray = 0
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val span = maxOf(r, g, b) - minOf(r, g, b)
        if (span < satEps) gray++
    }
    return gray.toFloat() / pixels.size
}
