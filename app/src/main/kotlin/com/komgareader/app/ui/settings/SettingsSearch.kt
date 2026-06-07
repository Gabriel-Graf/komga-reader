package com.komgareader.app.ui.settings

/**
 * Alle Vorkommen von [query] in [text], case-insensitive, als inklusive IntRanges.
 * Leere/blanke [query] oder kein Treffer → leere Liste. Pure Funktion (testbar).
 */
fun matchRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty() || needle.length > text.length) return emptyList()
    val haystack = text.lowercase()
    val lowered = needle.lowercase()
    val ranges = mutableListOf<IntRange>()
    var from = haystack.indexOf(lowered)
    while (from >= 0) {
        ranges += from..(from + lowered.length - 1)
        from = haystack.indexOf(lowered, from + lowered.length)
    }
    return ranges
}

/** Sektion matcht, wenn irgendein Term die (getrimmte) [query] enthält. Blank → false. */
fun sectionMatches(searchTerms: List<String>, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return false
    return searchTerms.any { it.contains(needle, ignoreCase = true) }
}
