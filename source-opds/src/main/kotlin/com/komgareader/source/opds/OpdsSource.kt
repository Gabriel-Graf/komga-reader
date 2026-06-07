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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OPDS-Katalog als [BrowsableSource]. Liest einen Atom-Feed via OkHttp und bildet
 * Einträge auf [Series] und [Book] ab. Lesen erfolgt über [downloadFile] + MuPDF;
 * seitenweises Streaming wird nicht unterstützt.
 */
class OpdsSource internal constructor(
    override val id: Long,
    override val name: String,
    private val catalogUrl: String,
    private val client: OkHttpClient,
    private val parser: OpdsFeedParser = OpdsFeedParser(),
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
     */
    override suspend fun downloadFile(bookRemoteId: String): ByteArray {
        val entries = fetchFeed(catalogUrl)
        val entry = entries.firstOrNull { it.id == bookRemoteId }
            ?: error("Kein OPDS-Eintrag mit ID '$bookRemoteId' gefunden")
        val href = entry.acquisitionHref
            ?: error("Eintrag '$bookRemoteId' hat keinen Acquisition-Link")
        val absoluteUrl = baseUrl.resolve(href)
            ?: error("Ungültiger Acquisition-Href: $href")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(absoluteUrl).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Download fehlgeschlagen: HTTP ${response.code} für $absoluteUrl"
                }
                response.body!!.bytes()
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
        val request = Request.Builder().url(url).build()
        val xml = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Feed-Abruf fehlgeschlagen: HTTP ${response.code} für $url" }
            response.body!!.string()
        }
        parser.parse(xml)
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

/** Baut eine [OpdsSource] aus Name und Katalog-URL zusammen. */
object OpdsSourceFactory {

    fun create(name: String, catalogUrl: String): OpdsSource {
        val client = OkHttpClient.Builder().build()
        val id = SourceId.of(name, SourceKind.OPDS, catalogUrl)
        return OpdsSource(
            id = id,
            name = name,
            catalogUrl = catalogUrl,
            client = client,
        )
    }
}
