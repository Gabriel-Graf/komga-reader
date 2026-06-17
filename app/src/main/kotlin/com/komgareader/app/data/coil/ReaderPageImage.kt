package com.komgareader.app.data.coil

/**
 * Common Coil model for a single reader page, so the paged/comic/webtoon readers consume
 * `List<ReaderPageImage>` **uniformly** regardless of where the bytes come from. Two kinds:
 *
 * - [SourceImage]      — page streamed through the source seam ([com.komgareader.app.data.coil.SourcePageFetcher]
 *                        → `BrowsableSource.openPage`). Komga/Kavita/OPDS-PSE.
 * - [RenderedPageImage] — a page of a **whole-file** document (downloaded book, local PDF/CBR,
 *                        OPDS without PSE), rendered to a bitmap by MuPDF via [RenderedPageFetcher].
 *
 * Coil resolves each kind through its own registered `Fetcher.Factory` (matched by concrete type),
 * so a reader can render either kind through the same `ImageRequest.data(page)` call. This is what
 * lets the COMIC/WEBTOON readers work for downloaded/whole-file books — not just streamed ones.
 */
sealed interface ReaderPageImage {
    val bookRemoteId: String

    /** Stable per-page identity within the book (used for `LazyColumn` item keys). */
    val pageKey: Int
}

/**
 * A page of a whole-file/downloaded document (CBZ/CBR/PDF), rendered by the MuPDF
 * [com.komgareader.domain.render.DocumentFactory]. [pageIndex] is 0-based; [ext] is the file
 * extension (e.g. `.cbz`) needed to open the document. Resolved by [RenderedPageFetcher] via the
 * shared [com.komgareader.app.data.RenderedPageStore] (opens the document once, renders many pages).
 */
data class RenderedPageImage(
    val sourceId: Long,
    override val bookRemoteId: String,
    val pageIndex: Int,
    val ext: String,
) : ReaderPageImage {
    override val pageKey: Int get() = pageIndex
}
