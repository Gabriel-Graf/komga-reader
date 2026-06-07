package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf

/**
 * Bestimmt den Regal-`defaultContentType` einer Serie **pfad-unabhängig** über ihre
 * Container-(Library-)Zugehörigkeit — damit das Regal-Tag (COMIC/MANGA/WEBTOON/NOVEL)
 * auch dann greift, wenn die Serie nicht durch das Regal, sondern über Stöbern/Suche
 * geöffnet wird (dann fehlt die `shelfId`).
 *
 * Eine Serie gehört zu einem Regal, wenn dieses eine [com.komgareader.domain.model.ShelfSource]
 * derselben Quelle hat, deren `containerIds` die `libraryId` der Serie enthält — oder leer ist
 * (leer = ganze Quelle). Das erste passende Regal mit gesetztem `defaultContentType` gewinnt.
 */
class ResolveShelfContentType {

    operator fun invoke(series: Series, shelves: List<Shelf>): ContentType? =
        shelves.firstOrNull { shelf ->
            shelf.defaultContentType != null && shelf.sources.any { source ->
                source.sourceId == series.sourceId &&
                    (source.containerIds.isEmpty() ||
                        (series.libraryId != null && series.libraryId in source.containerIds))
            }
        }?.defaultContentType
}
