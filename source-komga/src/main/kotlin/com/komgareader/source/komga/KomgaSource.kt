package com.komgareader.source.komga

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.ContainerSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceContainer
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.source.SyncingSource
import com.komgareader.source.komga.dto.ReadProgressUpdateDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

private const val PAGE_SIZE = 50

/**
 * Komga-Backend hinter den Domain-Nähten. Browsing/Lesen über [BrowsableSource],
 * Fortschritts-Sync über [SyncingSource]. HTTP via Retrofit/OkHttp, Mapping via
 * [KomgaMapper]. Wird über [KomgaSourceFactory] zusammengebaut.
 */
class KomgaSource internal constructor(
    override val id: Long,
    override val name: String,
    private val api: KomgaApi,
    private val mapper: KomgaMapper,
) : BrowsableSource, SyncingSource, ContainerSource {

    override val kind: SourceKind = SourceKind.KOMGA

    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> {
        val libraryIds = filter.containerIds.ifEmpty { null }
        val response = api.listSeries(page = page, size = PAGE_SIZE, libraryIds = libraryIds)
        return PagedResult(response.content.map(mapper::toSeries), hasNextPage = !response.last)
    }

    override suspend fun listContainers(): List<SourceContainer> =
        api.listLibraries().map { SourceContainer(id = it.id, name = it.name) }

    override suspend fun search(query: String, page: Int): PagedResult<Series> {
        val response = api.listSeries(page = page, size = PAGE_SIZE, search = query)
        return PagedResult(response.content.map(mapper::toSeries), hasNextPage = !response.last)
    }

    override suspend fun seriesDetail(seriesRemoteId: String): Series =
        mapper.toSeries(api.getSeries(seriesRemoteId))

    override suspend fun books(seriesRemoteId: String): List<Book> {
        val response = api.listBooks(seriesRemoteId)
        check(response.last) {
            "Komga lieferte trotz unpaged=true paginierte Bücher für Serie $seriesRemoteId"
        }
        return response.content.map(mapper::toBook)
    }

    override suspend fun pages(bookRemoteId: String): List<PageRef> =
        mapper.toPageRefs(bookRemoteId, api.listPages(bookRemoteId))

    override suspend fun openPage(ref: PageRef): ByteArray =
        api.getPage(ref.bookRemoteId, ref.pageNumber).bytes()

    suspend fun downloadFile(bookRemoteId: String): ByteArray =
        api.getFile(bookRemoteId).bytes()

    /** Auflösen, zu welcher Serie ein Buch gehört (für die Kapitel-Liste des Webtoon-Strips). */
    suspend fun seriesIdOf(bookRemoteId: String): String =
        api.getBook(bookRemoteId).seriesId

    override suspend fun pushProgress(bookRemoteId: String, progress: ReadProgress) {
        api.updateProgress(bookRemoteId, ReadProgressUpdateDto(progress.page, progress.completed))
    }

    override suspend fun pullProgress(bookRemoteId: String): ReadProgress? {
        val book = api.getBook(bookRemoteId)
        // localBookId/updatedAt sind beim reinen Pull noch unbekannt → Persistenz (Plan 1.4) füllt sie.
        return mapper.toReadProgress(book, localBookId = 0L, updatedAt = 0L)
    }
}

/** Baut eine [KomgaSource] aus Verbindungsdaten zusammen. */
object KomgaSourceFactory {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Erstellt eine [KomgaSource].
     *
     * Authentifizierungsreihenfolge:
     * 1. [apiKey] gesetzt → API-Key-Header (`X-API-Key`)
     * 2. [username] + [password] gesetzt → HTTP Basic Auth
     * 3. Sonst → [IllegalArgumentException]
     */
    fun create(
        name: String,
        baseUrl: String,
        apiKey: String? = null,
        username: String? = null,
        password: String? = null,
    ): KomgaSource {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val authInterceptor = when {
            !apiKey.isNullOrBlank() -> ApiKeyInterceptor(apiKey)
            !username.isNullOrBlank() && !password.isNullOrBlank() -> BasicAuthInterceptor(username, password)
            else -> throw IllegalArgumentException("Kein Auth: API-Key oder Benutzer+Passwort nötig")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val id = SourceId.of(name, SourceKind.KOMGA, normalizedBase)
        return KomgaSource(
            id = id,
            name = name,
            api = retrofit.create(KomgaApi::class.java),
            mapper = KomgaMapper(sourceId = id, baseUrl = normalizedBase),
        )
    }
}
