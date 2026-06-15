package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

data class DownloadedBook(
    val bookRemoteId: String,
    val sourceId: Long,
    val seriesRemoteId: String,
    val title: String,
    val format: String,
    val localPath: String,
    val totalPages: Int,
    /** Serien-Metadaten für Offline-Browsing (ohne Server keine andere Quelle). */
    val seriesTitle: String = "",
    val seriesCoverUrl: String? = null,
)

interface DownloadRepository {
    val downloads: Flow<List<DownloadedBook>>
    suspend fun get(bookRemoteId: String): DownloadedBook?
    suspend fun put(book: DownloadedBook)
    suspend fun remove(bookRemoteId: String)
    suspend fun removeBySourceId(sourceId: Long)
}
