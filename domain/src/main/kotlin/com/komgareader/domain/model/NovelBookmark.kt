package com.komgareader.domain.model

/**
 * A single in-text bookmark in a reflowable novel. Position is a crengine
 * xpointer ([xpointer]) — layout-independent, survives relayout. [word] +
 * [snippet] are captured at set time so the list is meaningful offline.
 * Local-only; never synced to a server.
 */
data class NovelBookmark(
    val id: Long,
    val sourceId: Long,
    val bookId: String,
    val xpointer: String,
    val number: Int,
    val label: String?,
    val snippet: String,
    val createdAt: Long,
)
