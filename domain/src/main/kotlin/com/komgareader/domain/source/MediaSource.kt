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

/** Filter für [BrowsableSource.browse]. [containerIds] leer = kein Container-Filter. */
data class SourceFilter(
    val seriesId: String? = null,
    val containerIds: List<String> = emptyList(),
)

/**
 * Verweis auf eine einzelne Seite (Bild) eines Buchs. [index] ist 0-basiert
 * (interne Position), [pageNumber] 1-basiert (wie von der Quelle adressiert).
 */
data class PageRef(
    val index: Int,
    val bookRemoteId: String,
    val pageNumber: Int,
    val url: String,
)

/** Quelle, die durchsucht und gelesen werden kann. */
interface BrowsableSource : MediaSource {
    suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series>
    suspend fun search(query: String, page: Int): PagedResult<Series>
    suspend fun books(seriesRemoteId: String): List<Book>

    /**
     * Reichhaltige Metadaten zu einer einzelnen Serie (Beschreibung, Status, Genres).
     * Quellen, die kein eigenes Serien-Detail liefern können, geben `null` zurück;
     * die UI fällt dann auf das aus [books] abgeleitete Minimum zurück.
     */
    suspend fun seriesDetail(seriesRemoteId: String): Series?

    suspend fun pages(bookRemoteId: String): List<PageRef>
    /** Liefert die rohen Bytes einer Seite (Stream) oder des Buchs (Download). */
    suspend fun openPage(ref: PageRef): ByteArray

    /**
     * Lädt die komplette Buchdatei (z.B. EPUB/CBZ) als rohe Bytes. Für Reader, die das
     * ganze Dokument brauchen (Reflow, MuPDF) statt seitenweisem Streaming über [openPage].
     * [onProgress] meldet `(gelesene Bytes, Gesamtbytes)` fortlaufend (Gesamt `-1`/`0` wenn
     * unbekannt); Quellen ohne Stream-Fortschritt melden best-effort am Ende. Default = ignorieren.
     */
    suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray

    /**
     * Löst auf, zu welcher Serie ein Buch gehört (für die Kapitel-Liste des Webtoon-Strips).
     * Gibt die quellen-interne Serien-`remoteId` zurück.
     */
    suspend fun seriesIdOf(bookRemoteId: String): String

    /**
     * Rohe Bytes des Titelbilds (Cover) — einer Serie ([isSeriesCover] = true) oder eines
     * Buchs/Kapitels. So lädt die UI Cover über die Naht (Coil-Fetcher) statt über eine
     * quellen-spezifische URL + Auth-Header. Quellen ohne Cover werfen/liefern leer.
     */
    suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray
}

/** Quelle, die Lese-Fortschritt server-seitig synchronisieren kann (z.B. Komga). */
interface SyncingSource : MediaSource {
    suspend fun pushProgress(bookRemoteId: String, progress: ReadProgress)
    suspend fun pullProgress(bookRemoteId: String): ReadProgress?

    /**
     * Markiert ein Buch server-seitig als gelesen ([read] = true → letzte Seite/komplett)
     * oder hebt die Markierung auf ([read] = false → Fortschritt löschen). [pageCount] ist
     * die Seitenzahl, die beim Setzen als gelesene Position gemeldet wird.
     */
    suspend fun setRead(bookRemoteId: String, read: Boolean, pageCount: Int)
}

/**
 * Server-seitiger Container (Komga-Library, OPDS-Feed, …), der Serien gruppiert.
 * Quellen-agnostisch (Naht C): die UI mappt ihn auf App-Bibliotheken.
 */
data class SourceContainer(val id: String, val name: String)

/**
 * Optionale Capability: Quelle kann ihre Top-Level-Container auflisten. Quellen
 * ohne native Gruppierung implementieren das schlicht nicht — die UI behandelt
 * sie dann als „ganze Quelle, keine Container".
 */
interface ContainerSource : MediaSource {
    suspend fun listContainers(): List<SourceContainer>
}
