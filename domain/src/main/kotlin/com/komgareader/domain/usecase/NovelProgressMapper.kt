package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReadProgress
import kotlin.math.roundToInt

/**
 * Reiner Mapper zwischen dem geräteunabhängigen Roman-Leseanteil (0.0..1.0) und dem
 * Server-Sync- bzw. Wiederaufnahme-Modell.
 *
 * Roman-Layouts sind reflowt: die „Seite" hängt vom Viewport und der Typografie ab,
 * taugt also NICHT als geräteübergreifender Sync-Wert. Lokal ist der crengine-Xpointer
 * (Anker) die exakte Wahrheit; zu Komga wird nur der grobe Anteil als Prozent gesynct.
 * Komgas REST-Fortschritt ist seiten-basiert ([ReadProgress.page]/[ReadProgress.totalPages]),
 * darum wird der Anteil auf ein festes Prozent-Raster abgebildet, sodass
 * `page / totalPages == fraction` gilt (= totalProgression in Prozent).
 */
class NovelProgressMapper {

    /**
     * Bildet den Leseanteil [fraction] (0.0..1.0) auf einen [ReadProgress] mit dem
     * Prozent-Raster ab: [ReadProgress.totalPages] = 100, [ReadProgress.page] = Prozent
     * (1-basiert, weil Komga-Seiten bei 1 beginnen). 100 % gilt als abgeschlossen.
     */
    fun toReadProgress(fraction: Float): ReadProgress {
        val clamped = fraction.coerceIn(0f, 1f)
        val page = (clamped * PERCENT_SCALE).roundToInt().coerceIn(1, PERCENT_SCALE)
        return ReadProgress(
            bookId = 0,
            page = page,
            totalPages = PERCENT_SCALE,
            completed = page == PERCENT_SCALE,
            updatedAt = 0,
        )
    }

    /**
     * Grober Wiederaufnahme-Anteil als Fallback. Liegt lokal ein [anchor] (Xpointer) vor,
     * nutzt der Aufrufer die EXAKTE `seekToAnchor`-Position und ruft dies nicht auf; ohne
     * Anker bleibt nur der über % rekonstruierte [fraction] (`seekToProgress`).
     */
    fun resumeFraction(anchor: String?, fraction: Float): Float =
        fraction.coerceIn(0f, 1f)

    private companion object {
        /** Prozent-Raster: 100 Schritte → page == Prozent, totalPages == 100. */
        const val PERCENT_SCALE = 100
    }
}
