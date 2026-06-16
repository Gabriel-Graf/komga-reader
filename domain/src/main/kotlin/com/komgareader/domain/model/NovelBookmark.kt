package com.komgareader.domain.model

/**
 * A single in-text bookmark in a reflowable novel. Position is a crengine
 * xpointer ([xpointer]) — layout-independent, survives relayout. The word text
 * and surrounding [snippet] are captured at set time so the list is meaningful offline.
 * Local-only; never synced to a server.
 *
 * [markerStyle] is the per-bookmark draw mode ([BookmarkMarkerStyle] name); the global
 * setting is only the default for *new* bookmarks. [color] is the marker's *content*
 * colour drawn over the page (ARGB), independent of the UI accent — default black.
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
    val markerStyle: String = BookmarkMarkerStyle.FLAG.name,
    val color: Int = 0xFF000000.toInt(),
)
