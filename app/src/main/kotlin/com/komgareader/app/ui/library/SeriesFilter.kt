package com.komgareader.app.ui.library

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series

/**
 * Reine Filterfunktion fürs Stöbern: behält eine Serie, wenn der Titel den (leeren oder
 * gesetzten) Suchtext enthält UND der Typ-Filter leer ist oder der **effektive** Werk-Typ
 * der Serie zu den gewählten Typen gehört.
 *
 * Der effektive Typ wird **nicht** hier geraten, sondern über [typeOf] hereingereicht —
 * exakt derselbe Wert, der auch als Typ-Tag angezeigt wird (Bibliotheks-Default ⟶ sonst
 * manuelle Zuweisung; siehe [LibraryViewModel]). So filtert die Liste nach genau dem Tag,
 * das der Nutzer sieht (DRY). Serien ohne erkennbaren Typ fallen bei aktivem Filter heraus.
 */
fun filterSeries(
    series: List<Series>,
    query: String,
    types: Set<ContentType>,
    typeOf: (Series) -> ContentType? = { it.contentTypeOverride },
): List<Series> = series.filter { item ->
    (query.isBlank() || item.title.contains(query, ignoreCase = true)) &&
        (types.isEmpty() || typeOf(item) in types)
}
