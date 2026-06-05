package com.komgareader.domain.model

/**
 * Lese-Fortschritt eines Buchs. [dirty] markiert lokal geänderten, noch nicht
 * zum Server gepushten Stand. [locator] hält EPUB-Position bzw. Comic-Seitenindex.
 */
data class ReadProgress(
    val bookId: Long,
    val page: Int,
    val totalPages: Int,
    val completed: Boolean = false,
    val locator: String? = null,
    val dirty: Boolean = false,
    val updatedAt: Long,
)
