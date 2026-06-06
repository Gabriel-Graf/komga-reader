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
    val booksCount: Int = 0,
    val metadata: SeriesMetadataDto = SeriesMetadataDto(),
)

@Serializable
data class SeriesMetadataDto(
    val title: String = "",
    val status: String = "",
)

@Serializable
data class BookDto(
    val id: String,
    val seriesId: String,
    val name: String,
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
