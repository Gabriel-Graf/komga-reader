package com.komgareader.source.komga.dto

import kotlinx.serialization.Serializable

/** Spring-`Page`-Envelope (nur die genutzten Felder). */
@Serializable
data class KomgaPage<T>(
    val content: List<T> = emptyList(),
    val last: Boolean = true,
    val number: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
data class SeriesDto(
    val id: String,
    val name: String,
    val libraryId: String = "",
    val booksCount: Int = 0,
    val metadata: SeriesMetadataDto = SeriesMetadataDto(),
)

@Serializable
data class SeriesMetadataDto(
    val title: String = "",
    val status: String = "",
    val summary: String = "",
    val genres: List<String> = emptyList(),
    val readingDirection: String = "",
)

@Serializable
data class BookDto(
    val id: String,
    val seriesId: String,
    val name: String,
    val seriesTitle: String = "",
    val url: String = "",
    val sizeBytes: Long = 0L,
    val created: String? = null,
    val lastModified: String? = null,
    val media: BookMediaDto = BookMediaDto(),
    val metadata: BookMetadataDto = BookMetadataDto(),
    val readProgress: ReadProgressDto? = null,
)

@Serializable
data class BookMediaDto(
    val mediaType: String = "",
    val pagesCount: Int = 0,
)

@Serializable
data class BookMetadataDto(
    val title: String = "",
    val summary: String = "",
    val number: String = "",
)

@Serializable
data class ReadProgressDto(
    val page: Int,
    val completed: Boolean,
)

@Serializable
data class PageDto(
    val number: Int,
    val fileName: String = "",
    val mediaType: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

/** Request-Body für PATCH read-progress. */
@Serializable
data class ReadProgressUpdateDto(
    val page: Int,
    val completed: Boolean,
)

@Serializable
data class LibraryDto(
    val id: String,
    val name: String = "",
)
