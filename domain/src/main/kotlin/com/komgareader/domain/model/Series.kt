package com.komgareader.domain.model

/**
 * Eine Serie aus einer Quelle. [contentTypeOverride] erlaubt, den vom Regal
 * vorgegebenen Typ pro Serie zu überschreiben.
 */
data class Series(
    val id: Long,
    val sourceId: Long,
    val remoteId: String,
    val title: String,
    val coverUrl: String? = null,
    val contentTypeOverride: ContentType? = null,
)
