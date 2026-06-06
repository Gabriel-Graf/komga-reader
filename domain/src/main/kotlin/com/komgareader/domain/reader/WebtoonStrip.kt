package com.komgareader.domain.reader

/** Ein Kapitel im fortlaufenden Webtoon-Strip. [pageCount] = tatsächliche Bildanzahl. */
data class WebtoonChapter(val bookRemoteId: String, val pageCount: Int)

/** Position innerhalb des Strips: Kapitel + 0-basierte Seite im Kapitel. */
data class StripPosition(val chapterIndex: Int, val pageInChapter: Int, val bookRemoteId: String)

/**
 * Fortlaufender Webtoon-Strip über mehrere Kapitel. Bildet den globalen,
 * kapitelübergreifenden Seitenindex auf (Kapitel, Seite-im-Kapitel) ab und
 * zurück — rein, kein I/O. Dient dazu, beim nahtlosen Scrollen den
 * Lese-Fortschritt kapitel-genau zu bestimmen.
 */
class WebtoonStrip(val chapters: List<WebtoonChapter>) {

    /** Globaler Start-Index jedes Kapitels (Präfix-Summe der Seitenzahlen). */
    private val starts: IntArray = IntArray(chapters.size).also { arr ->
        var acc = 0
        chapters.forEachIndexed { i, c ->
            arr[i] = acc
            acc += c.pageCount
        }
    }

    val totalPages: Int = chapters.sumOf { it.pageCount }

    /** Globaler Start-Index (0-basiert) des Kapitels [chapterIndex]. */
    fun chapterStart(chapterIndex: Int): Int = starts[chapterIndex]

    /** Globaler Index aus Kapitel + lokaler Seite. */
    fun globalIndex(chapterIndex: Int, pageInChapter: Int): Int = starts[chapterIndex] + pageInChapter

    /**
     * Lokalisiert einen globalen Index auf Kapitel + Seite-im-Kapitel.
     * Klemmt außerhalb liegende Indizes auf die erste bzw. letzte Seite.
     */
    fun locate(globalIndex: Int): StripPosition {
        require(chapters.isNotEmpty()) { "Strip hat keine Kapitel" }
        val clamped = globalIndex.coerceIn(0, totalPages - 1)
        // Letztes Kapitel, dessen Start <= clamped — Kapitel mit 0 Seiten werden
        // dabei übersprungen, da ihr Start mit dem des Nachfolgers zusammenfällt.
        var chapterIndex = 0
        for (i in chapters.indices) {
            if (chapters[i].pageCount > 0 && starts[i] <= clamped) chapterIndex = i
        }
        return StripPosition(
            chapterIndex = chapterIndex,
            pageInChapter = clamped - starts[chapterIndex],
            bookRemoteId = chapters[chapterIndex].bookRemoteId,
        )
    }
}
