package com.komgareader.domain.usecase

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.ReadingDirection
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.ViewerType

/**
 * Bestimmt den Lese-Modus pro Buch nach fester Prioritätsregel (Naht B).
 * Vollständige Begründung: docs/domain/viewer-type-resolution.md.
 *
 * 1. Serien-Override (manuell)        → map(override)
 * 2. Buch-Format EPUB                 → EPUB
 * 3. Leserichtung VERTICAL/WEBTOON    → WEBTOON
 * 4. Bibliotheks-Default (Fallback)   → map(fallback)
 * 5. Archiv-Format (CBZ/CBR/PDF)      → PAGED
 * 6. sonst                            → PAGED
 *
 * Der Bibliotheks-Default (Stufe 4) steht bewusst VOR dem Format-Default
 * (Stufe 5): Webtoons liegen fast immer als CBZ vor, daher muss ein
 * explizites WEBTOON-Bibliothek-Tag den Format-Default (PAGED) schlagen —
 * sonst bliebe der Bibliotheks-Default für Comics wirkungslos.
 */
class ResolveViewerType {

    operator fun invoke(series: Series, book: Book, fallback: ContentType?): ViewerType {
        series.contentTypeOverride?.let { return map(it) }
        if (book.format == BookFormat.EPUB) return ViewerType.EPUB
        if (series.readingDirection == ReadingDirection.VERTICAL ||
            series.readingDirection == ReadingDirection.WEBTOON
        ) {
            return ViewerType.WEBTOON
        }
        fallback?.let { return map(it) }
        if (book.format == BookFormat.CBZ ||
            book.format == BookFormat.CBR ||
            book.format == BookFormat.PDF
        ) {
            return ViewerType.PAGED
        }
        return ViewerType.PAGED
    }

    /**
     * Regal-Default-Viewer ohne konkretes Buch (z. B. fürs Browsen einer Gruppe):
     * leitet allein aus dem Regal-Inhaltstyp ab. Per-Buch-Signale (Format, Leserichtung)
     * greifen erst in [invoke], wenn ein Buch geöffnet wird.
     */
    fun forContentType(type: ContentType): ViewerType = map(type)

    private fun map(type: ContentType): ViewerType = when (type) {
        ContentType.MANGA -> ViewerType.PAGED
        ContentType.COMIC -> ViewerType.COMIC
        ContentType.WEBTOON -> ViewerType.WEBTOON
        ContentType.NOVEL -> ViewerType.EPUB
    }
}
