package com.komgareader.domain.model

/**
 * Eine Serie aus einer Quelle. [contentTypeOverride] erlaubt, den vom Regal
 * vorgegebenen Typ pro Serie zu überschreiben.
 *
 * [summary], [status] und [genres] sind generische, quellen-agnostische
 * Metadaten (Naht A): jede [com.komgareader.domain.source.MediaSource] füllt sie,
 * soweit das Backend sie liefert — sonst bleiben sie leer/`null`.
 */
data class Series(
    val id: Long,
    val sourceId: Long,
    val remoteId: String,
    val title: String,
    val coverUrl: String? = null,
    val contentTypeOverride: ContentType? = null,
    val summary: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val readingDirection: ReadingDirection? = null,
    /** Container (Komga-Library), zu dem die Serie gehört; für die pfad-unabhängige Regal-Zuordnung. */
    val libraryId: String? = null,
)
