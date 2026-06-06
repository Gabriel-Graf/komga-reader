package com.komgareader.domain.model

data class Book(
    val id: Long,
    val sourceId: Long,
    val seriesId: Long,
    val remoteId: String,
    val title: String,
    val format: BookFormat,
    val pageCount: Int,
    val downloadState: DownloadState = DownloadState.REMOTE,
    val seriesTitle: String = "",
    val sizeBytes: Long = 0L,
    val fileUrl: String? = null,
    val createdDate: String? = null,
    val modifiedDate: String? = null,
)
