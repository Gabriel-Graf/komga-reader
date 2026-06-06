package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ViewerType

/**
 * Bestimmt deterministisch den Lese-Modus: Serien-Override hat Vorrang vor dem
 * Regal-Typ. Kein Auto-Erkennen — der Typ ist immer explizit deklariert.
 */
class ResolveViewerType {

    operator fun invoke(series: Series, shelf: Shelf): ViewerType =
        when (series.contentTypeOverride ?: shelf.contentType) {
            ContentType.MANGA -> ViewerType.PAGED
            ContentType.COMIC -> ViewerType.PAGED
            ContentType.WEBTOON -> ViewerType.WEBTOON
            ContentType.NOVEL -> ViewerType.EPUB
        }
}
