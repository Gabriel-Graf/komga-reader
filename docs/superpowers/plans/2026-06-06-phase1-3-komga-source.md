# Phase 1 · Plan 3/4 — KomgaSource Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Eine `KomgaSource` hinter den Domain-Interfaces `BrowsableSource`/`SyncingSource`, die nativ mit der Komga-REST-API spricht (browse, search, books, pages, Seiten-Bytes streamen, Fortschritt push/pull) — voll host-testbar gegen einen Mock-HTTP-Server.

**Architecture:** Neues Kotlin/JVM-Modul `:source-komga` (kein Android → läuft im Host-JVM-Test). Retrofit + OkHttp + kotlinx.serialization. DTOs spiegeln die echte Komga-API (verifiziert gegen gotson/komga). Ein reiner `KomgaMapper` (Pure Functions, TDD) übersetzt DTOs → Domain-Modelle; `KomgaSource` orchestriert die HTTP-Calls. Tests nutzen OkHttp `MockWebServer` mit echten Komga-JSON-Antworten.

**Tech Stack:** Kotlin/JVM · Retrofit 2 · OkHttp · retrofit2-kotlinx-serialization-converter · kotlinx.serialization · JUnit5 · MockWebServer · kotlinx-coroutines-test.

## Referenz: verifizierter Komga-API-Kontrakt
- Auth: Header `X-API-Key: <key>` (empfohlen für Read-Client).
- `GET /api/v1/series?page={0-based}&size={n}&search={q}` → Spring `Page<SeriesDto>` (Envelope-Feld `content`, `last`, `totalPages`, `number`).
- `GET /api/v1/series/{id}/books?unpaged=true` → `Page<BookDto>`.
- `GET /api/v1/books/{id}/pages` → `List<PageDto>` (nicht paged).
- `GET /api/v1/books/{id}/pages/{number}` → rohe Bild-Bytes (1-basiert).
- `GET /api/v1/books/{id}` → `BookDto` (für `readProgress`).
- `PATCH /api/v1/books/{id}/read-progress` Body `{page, completed}` → 204.
- Cover: `GET /api/v1/series/{id}/thumbnail`.
- `mediaType`: `application/zip`→CBZ, `application/x-rar-compressed`(*)→CBR, `application/pdf`→PDF, `application/epub+zip`→EPUB.

---

### Task 0: Modul `:source-komga` anlegen

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `source-komga/build.gradle.kts`

- [ ] **Step 1: Version-Catalog erweitern**

In `gradle/libs.versions.toml` ergänze unter `[versions]`:
```toml
retrofit = "2.11.0"
okhttp = "4.12.0"
serialization = "1.7.3"
retrofitSerialization = "1.0.0"
```
unter `[libraries]`:
```toml
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
retrofit-serialization-converter = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version.ref = "retrofitSerialization" }
```
unter `[plugins]`:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Modul registrieren**

In `settings.gradle.kts` ergänze nach `include(":domain")`:
```kotlin
include(":source-komga")
```

- [ ] **Step 3: Modul-Build anlegen**

Create `source-komga/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.serialization.converter)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
```

- [ ] **Step 4: Build verifizieren**

Run: `./gradlew :source-komga:build`
Expected: BUILD SUCCESSFUL (leeres Modul).

- [ ] **Step 5: Commit**
```bash
git add settings.gradle.kts gradle/libs.versions.toml source-komga/build.gradle.kts
git commit -m "build: Modul :source-komga (Retrofit/OkHttp/Serialization)"
```

---

### Task 1: Komga-DTOs (kotlinx.serialization)

**Files:**
- Create: `source-komga/src/main/kotlin/com/komgareader/source/komga/dto/KomgaDtos.kt`

Nur die Felder, die der Client wirklich nutzt — `@Serializable` mit `ignoreUnknownKeys` (in Task 2 am Json gesetzt), daher müssen nicht alle Felder deklariert sein.

- [ ] **Step 1: DTOs anlegen**

Create `KomgaDtos.kt`:
```kotlin
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
```

- [ ] **Step 2: Kompilieren**
Run: `./gradlew :source-komga:compileKotlin` → SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/dto/KomgaDtos.kt
git commit -m "feat(komga): DTOs fuer Series/Book/Page/ReadProgress"
```

---

### Task 2: Retrofit-API + Json-Konfiguration

**Files:**
- Create: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaApi.kt`

- [ ] **Step 1: Retrofit-Interface anlegen**

Create `KomgaApi.kt`:
```kotlin
package com.komgareader.source.komga

import com.komgareader.source.komga.dto.BookDto
import com.komgareader.source.komga.dto.KomgaPage
import com.komgareader.source.komga.dto.PageDto
import com.komgareader.source.komga.dto.ReadProgressUpdateDto
import com.komgareader.source.komga.dto.SeriesDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Komga-REST-Endpunkte (v1). Pfade ohne führenden Slash → relativ zur baseUrl `.../api/v1/`. */
interface KomgaApi {

    @GET("series")
    suspend fun listSeries(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("search") search: String? = null,
    ): KomgaPage<SeriesDto>

    @GET("series/{id}/books")
    suspend fun listBooks(
        @Path("id") seriesId: String,
        @Query("unpaged") unpaged: Boolean = true,
    ): KomgaPage<BookDto>

    @GET("books/{id}")
    suspend fun getBook(@Path("id") bookId: String): BookDto

    @GET("books/{id}/pages")
    suspend fun listPages(@Path("id") bookId: String): List<PageDto>

    @GET("books/{id}/pages/{number}")
    @Streaming
    suspend fun getPage(
        @Path("id") bookId: String,
        @Path("number") pageNumber: Int,
    ): ResponseBody

    @PATCH("books/{id}/read-progress")
    suspend fun updateProgress(
        @Path("id") bookId: String,
        @Body body: ReadProgressUpdateDto,
    )
}
```

- [ ] **Step 2: Kompilieren**
Run: `./gradlew :source-komga:compileKotlin` → SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaApi.kt
git commit -m "feat(komga): Retrofit-API-Interface"
```

---

### Task 3: Format-Erkennung (mediaType → BookFormat, TDD)

**Files:**
- Create: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaFormat.kt`
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaFormatTest.kt`

- [ ] **Step 1: Failing-Test**

Create `KomgaFormatTest.kt`:
```kotlin
package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class KomgaFormatTest {

    @Test
    fun `zip ergibt CBZ`() {
        assertEquals(BookFormat.CBZ, mediaTypeToFormat("application/zip"))
    }

    @Test
    fun `rar in allen Varianten ergibt CBR`() {
        assertEquals(BookFormat.CBR, mediaTypeToFormat("application/x-rar-compressed"))
        assertEquals(BookFormat.CBR, mediaTypeToFormat("application/x-rar-compressed; version=5"))
    }

    @Test
    fun `pdf ergibt PDF`() {
        assertEquals(BookFormat.PDF, mediaTypeToFormat("application/pdf"))
    }

    @Test
    fun `epub ergibt EPUB`() {
        assertEquals(BookFormat.EPUB, mediaTypeToFormat("application/epub+zip"))
    }

    @Test
    fun `unbekannter Typ faellt auf CBZ zurueck`() {
        assertEquals(BookFormat.CBZ, mediaTypeToFormat("application/octet-stream"))
    }
}
```
Run `./gradlew :source-komga:test --tests "*KomgaFormatTest"` → RED.

- [ ] **Step 2: Implementieren**

Create `KomgaFormat.kt`:
```kotlin
package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat

/**
 * Bildet Komgas `mediaType` auf das interne [BookFormat] ab. CBR meldet je nach
 * RAR-Version Suffixe (`; version=5`), daher Präfix-Match. Unbekanntes wird als
 * CBZ behandelt (häufigster Bild-Container).
 */
fun mediaTypeToFormat(mediaType: String): BookFormat = when {
    mediaType.startsWith("application/x-rar-compressed") -> BookFormat.CBR
    mediaType.startsWith("application/pdf") -> BookFormat.PDF
    mediaType.startsWith("application/epub+zip") -> BookFormat.EPUB
    else -> BookFormat.CBZ
}
```
Run test → GREEN (5 pass).

- [ ] **Step 3: Commit**
```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaFormat.kt source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaFormatTest.kt
git commit -m "feat(komga): mediaType -> BookFormat (TDD)"
```

---

### Task 4: KomgaMapper (DTO → Domain, TDD)

**Files:**
- Create: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaMapper.kt`
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaMapperTest.kt`

Lokale DB-IDs (`Series.id`, `Book.id`, `Book.seriesId`) werden erst beim Persistieren (Plan 1.4) vergeben — der Mapper setzt sie auf `0L` und füllt `remoteId`/`sourceId`/Inhaltsfelder.

- [ ] **Step 1: Failing-Test**

Create `KomgaMapperTest.kt`:
```kotlin
package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DownloadState
import com.komgareader.source.komga.dto.BookDto
import com.komgareader.source.komga.dto.BookMediaDto
import com.komgareader.source.komga.dto.PageDto
import com.komgareader.source.komga.dto.ReadProgressDto
import com.komgareader.source.komga.dto.SeriesDto
import com.komgareader.source.komga.dto.SeriesMetadataDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KomgaMapperTest {

    private val mapper = KomgaMapper(sourceId = 77, baseUrl = "https://nas.local/api/v1/")

    @Test
    fun `Series uebernimmt remoteId, Titel und baut Cover-URL`() {
        val dto = SeriesDto(id = "S1", name = "Berserk", metadata = SeriesMetadataDto(title = "Berserk Deluxe"))
        val series = mapper.toSeries(dto)
        assertEquals(77, series.sourceId)
        assertEquals("S1", series.remoteId)
        assertEquals("Berserk Deluxe", series.title) // metadata.title hat Vorrang
        assertEquals("https://nas.local/api/v1/series/S1/thumbnail", series.coverUrl)
    }

    @Test
    fun `Series faellt auf name zurueck wenn metadata-title leer`() {
        val dto = SeriesDto(id = "S2", name = "Solo Leveling")
        assertEquals("Solo Leveling", mapper.toSeries(dto).title)
    }

    @Test
    fun `Book mappt Format, Seitenzahl und remoteIds`() {
        val dto = BookDto(
            id = "B1", seriesId = "S1", name = "Vol. 1",
            media = BookMediaDto(mediaType = "application/zip", pagesCount = 220),
        )
        val book = mapper.toBook(dto)
        assertEquals("B1", book.remoteId)
        assertEquals(77, book.sourceId)
        assertEquals(BookFormat.CBZ, book.format)
        assertEquals(220, book.pageCount)
        assertEquals(DownloadState.REMOTE, book.downloadState)
    }

    @Test
    fun `PageRefs verweisen auf den Seiten-Endpunkt (1-basiert)`() {
        val pages = listOf(PageDto(number = 1), PageDto(number = 2))
        val refs = mapper.toPageRefs(bookRemoteId = "B1", pages = pages)
        assertEquals(2, refs.size)
        assertEquals(0, refs[0].index) // 0-basierter interner Index
        assertEquals("https://nas.local/api/v1/books/B1/pages/1", refs[0].url)
        assertEquals("https://nas.local/api/v1/books/B1/pages/2", refs[1].url)
    }

    @Test
    fun `ReadProgress wird uebernommen wenn vorhanden`() {
        val dto = BookDto(
            id = "B1", seriesId = "S1", name = "x",
            media = BookMediaDto(mediaType = "application/zip", pagesCount = 100),
            readProgress = ReadProgressDto(page = 42, completed = false),
        )
        val progress = mapper.toReadProgress(dto, localBookId = 9, updatedAt = 123)!!
        assertEquals(9, progress.bookId)
        assertEquals(42, progress.page)
        assertEquals(100, progress.totalPages)
        assertEquals(false, progress.completed)
        assertEquals(123, progress.updatedAt)
    }

    @Test
    fun `ReadProgress ist null wenn Buch nie geoeffnet`() {
        val dto = BookDto(id = "B1", seriesId = "S1", name = "x",
            media = BookMediaDto(mediaType = "application/pdf", pagesCount = 10))
        assertNull(mapper.toReadProgress(dto, localBookId = 9, updatedAt = 1))
    }
}
```
Run → RED.

- [ ] **Step 2: Implementieren**

Create `KomgaMapper.kt`:
```kotlin
package com.komgareader.source.komga

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.DownloadState
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.source.PageRef
import com.komgareader.source.komga.dto.BookDto
import com.komgareader.source.komga.dto.PageDto
import com.komgareader.source.komga.dto.SeriesDto

/** Lokale DB-ID, die erst beim Persistieren vergeben wird. */
private const val UNASSIGNED_ID = 0L

/**
 * Übersetzt Komga-DTOs in Domain-Modelle. Reine Funktionen — keine I/O.
 * [baseUrl] endet auf `.../api/v1/` und dient zum Bau absoluter Cover-/Seiten-URLs.
 */
class KomgaMapper(private val sourceId: Long, private val baseUrl: String) {

    fun toSeries(dto: SeriesDto): Series = Series(
        id = UNASSIGNED_ID,
        sourceId = sourceId,
        remoteId = dto.id,
        title = dto.metadata.title.ifBlank { dto.name },
        coverUrl = "${baseUrl}series/${dto.id}/thumbnail",
    )

    fun toBook(dto: BookDto): Book = Book(
        id = UNASSIGNED_ID,
        sourceId = sourceId,
        seriesId = UNASSIGNED_ID,
        remoteId = dto.id,
        title = dto.metadata.title.ifBlank { dto.name },
        format = mediaTypeToFormat(dto.media.mediaType),
        pageCount = dto.media.pagesCount,
        downloadState = DownloadState.REMOTE,
    )

    fun toPageRefs(bookRemoteId: String, pages: List<PageDto>): List<PageRef> =
        pages.map { p ->
            PageRef(index = p.number - 1, url = "${baseUrl}books/$bookRemoteId/pages/${p.number}")
        }

    fun toReadProgress(dto: BookDto, localBookId: Long, updatedAt: Long): ReadProgress? {
        val rp = dto.readProgress ?: return null
        return ReadProgress(
            bookId = localBookId,
            page = rp.page,
            totalPages = dto.media.pagesCount,
            completed = rp.completed,
            updatedAt = updatedAt,
        )
    }
}
```
Run test → GREEN (6 pass).

- [ ] **Step 3: Commit**
```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaMapper.kt source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaMapperTest.kt
git commit -m "feat(komga): KomgaMapper DTO->Domain (TDD)"
```

---

### Task 5: Auth-Interceptor (X-API-Key, TDD via MockWebServer)

**Files:**
- Create: `source-komga/src/main/kotlin/com/komgareader/source/komga/ApiKeyInterceptor.kt`
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/ApiKeyInterceptorTest.kt`

- [ ] **Step 1: Failing-Test**

Create `ApiKeyInterceptorTest.kt`:
```kotlin
package com.komgareader.source.komga

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiKeyInterceptorTest {

    private val server = MockWebServer()

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `setzt X-API-Key-Header auf jede Anfrage`() {
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(ApiKeyInterceptor("geheim123")).build()

        client.newCall(Request.Builder().url(server.url("/api/v1/series")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("geheim123", recorded.getHeader("X-API-Key"))
    }
}
```
Run → RED.

- [ ] **Step 2: Implementieren**

Create `ApiKeyInterceptor.kt`:
```kotlin
package com.komgareader.source.komga

import okhttp3.Interceptor
import okhttp3.Response

/** Hängt den Komga-API-Key an jede Anfrage (`X-API-Key`). */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-API-Key", apiKey)
            .build()
        return chain.proceed(request)
    }
}
```
Run test → GREEN.

- [ ] **Step 3: Commit**
```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/ApiKeyInterceptor.kt source-komga/src/test/kotlin/com/komgareader/source/komga/ApiKeyInterceptorTest.kt
git commit -m "feat(komga): ApiKeyInterceptor (X-API-Key, TDD)"
```

---

### Task 6: KomgaSource + Factory (MockWebServer-Integrationstests)

**Files:**
- Create: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaSource.kt`
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaSourceTest.kt`

- [ ] **Step 1: Failing-Test (mit echten Komga-JSON-Antworten)**

Create `KomgaSourceTest.kt`:
```kotlin
package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KomgaSourceTest {

    private val server = MockWebServer()
    private fun source(): KomgaSource =
        KomgaSourceFactory.create(name = "Mein Komga", baseUrl = server.url("/api/v1/").toString(), apiKey = "k")

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `browse mappt Series-Seite und hasNextPage`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"content":[
              {"id":"S1","name":"Berserk","booksCount":3,"metadata":{"title":"Berserk","status":"ONGOING"}},
              {"id":"S2","name":"Saga","booksCount":9,"metadata":{"title":"","status":"ENDED"}}
            ],"last":false,"number":0,"totalPages":4}
        """.trimIndent()).addHeader("Content-Type", "application/json"))

        val result = source().browse(page = 0, filter = SourceFilter())

        assertEquals(2, result.items.size)
        assertEquals("S1", result.items[0].remoteId)
        assertEquals("Saga", result.items[1].title) // Fallback auf name
        assertTrue(result.hasNextPage) // last=false
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v1/series?"))
        assertEquals("k", req.getHeader("X-API-Key"))
    }

    @Test
    fun `search reicht die Query durch`() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[],"last":true,"number":0,"totalPages":0}"""))
        source().search(query = "luffy", page = 0)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("search=luffy"), "Pfad war: ${req.path}")
    }

    @Test
    fun `books mappt Buchliste mit Format und Seitenzahl`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"content":[
              {"id":"B1","seriesId":"S1","name":"v01","media":{"mediaType":"application/zip","pagesCount":220},"metadata":{"title":"Vol. 1"}}
            ],"last":true,"number":0,"totalPages":1}
        """.trimIndent()))

        val books = source().books("S1")
        assertEquals(1, books.size)
        assertEquals("B1", books[0].remoteId)
        assertEquals(BookFormat.CBZ, books[0].format)
        assertEquals(220, books[0].pageCount)
        assertEquals("Vol. 1", books[0].title)
    }

    @Test
    fun `pages liefert PageRefs auf den Seiten-Endpunkt`() = runTest {
        server.enqueue(MockResponse().setBody("""
            [{"number":1,"fileName":"001.jpg","mediaType":"image/jpeg"},
             {"number":2,"fileName":"002.jpg","mediaType":"image/jpeg"}]
        """.trimIndent()))

        val refs: List<PageRef> = source().pages("B1")
        assertEquals(2, refs.size)
        assertEquals(0, refs[0].index)
        assertTrue(refs[1].url.endsWith("/books/B1/pages/2"))
    }

    @Test
    fun `openPage laedt die rohen Seiten-Bytes`() = runTest {
        server.enqueue(MockResponse().setBody("BILDBYTES"))
        val ref = PageRef(index = 0, url = server.url("/api/v1/books/B1/pages/1").toString())
        val bytes = source().openPage(ref)
        assertEquals("BILDBYTES", bytes.decodeToString())
    }

    @Test
    fun `pushProgress sendet PATCH mit page und completed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val progress = ReadProgress(bookId = 9, page = 55, totalPages = 220, updatedAt = 1)
        source().pushProgress("B1", progress)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertTrue(req.path!!.endsWith("/books/B1/read-progress"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"page\":55"), "Body war: $body")
        assertTrue(body.contains("\"completed\":false"))
    }

    @Test
    fun `pullProgress liest readProgress aus dem Buch`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"id":"B1","seriesId":"S1","name":"v01","media":{"mediaType":"application/zip","pagesCount":220},
             "readProgress":{"page":80,"completed":false}}
        """.trimIndent()))

        val progress = source().pullProgress("B1")!!
        assertEquals(80, progress.page)
        assertEquals(220, progress.totalPages)
    }
}
```
Run `./gradlew :source-komga:test --tests "*KomgaSourceTest"` → RED.

- [ ] **Step 2: KomgaSource + Factory implementieren**

Create `KomgaSource.kt`:
```kotlin
package com.komgareader.source.komga

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.source.SyncingSource
import com.komgareader.source.komga.dto.ReadProgressUpdateDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
) : BrowsableSource, SyncingSource {

    override val kind: SourceKind = SourceKind.KOMGA

    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> {
        val response = api.listSeries(page = page, size = PAGE_SIZE)
        return PagedResult(response.content.map(mapper::toSeries), hasNextPage = !response.last)
    }

    override suspend fun search(query: String, page: Int): PagedResult<Series> {
        val response = api.listSeries(page = page, size = PAGE_SIZE, search = query)
        return PagedResult(response.content.map(mapper::toSeries), hasNextPage = !response.last)
    }

    override suspend fun books(seriesRemoteId: String): List<Book> =
        api.listBooks(seriesRemoteId).content.map(mapper::toBook)

    override suspend fun pages(bookRemoteId: String): List<PageRef> =
        mapper.toPageRefs(bookRemoteId, api.listPages(bookRemoteId))

    override suspend fun openPage(ref: PageRef): ByteArray {
        val number = ref.url.substringAfterLast("/").toInt()
        val bookId = ref.url.substringBeforeLast("/pages/").substringAfterLast("/books/")
        return api.getPage(bookId, number).bytes()
    }

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

    fun create(name: String, baseUrl: String, apiKey: String): KomgaSource {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(apiKey))
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
```
Run test → GREEN (7 pass).

- [ ] **Step 3: Commit**
```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaSource.kt source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaSourceTest.kt
git commit -m "feat(komga): KomgaSource + Factory (MockWebServer-Tests)"
```

---

### Task 7: E2E-Test (voller Fluss gegen MockWebServer)

**Files:**
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaE2ETest.kt`

- [ ] **Step 1: E2E-Test schreiben**

Create `KomgaE2ETest.kt`:
```kotlin
package com.komgareader.source.komga

import com.komgareader.domain.model.ReadProgress
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-End: ein simulierter Komga-Server liefert eine Serie → Bücher → Seiten →
 * Bytes, und nimmt einen Fortschritts-Push entgegen. Beweist die ganze Quelle als
 * Kette, nicht nur einzelne Calls.
 */
class KomgaE2ETest {

    private val server = MockWebServer()

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `browse bis Fortschritts-Push als durchgehender Fluss`() = runTest {
        // 1. Serie browsen
        server.enqueue(MockResponse().setBody("""{"content":[{"id":"S1","name":"Berserk","metadata":{"title":"Berserk"}}],"last":true,"number":0,"totalPages":1}"""))
        // 2. Bücher der Serie
        server.enqueue(MockResponse().setBody("""{"content":[{"id":"B1","seriesId":"S1","name":"v01","media":{"mediaType":"application/zip","pagesCount":2},"metadata":{"title":"Vol. 1"}}],"last":true,"number":0,"totalPages":1}"""))
        // 3. Seiten des Buchs
        server.enqueue(MockResponse().setBody("""[{"number":1,"mediaType":"image/jpeg"},{"number":2,"mediaType":"image/jpeg"}]"""))
        // 4. Bytes der ersten Seite
        server.enqueue(MockResponse().setBody("PAGE1"))
        // 5. Fortschritt push (204)
        server.enqueue(MockResponse().setResponseCode(204))

        val source = KomgaSourceFactory.create("Mein Komga", server.url("/api/v1/").toString(), "k")

        val series = source.browse(0, com.komgareader.domain.source.SourceFilter()).items.single()
        assertEquals("Berserk", series.title)

        val book = source.books(series.remoteId).single()
        assertEquals(2, book.pageCount)

        val pages = source.pages(book.remoteId)
        assertEquals(2, pages.size)

        val bytes = source.openPage(pages.first())
        assertEquals("PAGE1", bytes.decodeToString())

        source.pushProgress(book.remoteId, ReadProgress(bookId = 1, page = 1, totalPages = 2, updatedAt = 1))

        // Verifiziere die fünf Requests in Reihenfolge
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/series?"))
        assertTrue(server.takeRequest().path!!.endsWith("/series/S1/books?unpaged=true"))
        assertTrue(server.takeRequest().path!!.endsWith("/books/B1/pages"))
        assertTrue(server.takeRequest().path!!.endsWith("/books/B1/pages/1"))
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertTrue(patch.path!!.endsWith("/books/B1/read-progress"))
    }
}
```
Run `./gradlew :source-komga:test --tests "*KomgaE2ETest"` → GREEN.

- [ ] **Step 2: Voller Modul-Test**
Run: `./gradlew :source-komga:test` → alle Tests grün (5 Format + 6 Mapper + 1 Interceptor + 7 Source + 1 E2E = 20).

- [ ] **Step 3: Commit**
```bash
git add source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaE2ETest.kt
git commit -m "test(komga): E2E browse->books->pages->bytes->push"
```

---

## Self-Review-Notiz (Autor)
- **Spec-Abdeckung:** §5 KomgaSource (REST, browse/search/books/pages/stream/sync) → Tasks 2,6; native Auth → Task 5; Format-Mapping → Task 3; DTO→Domain → Task 4; offline-first localBookId/updatedAt bewusst in Persistenz (Plan 1.4) verschoben (Mapper-Parameter vorbereitet).
- **Bewusst verschoben:** Deprecated-GET vs. POST-`/list`: GET genügt fürs MVP (funktioniert weiter). Basic-Auth-Fallback: Interceptor-Struktur erlaubt spätere Variante, MVP nutzt X-API-Key. Cover-Download lädt der UI-Layer (Plan 1.4) über die gebaute `coverUrl`.
- **Typen-Konsistenz:** `mediaTypeToFormat` einheitlich; `KomgaMapper`-Signaturen (toSeries/toBook/toPageRefs/toReadProgress) decken sich zwischen Test und Impl; `PageRef.index` 0-basiert, Komga-`number` 1-basiert (im Mapper/`openPage` konsistent umgerechnet).
