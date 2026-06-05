package com.komgareader.domain.model

/**
 * Nutzer-definiertes Regal. Bündelt eine oder mehrere Quellen und deklariert
 * über [contentType] den Default-Viewer für alle enthaltenen Serien.
 */
data class Shelf(
    val id: Long,
    val name: String,
    val contentType: ContentType,
    val sourceIds: List<Long>,
)
