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
    (query.isBlank() || fuzzyMatchesTitle(item.title, query)) &&
        (types.isEmpty() || typeOf(item) in types) &&
        (!downloadedOnly || isDownloaded(item))
}

/**
 * Tippfehler-tolerante Titelsuche (rein, client-seitig über die bereits geladenen Serien). Eine Serie
 * passt, wenn **jedes** Such-Token den Titel trifft — entweder als Teilstring (schneller, exakter Pfad)
 * oder fuzzy gegen ein Titelwort innerhalb einer längen-abhängigen Levenshtein-Toleranz (kurze Tokens
 * tolerieren nichts, damit „ber" nicht halb die Bibliothek trifft). So findet „berserc"/„narto" weiterhin
 * „Berserk"/„Naruto", während exakte Tippeingaben unverändert greifen.
 */
fun fuzzyMatchesTitle(title: String, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val t = title.lowercase()
    if (t.contains(q)) return true // exact (sub)string — unchanged behaviour, fastest path
    val titleWords = t.split(*WORD_SEPARATORS).filter { it.isNotEmpty() }
    return q.split(*WORD_SEPARATORS).filter { it.isNotEmpty() }.all { token ->
        t.contains(token) || titleWords.any { word -> fuzzyHit(word, token) }
    }
}

private val WORD_SEPARATORS = charArrayOf(' ', '-', '_', '.', ':', ',', '/', '\'', '!', '?')

/** A token fuzzily hits a title word within a length-scaled Levenshtein tolerance. */
private fun fuzzyHit(word: String, token: String): Boolean {
    val tolerance = when {
        token.length <= 3 -> 0
        token.length <= 6 -> 1
        else -> 2
    }
    if (tolerance == 0) return false
    return levenshteinWithin(word, token, tolerance)
}

/** Levenshtein distance with an early-out: returns true iff `distance(a, b) <= max`. */
private fun levenshteinWithin(a: String, b: String, max: Int): Boolean {
    if (kotlin.math.abs(a.length - b.length) > max) return false
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        var rowMin = curr[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            if (curr[j] < rowMin) rowMin = curr[j]
        }
        if (rowMin > max) return false // whole row already exceeds tolerance
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[b.length] <= max
}
