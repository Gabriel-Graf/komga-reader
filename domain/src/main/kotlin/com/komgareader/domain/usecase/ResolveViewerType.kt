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
 * 4. Archiv-Format (CBZ/CBR/PDF)      → PAGED
 * 5. Bibliotheks-Default (Fallback)   → map(fallback)
 * 6. sonst                            → PAGED
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
        if (book.format == BookFormat.CBZ ||
            book.format == BookFormat.CBR ||
            book.format == BookFormat.PDF
        ) {
            return ViewerType.PAGED
        }
        fallback?.let { return map(it) }
        return ViewerType.PAGED
    }

    private fun map(type: ContentType): ViewerType = when (type) {
        ContentType.MANGA -> ViewerType.PAGED
        ContentType.COMIC -> ViewerType.PAGED
        ContentType.WEBTOON -> ViewerType.WEBTOON
        ContentType.NOVEL -> ViewerType.EPUB
    }
}
