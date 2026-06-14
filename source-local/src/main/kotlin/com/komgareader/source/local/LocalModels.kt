package com.komgareader.source.local

import com.komgareader.domain.model.BookFormat

/** One file/folder discovered by the scanner, path relative to the picked root. */
data class ScannedEntry(
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
)

/** Pure, in-memory library index built from scanned entries. */
data class LocalIndex(val series: List<LocalSeries>) {
    fun series(remoteId: String): LocalSeries? = series.firstOrNull { it.remoteId == remoteId }
    fun book(bookRemoteId: String): LocalBook? =
        series.firstNotNullOfOrNull { s -> s.books.firstOrNull { it.remoteId == bookRemoteId } }
}

data class LocalSeries(
    val remoteId: String,
    val title: String,
    val books: List<LocalBook>,
)

data class LocalBook(
    val remoteId: String,
    val title: String,
    val format: BookFormat,
    val number: String? = null,
    val summary: String? = null,
)
