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

    /**
     * true, wenn der Seitenhintergrund überwiegend hell ist (weißer/heller Hintergrund), sonst dunkel.
     * Strategie: Otsu-Schwelle trennt die Seite in zwei Klassen; wenn der Mittelwert der OBEREN
     * Klasse (hellere Pixel) nahe Weiß (> 200) liegt, ist die obere Klasse der Hintergrund.
     * Liegt er darunter (z. B. ~176 bei hellen Panels auf schwarzem Hintergrund), ist die
     * untere Klasse (dunkle Pixel) der Hintergrund.
     */
    fun isLightBackground(page: RenderedPage): Boolean {
        if (page.pixels.isEmpty()) return true
        val threshold = otsuThreshold(page)
        var sumHigh = 0L; var countHigh = 0
        for (p in page.pixels) {
            val lum = luminance(p)
            if (lum > threshold) { sumHigh += lum; countHigh++ }
        }
        if (countHigh == 0) return true
        val meanHigh = sumHigh / countHigh
        // Obere Klasse nahe Weiß (> 200) → heller Hintergrund; sonst dunkler Hintergrund.
        return meanHigh > 200
    }

    /**
     * Hintergrund-Maske mit Polarität.
     * [lightBackground]=true: helle Pixel (Luminanz > [threshold]) sind Hintergrund (weißer Gutter).
     * [lightBackground]=false: dunkle Pixel (Luminanz ≤ [threshold]) sind Hintergrund (schwarze Gasse).
     * Otsu-Konvention: dunkle Klasse = [0..T], helle Klasse = [T+1..255].
     */
    fun backgroundMask(page: RenderedPage, threshold: Int, lightBackground: Boolean): BooleanArray =
        BooleanArray(page.pixels.size) {
            val lum = luminance(page.pixels[it])
            if (lightBackground) lum > threshold else lum <= threshold
        }

    /** true = Hintergrund (Luminanz > [threshold]). Rückwärts-kompatibel mit hellem Hintergrund. */
    fun backgroundMask(page: RenderedPage, threshold: Int): BooleanArray =
        backgroundMask(page, threshold, lightBackground = true)
}
