package com.komgareader.source.opds

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OPDS-Katalog als [BrowsableSource]. Liest einen Atom-Feed via OkHttp und bildet
 * Einträge auf [Series] und [Book] ab. Lesen erfolgt über [downloadFile] + MuPDF;
 * seitenweises Streaming wird nicht unterstützt.
 *
 * Authentifizierung: optional Basic-Auth über [username]/[password].
 * OPDS-Server (u. a. Komga) akzeptieren keinen `X-API-Key`-Header am OPDS-Endpunkt.
 */
class OpdsSource internal constructor(
    override val id: Long,
    override val name: String,
    private val catalogUrl: String,
    private val client: OkHttpClient,
    private val parser: OpdsFeedParser = OpdsFeedParser(),
    private val username: String? = null,
    private val password: String? = null,
) : BrowsableSource {

    override val kind: SourceKind = SourceKind.OPDS

    private val baseUrl: HttpUrl = catalogUrl.toHttpUrl()

    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> {
        val entries = fetchFeed(catalogUrl)
        val series = entries.map { it.toSeries() }
        return PagedResult(items = series, hasNextPage = false)
    }

    override suspend fun search(query: String, page: Int): PagedResult<Series> {
        val url = baseUrl.newBuilder().addQueryParameter("query", query).build().toString()
        val entries = fetchFeed(url)
        val series = entries.map { it.toSeries() }
        return PagedResult(items = series, hasNextPage = false)
    }

    override suspend fun books(seriesRemoteId: String): List<Book> {
        val entries = fetchFeed(catalogUrl)
        return entries
            .filter { it.id == seriesRemoteId }
            .map { it.toBook() }
    }

    /**
     * OPDS-Atom-Einträge tragen keine reichhaltigen Serien-Metadaten (kein Summary,
     * Status oder Genre). Liefert daher höchstens das aus dem Feed bekannte Minimum
     * (Titel + Cover) oder `null`, wenn kein passender Eintrag existiert.
     */
    override suspend fun seriesDetail(seriesRemoteId: String): Series? =
        fetchFeed(catalogUrl).firstOrNull { it.id == seriesRemoteId }?.toSeries()

    override suspend fun pages(bookRemoteId: String): List<PageRef> = emptyList()

    override suspend fun openPage(ref: PageRef): ByteArray =
        throw UnsupportedOperationException("OPDS liest via Download")

    /**
     * Lädt das Buch über den Acquisition-Link herunter und liefert die rohen Bytes.
     * OPDS lädt die Datei am Stück (kein Stream-Fortschritt) — [onProgress] wird daher
     * best-effort genau einmal am Ende mit `(size, size)` gemeldet.
     */
    override suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): ByteArray {
        val entries = fetchFeed(catalogUrl)
        val entry = entries.firstOrNull { it.id == bookRemoteId }
            ?: error("Kein OPDS-Eintrag mit ID '$bookRemoteId' gefunden")
        val href = entry.acquisitionHref
            ?: error("Eintrag '$bookRemoteId' hat keinen Acquisition-Link")
        val absoluteUrl = baseUrl.resolve(href)
            ?: error("Ungültiger Acquisition-Href: $href")
        return withContext(Dispatchers.IO) {
            val request = buildRequest(absoluteUrl.toString())
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Download fehlgeschlagen: HTTP ${response.code} für $absoluteUrl"
                }
                response.body!!.bytes().also { onProgress(it.size.toLong(), it.size.toLong()) }
            }
        }
    }

    /**
     * Lädt das Cover (Thumbnail/Image-Link) des Eintrags. Im flachen OPDS-Katalog trägt
     * derselbe Eintrag Serien- und Buch-Cover — [isSeriesCover] spielt daher keine Rolle.
     * Ohne Cover-Link → leere Bytes (die UI zeigt dann den Platzhalter).
     */
    override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray {
        val entries = fetchFeed(catalogUrl)
        val href = entries.firstOrNull { it.id == remoteId }?.coverHref ?: return ByteArray(0)
        val absoluteUrl = baseUrl.resolve(href) ?: return ByteArray(0)
        return withContext(Dispatchers.IO) {
            val request = buildRequest(absoluteUrl.toString())
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) ByteArray(0) else response.body?.bytes() ?: ByteArray(0)
            }
        }
    }

    /**
     * In einem flachen OPDS-Katalog trägt derselbe Atom-Eintrag sowohl die Serie als auch
     * das Buch — die `remoteId` des Buchs ist zugleich die der Serie. Daher ist die
     * Serien-ID schlicht die übergebene Buch-ID.
     */
    override suspend fun seriesIdOf(bookRemoteId: String): String = bookRemoteId

    private suspend fun fetchFeed(url: String): List<OpdsEntry> = withContext(Dispatchers.IO) {
        val request = buildRequest(url)
        val xml = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Feed-Abruf fehlgeschlagen: HTTP ${response.code} für $url" }
            response.body!!.string()
        }
        parser.parse(xml)
    }

    /** Baut einen [Request] mit optionalem Basic-Auth-Header. */
    private fun buildRequest(url: String): Request {
        val builder = Request.Builder().url(url)
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            builder.header("Authorization", Credentials.basic(username, password))
        }
        return builder.build()
    }

    private fun OpdsEntry.toSeries(): Series = Series(
        id = 0L,
        sourceId = this@OpdsSource.id,
        remoteId = id,
        title = title,
        coverUrl = coverHref?.let { baseUrl.resolve(it)?.toString() },
    )

    private fun OpdsEntry.toBook(): Book = Book(
        id = 0L,
        sourceId = this@OpdsSource.id,
        seriesId = 0L,
        remoteId = id,
        title = title,
        format = opdsTypeToFormat(acquisitionType),
        pageCount = 0,
    )
}

/** Baut eine [OpdsSource] aus Name, Katalog-URL und optionalen Credentials zusammen. */
object OpdsSourceFactory {

    /**
     * Erstellt eine [OpdsSource]. Optionale Basic-Auth-Credentials ([username]/[password])
     * werden für alle Requests (Feed-Abruf, Download, Cover) als `Authorization`-Header gesetzt.
     * Komga OPDS akzeptiert keinen `X-API-Key`-Header — Basic-Auth ist der korrekte Weg.
     */
    fun create(
        name: String,
        catalogUrl: String,
        username: String? = null,
        password: String? = null,
    ): OpdsSource {
        val client = OkHttpClient.Builder().build()
        val id = SourceId.of(name, SourceKind.OPDS, catalogUrl)
        return OpdsSource(
            id = id,
            name = name,
            catalogUrl = catalogUrl,
            client = client,
            username = username,
            password = password,
        )
    }
}
