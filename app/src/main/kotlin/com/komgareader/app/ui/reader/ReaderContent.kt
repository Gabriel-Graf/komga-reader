package com.komgareader.app.ui.reader

import com.komgareader.domain.source.PageRef

sealed interface ReaderContent {
    data object Loading : ReaderContent
    /** Streaming-Modus: Seitenbilder kommen vom Server. [authHeaders] enthält API-Key oder Basic-Auth. */
    data class Streamed(
        val pages: List<PageRef>,
        val authHeaders: Map<String, String>,
        val initialPage: Int,
    ) : ReaderContent
    /** MuPDF-gerendertes Dokument (EPUB-Stream oder lokaler Download). */
    data class Rendered(val pageCount: Int, val initialPage: Int) : ReaderContent
    data class Error(val message: String) : ReaderContent
}
