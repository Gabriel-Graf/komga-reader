package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage

/** Binarisierung einer Seite: Luminanz + adaptive Otsu-Schwelle → Hintergrund-Maske. Reines Kotlin. */
object ImageBinarization {

    /** Wahrgenommene Helligkeit 0..255 aus einem ARGB-Pixel (Rec. 601). */
    fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Otsu-Schwelle: maximiert die Inter-Klassen-Varianz über das Luminanz-Histogramm. */
    fun otsuThreshold(page: RenderedPage): Int {
        val hist = IntArray(256)
        for (p in page.pixels) hist[luminance(p)]++
        val total = page.pixels.size
        if (total == 0) return 127
        var sumAll = 0.0
        for (t in 0..255) sumAll += t.toDouble() * hist[t]
        var sumB = 0.0
        var wB = 0
        var maxVar = -1.0
        var threshold = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) { maxVar = between; threshold = t }
        }
        return threshold
    }

    /** true = Hintergrund (Luminanz > [threshold]). */
    fun backgroundMask(page: RenderedPage, threshold: Int): BooleanArray =
        BooleanArray(page.pixels.size) { luminance(page.pixels[it]) > threshold }
}
