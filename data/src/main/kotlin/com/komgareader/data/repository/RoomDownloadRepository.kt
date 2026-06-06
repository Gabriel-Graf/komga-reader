package com.komgareader.data.repository

import com.komgareader.data.db.DownloadDao
import com.komgareader.data.db.DownloadEntity
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.DownloadedBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDownloadRepository(private val dao: DownloadDao) : DownloadRepository {

    override val downloads: Flow<List<DownloadedBook>> =
        dao.observeAll().map { list -> list.map(::toBook) }

    override suspend fun get(bookRemoteId: String): DownloadedBook? =
        dao.get(bookRemoteId)?.let(::toBook)

    override suspend fun put(book: DownloadedBook) =
        dao.put(toEntity(book))

    override suspend fun remove(bookRemoteId: String) =
        dao.delete(bookRemoteId)

    private fun toBook(e: DownloadEntity) = DownloadedBook(
        bookRemoteId = e.bookRemoteId,
        sourceId = e.sourceId,
        seriesRemoteId = e.seriesRemoteId,
        title = e.title,
        format = e.format,
        localPath = e.localPath,
        totalPages = e.totalPages,
    )

    private fun toEntity(b: DownloadedBook) = DownloadEntity(
        bookRemoteId = b.bookRemoteId,
        sourceId = b.sourceId,
        seriesRemoteId = b.seriesRemoteId,
        title = b.title,
        format = b.format,
        localPath = b.localPath,
        totalPages = b.totalPages,
    )
}
