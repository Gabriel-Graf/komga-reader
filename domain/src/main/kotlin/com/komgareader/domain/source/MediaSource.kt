package com.komgareader.domain.source

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind

/** Naht A: jede Backend-Quelle (Komga, lokal, Online-Plugin) implementiert dies. */
interface MediaSource {
    val id: Long
    val name: String
    val kind: SourceKind
}

/** Eine Seite Ergebnisse mit Cursor-Flag. */
data class PagedResult<T>(val items: List<T>, val hasNextPage: Boolean)

/** Filter für [BrowsableSource.browse]. Wächst in späteren Plänen. */
data class SourceFilter(val seriesId: String? = null)

/** Verweis auf eine einzelne Seite (Bild) eines Buchs. */
data class PageRef(val index: Int, val url: String)

/** Quelle, die durchsucht und gelesen werden kann. */
interface BrowsableSource : MediaSource {
    suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series>
    suspend fun search(query: String, page: Int): PagedResult<Series>
    suspend fun books(seriesRemoteId: String): List<Book>
    suspend fun pages(bookRemoteId: String): List<PageRef>
    /** Liefert die rohen Bytes einer Seite (Stream) oder des Buchs (Download). */
    suspend fun openPage(ref: PageRef): ByteArray
}

/** Quelle, die Lese-Fortschritt server-seitig synchronisieren kann (z.B. Komga). */
interface SyncingSource : MediaSource {
    suspend fun pushProgress(bookRemoteId: String, progress: ReadProgress)
    suspend fun pullProgress(bookRemoteId: String): ReadProgress?
}
