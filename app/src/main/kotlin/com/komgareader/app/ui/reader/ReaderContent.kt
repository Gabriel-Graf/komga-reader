package com.komgareader.app.ui.reader

import com.komgareader.app.data.coil.SourceImage
import com.komgareader.app.ui.common.UiError
import com.komgareader.domain.reader.WebtoonStrip

sealed interface ReaderContent {
    data object Loading : ReaderContent
    /**
     * Streaming-Modus: Seitenbilder kommen über die Quellen-Naht. [pages] sind
     * [SourceImage]-Modelle; der Coil-`SourcePageFetcher` löst sie über
     * `openPage` in Bytes auf — kein url/authHeaders in der UI.
     */
    data class Streamed(
        val pages: List<SourceImage>,
        val initialPage: Int,
    ) : ReaderContent
    /**
     * Webtoon-Modus: Seiten **aller Kapitel** der Serie nahtlos hintereinander.
     * [pages] ist die flache, kapitelübergreifende Bildliste ([SourceImage]); [strip]
     * bildet den globalen Index auf Kapitel + Seite ab (für kapitel-genauen Fortschritt).
     * [initialPage] ist der globale Startindex.
     */
    data class Webtoon(
        val pages: List<SourceImage>,
        val initialPage: Int,
        val strip: WebtoonStrip,
    ) : ReaderContent
    /**
     * MuPDF-gerendertes Dokument für **lokale Downloads** (CBZ/CBR/PDF offline).
     * EPUB läuft nicht mehr hierdurch, sondern über [Novel] (crengine-Reflow).
     */
    data class Rendered(val pageCount: Int, val initialPage: Int) : ReaderContent
    /**
     * Roman-Modus (EPUB): der [NovelReaderViewModel] öffnet das Buch selbst über die
     * crengine-Reflow-Engine. Dieser Marker signalisiert dem Reader-Host nur den
     * Modus — die Bytes lädt der Novel-Reader über denselben Mechanismus.
     */
    data object Novel : ReaderContent
    data class Error(val error: UiError) : ReaderContent
}
