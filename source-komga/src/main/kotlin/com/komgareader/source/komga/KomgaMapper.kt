package com.komgareader.source.komga

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.DownloadState
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.source.PageRef
import com.komgareader.source.komga.dto.BookDto
import com.komgareader.source.komga.dto.PageDto
import com.komgareader.source.komga.dto.SeriesDto

/** Lokale DB-ID, die erst beim Persistieren vergeben wird. */
private const val UNASSIGNED_ID = 0L

/**
 * Übersetzt Komga-DTOs in Domain-Modelle. Reine Funktionen — keine I/O.
 * [baseUrl] endet auf `.../api/v1/` und dient zum Bau absoluter Cover-/Seiten-URLs.
 */
class KomgaMapper(private val sourceId: Long, private val baseUrl: String) {

    fun toSeries(dto: SeriesDto): Series = Series(
        id = UNASSIGNED_ID,
        sourceId = sourceId,
        remoteId = dto.id,
        title = dto.metadata.title.ifBlank { dto.name },
        coverUrl = "${baseUrl}series/${dto.id}/thumbnail",
    )

    fun toBook(dto: BookDto): Book = Book(
        id = UNASSIGNED_ID,
        sourceId = sourceId,
        seriesId = UNASSIGNED_ID,
        remoteId = dto.id,
        title = dto.metadata.title.ifBlank { dto.name },
        format = mediaTypeToFormat(dto.media.mediaType),
        pageCount = dto.media.pagesCount,
        downloadState = DownloadState.REMOTE,
        seriesTitle = dto.seriesTitle,
        sizeBytes = dto.sizeBytes,
        fileUrl = dto.url.ifBlank { null },
        createdDate = dto.created,
        modifiedDate = dto.lastModified,
    )

    fun toPageRefs(bookRemoteId: String, pages: List<PageDto>): List<PageRef> =
        pages.map { p ->
            PageRef(
                index = p.number - 1,
                bookRemoteId = bookRemoteId,
                pageNumber = p.number,
                url = "${baseUrl}books/$bookRemoteId/pages/${p.number}",
            )
        }

    fun toReadProgress(dto: BookDto, localBookId: Long, updatedAt: Long): ReadProgress? {
        val rp = dto.readProgress ?: return null
        return ReadProgress(
            bookId = localBookId,
            page = rp.page,
            totalPages = dto.media.pagesCount,
            completed = rp.completed,
            updatedAt = updatedAt,
        )
    }
}
