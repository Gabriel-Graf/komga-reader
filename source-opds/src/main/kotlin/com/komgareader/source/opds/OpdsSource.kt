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

/** PSE-Href-Platzhalter für die Seitennummer (0-basiert per PSE-Spec). */
private const val PSE_PAGE_PLACEHOLDER = "{pageNumber}"

/**
 * OPDS-Katalog als [BrowsableSource]. Liest einen Atom-Feed via OkHttp und bildet
 * Einträge auf [Series] und [Book] ab.
 *
 * **Katalog-Form — zwei Wege:**
 * - **Hierarchisch (Komga/Kavita/Stump/Ubooquity/Calibre):** der konfigurierte `catalogUrl`-Feed
 *   listet *Serien* als Navigations-Einträge (`navigationHref` = Subsection-Link). [browse] zeigt
 *   sie; [books] **folgt** dem Subsection-Link auf den Acquisition-Feed der Serie und liest dort
 *   die Bücher. Eine Navigations-Ebene (Serie→Bücher); tiefere Bäume sind noch nicht abgedeckt.
 * - **Flach:** listet `catalogUrl` direkt Acquisition-Einträge, ist jeder Eintrag zugleich Serie
 *   und Buch (Rückwärtskompatibilität) — [books] gibt dann den Eintrag selbst zurück.
 *
 * **Lesepfad — zwei Wege, je nach Server-Fähigkeit:**
 * - **PSE (OPDS Page Streaming Extension):** trägt ein Eintrag einen PSE-Stream-Link
 *   (`pseTemplateHref`/`pseCount`), liefert [pages] echte [PageRef]s und [openPage] streamt
 *   die einzelne Seite — der Reader paginiert ohne Voll-Download (gleicher Pfad wie Komga).
 * - **whole-file:** ohne PSE liefert [pages] eine leere Liste → der Reader lädt das ganze
 *   Buch über [downloadFile] (MuPDF/Reflow). EPUB/PDF gehen immer diesen Weg.
 *
 * **Identität & Cache:** `remoteId` bleibt die stabile OPDS-`<id>` (Progress/DB überleben). Da
 * Buch-Operationen ([pages]/[openPage]/[downloadFile]/[coverBytes]) nur die `remoteId` bekommen,
 * die nötigen URLs aber im Feed-Eintrag stehen, werden geparste Einträge in [entriesById] gecacht
 * (unveränderliche URLs/Vorlagen → keine Stale-Sorge), die Serien-Subsection-Feeds in
 * [seriesAcqFeed]. Der Lesepfad ruft stets [browse]→[books] vor dem Öffnen → Cache warm; kalte
 * Buch-Ops holen den `catalogUrl`-Feed best-effort nach (deckt den Flat-Fall).
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

    /** Jeder je geparste Eintrag (Serie und Buch), keyed by OPDS-`<id>` — Quelle für Buch-Ops. */
    @Volatile private var entriesById: Map<String, OpdsEntry> = emptyMap()

    /** Serien-`remoteId` → absolute URL ihres Acquisition-(Bücher-)Feeds (aus dem Subsection-Link). */
    @Volatile private var seriesAcqFeed: Map<String, String> = emptyMap()

    /** Buch-`remoteId` → `remoteId` der Serie, in deren Feed es entdeckt wurde (für [seriesIdOf]). */
    @Volatile private var bookSeries: Map<String, String> = emptyMap()

    /**
     * Löst einen Eintrag über die `remoteId` auf. Cache-Treffer bevorzugt; bei kaltem Cache wird
     * der `catalogUrl`-Feed einmal nachgeholt.
     *
     * **Bekannte Grenze (hierarchisch + kalt):** im hierarchischen Katalog trägt `catalogUrl` nur
     * Serien-Nav-Einträge, nicht die Bücher. Wird eine Buch-Op aufgerufen, ohne dass [books] vorher
     * den Serien-Feed geladen hat (z. B. Prozess-Neustart direkt in den Reader), bleibt das Buch
     * unbekannt → der catalog-Refetch findet es nicht. Der normale Lesepfad Library→SeriesDetail→Reader
     * ruft [books] stets zuvor → Cache warm. Härtung (Buch→Feed persistieren) ist Soll.
     */
    private suspend fun entryFor(remoteId: String): OpdsEntry? =
        entriesById[remoteId] ?: run { fetchFeed(catalogUrl); entriesById[remoteId] }

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

    /**
     * Bücher einer Serie. Hierarchisch: dem Subsection-Link der Serie auf ihren Acquisition-Feed
     * folgen und die dortigen Buch-Einträge abbilden. Flach: der Serien-Eintrag *ist* das Buch.
     */
    override suspend fun books(seriesRemoteId: String): List<Book> {
        // Eltern-Feed mindestens einmal gesehen haben, damit der Subsection-Link bekannt ist.
        if (seriesAcqFeed[seriesRemoteId] == null && entriesById[seriesRemoteId] == null) {
            fetchFeed(catalogUrl)
        }
        seriesAcqFeed[seriesRemoteId]?.let { acqFeedUrl ->
            val entries = fetchFeed(acqFeedUrl).filter { it.isReadable() }
            bookSeries = bookSeries + entries.associate { it.id to seriesRemoteId }
            return entries.map { it.toBook() }
        }
        // Flat-Fall: kein Subsection-Link → der Serien-Eintrag selbst ist das Buch.
        val entry = entriesById[seriesRemoteId] ?: return emptyList()
        return if (entry.isReadable()) listOf(entry.toBook()) else emptyList()
    }

    /** Ein Eintrag ist als Buch lesbar, wenn er einen Download- oder PSE-Stream-Link trägt. */
    private fun OpdsEntry.isReadable(): Boolean = acquisitionHref != null || pseCount != null

    /**
     * OPDS-Atom-Einträge tragen keine reichhaltigen Serien-Metadaten (kein Summary,
     * Status oder Genre). Liefert daher höchstens das aus dem Feed bekannte Minimum
     * (Titel + Cover) oder `null`, wenn kein passender Eintrag existiert.
     */
    override suspend fun seriesDetail(seriesRemoteId: String): Series? =
        entryFor(seriesRemoteId)?.toSeries()

    /**
     * Liefert seitenweise [PageRef]s, wenn der Eintrag PSE anbietet (`pseCount`/`pseTemplateHref`).
     * Jede [PageRef] trägt die absolute, 0-basiert eingesetzte Seiten-URL. Ohne PSE → leere Liste,
     * der Reader fällt dann auf den whole-file-Download zurück.
     */
    override suspend fun pages(bookRemoteId: String): List<PageRef> {
        val entry = entryFor(bookRemoteId) ?: return emptyList()
        val count = entry.pseCount ?: return emptyList()
        val template = entry.pseTemplateHref ?: return emptyList()
        return (1..count).map { n ->
            PageRef(
                index = n - 1,
                bookRemoteId = bookRemoteId,
                pageNumber = n,
                url = baseUrl.resolve(pseHref(template, n))?.toString().orEmpty(),
            )
        }
    }

    /**
     * Streamt eine einzelne PSE-Seite. Die Stream-Vorlage kommt aus dem [entriesById]-Cache
     * (von [pages]/[books] gewärmt); fehlt sie, wird der Feed einmal nachgeholt. Hat der Eintrag kein
     * PSE, gibt es keine streambare Seite → [UnsupportedOperationException] (der Reader liest
     * solche Bücher über [downloadFile], nicht über diesen Pfad).
     */
    override suspend fun openPage(ref: PageRef): ByteArray {
        val template = entryFor(ref.bookRemoteId)?.pseTemplateHref
            ?: throw UnsupportedOperationException(
                "OPDS-Eintrag '${ref.bookRemoteId}' bietet kein PSE-Streaming — liest via Download",
            )
        val absoluteUrl = baseUrl.resolve(pseHref(template, ref.pageNumber))
            ?: error("Ungültiger PSE-Href für Seite ${ref.pageNumber}")
        return withContext(Dispatchers.IO) {
            val request = buildRequest(absoluteUrl.toString())
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Seiten-Abruf fehlgeschlagen: HTTP ${response.code} für $absoluteUrl"
                }
                response.body!!.bytes()
            }
        }
    }

    /** Setzt die 0-basierte Seitennummer in die PSE-Vorlage ein (`{pageNumber}`, Spec = 0-basiert). */
    private fun pseHref(template: String, pageNumber: Int): String =
        template.replace(PSE_PAGE_PLACEHOLDER, (pageNumber - 1).toString())

    /**
     * Lädt das Buch über den Acquisition-Link herunter und liefert die rohen Bytes.
     * OPDS lädt die Datei am Stück (kein Stream-Fortschritt) — [onProgress] wird daher
     * best-effort genau einmal am Ende mit `(size, size)` gemeldet.
     */
    override suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): ByteArray {
        val entry = entryFor(bookRemoteId)
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
        val href = entryFor(remoteId)?.coverHref ?: return ByteArray(0)
        val absoluteUrl = baseUrl.resolve(href) ?: return ByteArray(0)
        return withContext(Dispatchers.IO) {
            val request = buildRequest(absoluteUrl.toString())
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) ByteArray(0) else response.body?.bytes() ?: ByteArray(0)
            }
        }
    }

    /**
     * Serie eines Buchs. Hierarchisch: die beim [books]-Aufruf entdeckte Eltern-Serie aus dem
     * [bookSeries]-Cache. Flach (oder unbekannt): der Eintrag ist seine eigene Serie → Buch-ID.
     */
    override suspend fun seriesIdOf(bookRemoteId: String): String =
        bookSeries[bookRemoteId] ?: bookRemoteId

    private suspend fun fetchFeed(url: String): List<OpdsEntry> = withContext(Dispatchers.IO) {
        val request = buildRequest(url)
        val xml = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Feed-Abruf fehlgeschlagen: HTTP ${response.code} für $url" }
            response.body!!.string()
        }
        val entries = parser.parse(xml)
        // Einträge + Serien-Subsection-Feeds dieses Feeds in die Caches mergen
        // (unveränderliche URLs/Vorlagen → für spätere Buch-Ops, keine Stale-Sorge).
        entriesById = entriesById + entries.associateBy { it.id }
        val navs = entries.mapNotNull { e ->
            e.navigationHref?.let { e.id to (baseUrl.resolve(it)?.toString() ?: it) }
        }
        if (navs.isNotEmpty()) seriesAcqFeed = seriesAcqFeed + navs
        entries
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
        pageCount = pseCount ?: 0,
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
