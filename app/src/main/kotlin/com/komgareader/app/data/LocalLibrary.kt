package com.komgareader.app.data

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
