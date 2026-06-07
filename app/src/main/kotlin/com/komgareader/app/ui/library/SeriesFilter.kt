package com.komgareader.app.ui.library

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series

/**
 * Reine Filterfunktion fürs Stöbern: behält eine Serie, wenn der Titel den (leeren oder
 * gesetzten) Suchtext enthält UND der Typ-Filter leer ist oder der manuell zugewiesene
 * Typ ([Series.contentTypeOverride]) zu den gewählten Typen gehört. Serien ohne Typ
 * fallen bei aktivem Filter immer heraus (beim Stöbern greift kein Regal-Default).
 */
fun filterSeries(
    series: List<Series>,
    query: String,
    types: Set<ContentType>,
): List<Series> = series.filter { item ->
    (query.isBlank() || item.title.contains(query, ignoreCase = true)) &&
        (types.isEmpty() || item.contentTypeOverride in types)
}
