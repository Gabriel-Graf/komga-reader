package com.komgareader.domain.source

import com.komgareader.domain.reader.WebtoonChapter
import com.komgareader.domain.reader.WebtoonStrip

/**
 * Pures Ergebnis der Webtoon-Strip-Planung: der kapitelübergreifende [strip] (Index ↔ Kapitel/Seite),
 * die flache, nahtlose Seiten-Liste [pages] über alle Kapitel und der globale Startindex
 * [initialGlobalIndex]. Reine Daten — die Quelle mappt [pages] auf ihre Bild-Refs.
 */
data class WebtoonStripPlan(
    val strip: WebtoonStrip,
    val pages: List<PageRef>,
    val initialGlobalIndex: Int,
)

/**
 * Baut den nahtlosen, kapitelübergreifenden Webtoon-Strip rein und deterministisch: aus den
 * Kapiteln (remoteId + Seitenzahl), dem geöffneten Buch und dessen lokaler Startseite entsteht
 * der [WebtoonStrip], die flache Seiten-Liste (über [buildPageRefs] je Kapitel) und der globale
 * Startindex. Kein Netzabruf, kein I/O — die aufrufende Quelle holt die Kapitel/den Fortschritt
 * und mappt die [PageRef]s auf ihre Bild-Modelle.
 *
 * Ein unbekanntes [openedBookRemoteId] fällt auf das erste Kapitel zurück (Index 0).
 */
fun buildWebtoonStrip(
    chapters: List<WebtoonChapter>,
    openedBookRemoteId: String,
    localStartPage: Int,
): WebtoonStripPlan {
    val strip = WebtoonStrip(chapters)
    val pages = chapters.flatMap { buildPageRefs(it.bookRemoteId, it.pageCount) }

    val openedIndex = chapters.indexOfFirst { it.bookRemoteId == openedBookRemoteId }.coerceAtLeast(0)
    val openedPageCount = chapters.getOrNull(openedIndex)?.pageCount ?: 0
    val clampedLocalStart = localStartPage.coerceIn(0, (openedPageCount - 1).coerceAtLeast(0))
    val initialGlobalIndex = if (strip.totalPages == 0) 0 else strip.globalIndex(openedIndex, clampedLocalStart)

    return WebtoonStripPlan(strip = strip, pages = pages, initialGlobalIndex = initialGlobalIndex)
}
