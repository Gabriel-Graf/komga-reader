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
)

interface DownloadRepository {
    val downloads: Flow<List<DownloadedBook>>
    suspend fun get(bookRemoteId: String): DownloadedBook?
    suspend fun put(book: DownloadedBook)
    suspend fun remove(bookRemoteId: String)
}
