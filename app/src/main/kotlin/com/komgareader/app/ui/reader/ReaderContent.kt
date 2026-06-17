package com.komgareader.app.ui.reader

import com.komgareader.app.data.coil.ReaderPageImage
import com.komgareader.app.ui.common.UiError
import com.komgareader.domain.reader.WebtoonStrip

sealed interface ReaderContent {
    data object Loading : ReaderContent
    /**
     * Page-list content: a flat, ordered list of [ReaderPageImage] the reader shows one per page.
     * The page model is **uniform** — each entry is either a streamed
     * [com.komgareader.app.data.coil.SourceImage] (Komga/Kavita/OPDS-PSE, resolved via `openPage`)
     * or a [com.komgareader.app.data.coil.RenderedPageImage] (downloaded/whole-file book, rendered
     * by MuPDF). The reader host dispatches this on [ViewerMode] (paged/comic/webtoon), so a
     * whole-file book honours its resolved viewer type just like a streamed one.
     */
    data class Pages(
        val pages: List<ReaderPageImage>,
        val initialPage: Int,
    ) : ReaderContent
    /**
     * Webtoon-Modus: Seiten **aller Kapitel** der Serie nahtlos hintereinander.
     * [pages] ist die flache, kapitelübergreifende Bildliste; [strip]
     * bildet den globalen Index auf Kapitel + Seite ab (für kapitel-genauen Fortschritt).
     * [initialPage] ist der globale Startindex.
     */
    data class Webtoon(
        val pages: List<ReaderPageImage>,
        val initialPage: Int,
        val strip: WebtoonStrip,
    ) : ReaderContent
    /**
     * Roman-Modus (EPUB): der [NovelReaderViewModel] öffnet das Buch selbst über die
     * crengine-Reflow-Engine. Dieser Marker signalisiert dem Reader-Host nur den
     * Modus — die Bytes lädt der Novel-Reader über denselben Mechanismus.
     */
    data object Novel : ReaderContent
    data class Error(val error: UiError) : ReaderContent
}
