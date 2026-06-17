package com.komgareader.source.opds

/**
 * Ein einzelner Atom-Eintrag aus einem OPDS-Feed.
 * [coverHref] ist null wenn kein Cover-Link vorhanden.
 * [acquisitionHref] ist null wenn kein Acquisition-Link vorhanden.
 *
 * OPDS-PSE (Page Streaming Extension): [pseTemplateHref] ist die Href-Vorlage des
 * Streaming-Links mit `{pageNumber}`-Platzhalter (0-basiert per Spec), [pseCount] die
 * Seitenzahl. Beide null, wenn der Eintrag kein PSE-Streaming anbietet (EPUB/PDF, oder
 * ein Server ohne PSE) — dann liest der Reader das Buch whole-file über den Acquisition-Link.
 */
data class OpdsEntry(
    val id: String,
    val title: String,
    val coverHref: String?,
    val acquisitionHref: String?,
    val acquisitionType: String?,
    val pseCount: Int? = null,
    val pseTemplateHref: String? = null,
)
