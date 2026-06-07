package com.komgareader.app.ui.library

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series

/**
 * Reine Filterfunktion fürs Stöbern. Eine Serie bleibt erhalten, wenn **alle** aktiven
 * Achsen zutreffen:
 * - **Titel** enthält den (leeren oder gesetzten) Suchtext.
 * - **Werk-Typ**: Filter leer ODER der effektive Typ (via [typeOf]) gehört zu [types].
 *   Der effektive Typ wird hereingereicht — exakt der Wert, der auch als Typ-Tag erscheint
 *   (Bibliotheks-Default ⟶ sonst manuelle Zuweisung; siehe [LibraryViewModel]), DRY.
 * - **Heruntergeladen**: ist [downloadedOnly] aktiv, bleiben nur Werke mit mindestens einem
 *   lokal gespeicherten Kapitel ([isDownloaded]).
 *
 * Serien ohne erkennbaren Typ fallen bei aktivem Typ-Filter heraus.
 */
fun filterSeries(
    series: List<Series>,
    query: String,
    types: Set<ContentType>,
    downloadedOnly: Boolean = false,
    typeOf: (Series) -> ContentType? = { it.contentTypeOverride },
    isDownloaded: (Series) -> Boolean = { false },
): List<Series> = series.filter { item ->
    (query.isBlank() || item.title.contains(query, ignoreCase = true)) &&
        (types.isEmpty() || typeOf(item) in types) &&
        (!downloadedOnly || isDownloaded(item))
}
