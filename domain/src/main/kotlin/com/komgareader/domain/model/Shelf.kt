package com.komgareader.domain.model

/**
 * Nutzer-definierte App-Bibliothek. Bündelt pro Quelle ausgewählte Container
 * (Komga-Libraries). [defaultContentType] ist der optionale Viewer-Notnagel,
 * wenn Metadaten einer Serie keine Leserichtung hergeben (siehe ResolveViewerType).
 */
data class Shelf(
    val id: Long,
    val name: String,
    val sources: List<ShelfSource>,
    val defaultContentType: ContentType? = null,
)

/** Auswahl innerhalb einer Quelle. [containerIds] leer = ganze Quelle. */
data class ShelfSource(
    val sourceId: Long,
    val containerIds: List<String> = emptyList(),
)
