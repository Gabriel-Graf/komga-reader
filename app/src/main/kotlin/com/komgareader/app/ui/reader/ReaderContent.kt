package com.komgareader.app.ui.reader

import com.komgareader.domain.reader.WebtoonStrip
import com.komgareader.domain.source.PageRef

sealed interface ReaderContent {
    data object Loading : ReaderContent
    /** Streaming-Modus: Seitenbilder kommen vom Server. [authHeaders] enthält API-Key oder Basic-Auth. */
    data class Streamed(
        val pages: List<PageRef>,
        val authHeaders: Map<String, String>,
        val initialPage: Int,
    ) : ReaderContent
    /**
     * Webtoon-Modus: Seiten **aller Kapitel** der Serie nahtlos hintereinander.
     * [pages] ist die flache, kapitelübergreifende Bildliste; [strip] bildet den
     * globalen Index auf Kapitel + Seite ab (für kapitel-genauen Fortschritt).
     * [initialPage] ist der globale Startindex.
     */
    data class Webtoon(
        val pages: List<PageRef>,
        val authHeaders: Map<String, String>,
        val initialPage: Int,
        val strip: WebtoonStrip,
    ) : ReaderContent
    /** MuPDF-gerendertes Dokument (EPUB-Stream oder lokaler Download). */
    data class Rendered(val pageCount: Int, val initialPage: Int) : ReaderContent
    data class Error(val message: String) : ReaderContent
}
