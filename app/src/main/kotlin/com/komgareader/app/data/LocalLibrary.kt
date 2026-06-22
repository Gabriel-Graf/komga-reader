package com.komgareader.app.data

import com.komgareader.app.data.coil.SourceCover
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DownloadState
import com.komgareader.domain.model.Series
import com.komgareader.domain.repository.DownloadedBook

/**
 * Baut aus heruntergeladenen Büchern eine Serien-Liste fürs **Offline-Browsing** — gruppiert
 * nach Serie, mit den beim Download gespeicherten Serien-Metadaten (Titel/Cover). Ohne Server
 * gibt es keine andere Quelle für diese Anzeige.
 */
fun List<DownloadedBook>.localSeries(sourceId: Long? = null): List<Series> =
    filter { sourceId == null || it.sourceId == sourceId }
        .groupBy { it.seriesRemoteId }
        .map { (remoteId, books) ->
            val first = books.first()
            Series(
                id = 0,
                sourceId = first.sourceId,
                remoteId = remoteId,
                title = first.seriesTitle.ifBlank { first.title },
                coverUrl = first.seriesCoverUrl,
            )
        }
        .sortedBy { it.title.lowercase() }

/** Menge der Serien-Remote-IDs, die lokale Downloads haben (für das „lokal"-Cover-Badge). */
fun List<DownloadedBook>.localSeriesIds(): Set<String> = mapTo(mutableSetOf()) { it.seriesRemoteId }

/**
 * Die heruntergeladenen Bücher **einer** Serie als Domain-[Book]s — der Offline-Fallback, wenn
 * der Server (Naht A) eine Serie nicht laden kann. So zeigt die Serien-Detailseite ohne Netz die
 * lokal vorhandenen Bände statt zu blockieren (analog zum Offline-Browsing über [localSeries]).
 * Natürlich sortiert (Band 10 nach Band 2), als [DownloadState.LOCAL] markiert.
 */
fun List<DownloadedBook>.localBooks(seriesRemoteId: String, sourceId: Long): List<Book> =
    filter { it.seriesRemoteId == seriesRemoteId && it.sourceId == sourceId }
        // Nach der Band-Nummer sortieren (wie online), nicht nach dem Titel-String — der Titel ist oft
        // der Kapitel-Name ohne Nummer. Fällt auf den Titel zurück, wenn keine Nummer gespeichert ist
        // (Downloads von vor Migration 21→22).
        .sortedBy { naturalSortKey(it.number?.ifBlank { null } ?: it.title) }
        .map { dl ->
            Book(
                id = 0,
                sourceId = dl.sourceId,
                seriesId = 0,
                remoteId = dl.bookRemoteId,
                title = dl.title,
                format = bookFormatOf(dl.format),
                pageCount = dl.totalPages,
                downloadState = DownloadState.LOCAL,
                seriesTitle = dl.seriesTitle,
                number = dl.number,
            )
        }

/**
 * Reichhaltige Serien-Metadaten **offline** aus den heruntergeladenen Bänden (beim Download
 * persistiert, je Band redundant wie [DownloadedBook.seriesTitle]) — Beschreibung/Status/Genres für
 * die Detailseite ohne Server. `null`, wenn keine Bände der Serie lokal sind.
 */
fun List<DownloadedBook>.localSeriesDetail(seriesRemoteId: String, sourceId: Long): Series? {
    val first = firstOrNull { it.seriesRemoteId == seriesRemoteId && it.sourceId == sourceId } ?: return null
    return Series(
        id = 0,
        sourceId = sourceId,
        remoteId = seriesRemoteId,
        title = first.seriesTitle.ifBlank { first.title },
        coverUrl = first.seriesCoverUrl,
        summary = first.seriesSummary,
        status = first.seriesStatus,
        genres = first.seriesGenres,
    )
}

/**
 * Das heruntergeladene Buch, das ein [SourceCover] offline bedient — Serien-Cover = erster Band
 * dieser Serie, Buch-Cover = genau dieses Buch. Quellen-übergreifend (matcht die [SourceCover.sourceId]),
 * damit auch Server-Downloads (Komga & Co.) offline ein Cover aus der lokalen Datei bekommen, nicht
 * nur die LOCAL-Quelle.
 */
fun List<DownloadedBook>.coverBookFor(model: SourceCover): DownloadedBook? =
    if (model.isSeries) {
        // Series cover = the naturally-first downloaded volume (vol. 1), independent of DB/list order.
        filter { it.sourceId == model.sourceId && it.seriesRemoteId == model.remoteId }
            .minByOrNull { naturalSortKey(it.title) }
    } else {
        firstOrNull { it.sourceId == model.sourceId && it.bookRemoteId == model.remoteId }
    }

/** Toleranter Parser für den persistierten Format-String ("CBZ"/"cbz"/…); Unbekanntes → [BookFormat.CBZ]. */
private fun bookFormatOf(format: String): BookFormat =
    BookFormat.entries.firstOrNull { it.name.equals(format, ignoreCase = true) } ?: BookFormat.CBZ

/** Natürlicher Sortierschlüssel: Zahlenfolgen null-gepaddet, damit "Vol. 10" nach "Vol. 2" sortiert. */
private fun naturalSortKey(title: String): String =
    Regex("\\d+").replace(title.lowercase()) { it.value.padStart(12, '0') }
