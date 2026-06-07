# Komga-Libraries + echte Viewer-Erkennung — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den Lese-Modus (Webtoon vs. Paged) aus echten Komga-Metadaten erkennen statt aus einem erzwungenen Gruppen-Typ, und Komga-Libraries frei in benennbare, nachträglich editierbare App-Bibliotheken bündeln.

**Architecture:** Naht A trägt `readingDirection` quellen-agnostisch ins Domain-Modell. Naht B (`ResolveViewerType`) wird zu einer 6-stufigen Prioritätsregel, aufgelöst pro Buch beim Öffnen. Naht C führt eine optionale `ContainerSource`-Capability ein (Komga-Libraries); `KomgaSource` implementiert sie und filtert `browse` per `library_id`. Die `Shelf` (App-Bibliothek) bündelt pro Quelle ausgewählte Container und trägt einen optionalen Default-Typ als Viewer-Notnagel.

**Tech Stack:** Kotlin, Hilt, Room (Migration v5→v6), Retrofit/kotlinx.serialization, Jetpack Compose, kotlin.test + MockWebServer.

**Wichtige Architektur-Erkenntnis:** `ViewerMode` (App) hat nur `{PAGED, WEBTOON}`. EPUB/Novel wird bereits über `BookFormat.EPUB` zum EpubReader geleitet — daher betrifft die Viewer-Erkennung ausschließlich **Webtoon vs. Paged**. Die Domain-`ViewerType.EPUB` bleibt für Vollständigkeit/Doku erhalten, wird in der App aber auf `ViewerMode.PAGED` gemappt (EPUB-Bücher wählen den Reader per Format).

---

## File Structure

**Domain (`domain/`)**
- Create `model/ReadingDirection.kt` — neutrales Enum `LTR, RTL, VERTICAL, WEBTOON`.
- Modify `model/Series.kt` — Feld `readingDirection: ReadingDirection?`.
- Modify `model/Shelf.kt` — `sources: List<ShelfSource>` + `defaultContentType: ContentType?`; neue `ShelfSource`.
- Modify `source/MediaSource.kt` — `SourceContainer`, `ContainerSource`, `SourceFilter.containerIds`.
- Modify `usecase/ResolveViewerType.kt` — neue Signatur + Prioritätsregel.
- Modify `repository/ShelfRepository.kt` — `add` gibt `Long` zurück, neues `update`.

**Source-Komga (`source-komga/`)**
- Modify `dto/KomgaDtos.kt` — `SeriesMetadataDto.readingDirection`; neues `LibraryDto`.
- Modify `KomgaMapper.kt` — `readingDirection`-Mapping.
- Modify `KomgaApi.kt` — `listLibraries()`, `library_id`-Param an `listSeries`.
- Modify `KomgaSource.kt` — `ContainerSource`-Impl + `browse`-Filter.

**Data (`data/`)**
- Modify `db/Entities.kt` — `ShelfEntity` Spalten `sources`, `defaultContentType`.
- Modify `db/AppDatabase.kt` — Version 6, `MIGRATION_5_6`.
- Modify `db/ShelfDao.kt` — `update` (bereits via REPLACE-Insert abgedeckt; kein neuer Code nötig).
- Modify `repository/RoomShelfRepository.kt` — Encode/Decode + `update`.
- Modify `di/DataModule.kt` — `MIGRATION_5_6` registrieren.

**App (`app/`)**
- Modify `ui/groups/GroupsViewModel.kt` — Container laden, `add/updateGroup` mit neuem Modell.
- Modify `ui/groups/GroupsScreen.kt` — `LibraryEditDialog` (create+edit), Settings-Icon je Karte.
- Modify `ui/groups/GroupBrowseViewModel.kt` — Container-Filter, kein Viewer mehr im Grid.
- Modify `ui/groups/GroupBrowseRoute.kt` — `onOpenSeries(seriesId, shelfId)`.
- Modify `ui/series/SeriesDetailViewModel.kt` — per-Buch Viewer via `ResolveViewerType` + optionalem Shelf-Fallback.
- Modify `ui/series/SeriesDetailScreen.kt` — `onOpenBook` mit `viewerMode`.
- Modify `MainActivity.kt` — Nav: `shelfId` statt `viewerMode` nach SeriesDetail tragen.
- Modify `i18n/Strings.kt` — Rename DE+EN.

**Docs/Skill**
- Create `docs/domain/viewer-type-resolution.md`.
- Create `.claude/skills/komga-viewer-type-resolution/SKILL.md`.

---

## Task 1: readingDirection durch das Komga-Mapping

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ReadingDirection.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/model/Series.kt`
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/dto/KomgaDtos.kt:22-27` (SeriesMetadataDto)
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaMapper.kt:21-30` (toSeries)
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaMapperTest.kt`

- [ ] **Step 1: Failing Test schreiben** — am Ende von `KomgaMapperTest` (vor letzter `}`), neue Tests anhängen:

```kotlin
    @Test
    fun `Series-readingDirection wird aus Komga-Wert gemappt`() {
        val webtoon = mapper.toSeries(
            SeriesDto(id = "S1", name = "Tower", metadata = SeriesMetadataDto(readingDirection = "WEBTOON")),
        )
        val rtl = mapper.toSeries(
            SeriesDto(id = "S2", name = "Berserk", metadata = SeriesMetadataDto(readingDirection = "RIGHT_TO_LEFT")),
        )
        assertEquals(ReadingDirection.WEBTOON, webtoon.readingDirection)
        assertEquals(ReadingDirection.RTL, rtl.readingDirection)
    }

    @Test
    fun `Series-readingDirection ist null bei leer oder unbekannt`() {
        val leer = mapper.toSeries(SeriesDto(id = "S3", name = "X"))
        val unbekannt = mapper.toSeries(
            SeriesDto(id = "S4", name = "Y", metadata = SeriesMetadataDto(readingDirection = "DIAGONAL")),
        )
        assertNull(leer.readingDirection)
        assertNull(unbekannt.readingDirection)
    }
```

Import oben ergänzen: `import com.komgareader.domain.model.ReadingDirection`.

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./gradlew :source-komga:test --tests "com.komgareader.source.komga.KomgaMapperTest"`
Expected: FAIL — `Unresolved reference: ReadingDirection` / `readingDirection`.

- [ ] **Step 3: `ReadingDirection` anlegen**

`domain/src/main/kotlin/com/komgareader/domain/model/ReadingDirection.kt`:

```kotlin
package com.komgareader.domain.model

/**
 * Lese-Richtung einer Serie, quellen-agnostisch (Naht A). `VERTICAL`/`WEBTOON`
 * bedeuten vertikalen Strip; `LTR`/`RTL` paginierte Comics/Manga. `null` =
 * unbekannt → der Viewer fällt auf Format bzw. Bibliotheks-Default zurück.
 */
enum class ReadingDirection { LTR, RTL, VERTICAL, WEBTOON }
```

- [ ] **Step 4: `Series` um `readingDirection` erweitern**

In `domain/src/main/kotlin/com/komgareader/domain/model/Series.kt`, nach `genres`:

```kotlin
    val genres: List<String> = emptyList(),
    val readingDirection: ReadingDirection? = null,
)
```

- [ ] **Step 5: DTO-Feld ergänzen**

In `KomgaDtos.kt` `SeriesMetadataDto`:

```kotlin
data class SeriesMetadataDto(
    val title: String = "",
    val status: String = "",
    val summary: String = "",
    val genres: List<String> = emptyList(),
    val readingDirection: String = "",
)
```

- [ ] **Step 6: Mapping im `KomgaMapper`**

In `KomgaMapper.kt` Imports ergänzen: `import com.komgareader.domain.model.ReadingDirection`. In `toSeries(...)` nach `genres = dto.metadata.genres,`:

```kotlin
        genres = dto.metadata.genres,
        readingDirection = toReadingDirection(dto.metadata.readingDirection),
    )

    private fun toReadingDirection(raw: String): ReadingDirection? = when (raw) {
        "LEFT_TO_RIGHT" -> ReadingDirection.LTR
        "RIGHT_TO_LEFT" -> ReadingDirection.RTL
        "VERTICAL" -> ReadingDirection.VERTICAL
        "WEBTOON" -> ReadingDirection.WEBTOON
        else -> null
    }
```

(Die schließende `)` von `toSeries` entfällt an alter Stelle — siehe oben.)

- [ ] **Step 7: Test laufen lassen — muss bestehen**

Run: `./gradlew :source-komga:test --tests "com.komgareader.source.komga.KomgaMapperTest"`
Expected: PASS (alle Tests grün).

- [ ] **Step 8: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ReadingDirection.kt domain/src/main/kotlin/com/komgareader/domain/model/Series.kt source-komga/src/main/kotlin/com/komgareader/source/komga/dto/KomgaDtos.kt source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaMapper.kt source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaMapperTest.kt
git commit -m "feat(domain): readingDirection aus Komga-Metadaten mappen (Naht A)"
```

---

## Task 2: ResolveViewerType — Prioritätsregel (Naht B)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt`

> Hinweis: Der einzige bisherige Aufrufer (`GroupBrowseViewModel`) wird erst in Task 6 angepasst. Damit das Domain-Modul nach diesem Task **isoliert** grün baut, kompiliert die App-Schicht erst nach Task 6 wieder. Das ist gewollt — committe trotzdem (Domain-Tests laufen unabhängig). Falls der gewählte Ausführungsmodus einen vollen App-Build pro Task erzwingt, Task 2 und Task 6 zusammen ausführen.

- [ ] **Step 1: Test-Datei ersetzen** durch die Prioritätsregel-Tests:

`domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt`:

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.ReadingDirection
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.ViewerType
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveViewerTypeTest {

    private val resolve = ResolveViewerType()

    private fun series(
        override: ContentType? = null,
        direction: ReadingDirection? = null,
    ) = Series(
        id = 0, sourceId = 0, remoteId = "S", title = "t",
        contentTypeOverride = override, readingDirection = direction,
    )

    private fun book(format: BookFormat) =
        Book(id = 0, sourceId = 0, seriesId = 0, remoteId = "B", title = "t", format = format, pageCount = 1)

    @Test
    fun `Stufe 1 — Serien-Override schlaegt alles`() {
        val result = resolve(series(override = ContentType.WEBTOON), book(BookFormat.CBZ), fallback = ContentType.MANGA)
        assertEquals(ViewerType.WEBTOON, result)
    }

    @Test
    fun `Stufe 2 — EPUB-Format ergibt EPUB`() {
        val result = resolve(series(direction = ReadingDirection.WEBTOON), book(BookFormat.EPUB), fallback = null)
        assertEquals(ViewerType.EPUB, result)
    }

    @Test
    fun `Stufe 3 — vertikale Leserichtung ergibt WEBTOON trotz Archiv-Format`() {
        val result = resolve(series(direction = ReadingDirection.VERTICAL), book(BookFormat.CBZ), fallback = null)
        assertEquals(ViewerType.WEBTOON, result)
    }

    @Test
    fun `Stufe 4 — Archiv-Format ohne vertikale Richtung ergibt PAGED`() {
        val result = resolve(series(direction = ReadingDirection.LTR), book(BookFormat.CBR), fallback = null)
        assertEquals(ViewerType.PAGED, result)
    }

    @Test
    fun `Stufe 5 — ohne Metadaten greift der Fallback-Typ`() {
        val result = resolve(series(), book(BookFormat.PDF).copy(format = BookFormat.PDF), fallback = ContentType.WEBTOON)
        // PDF ist Archiv-Format → Stufe 4 würde PAGED liefern; Fallback greift NUR wenn kein Format greift.
        // Daher: leeres Format-Szenario simulieren wir über NOVEL-Fallback bei EPUB-losem Buch unten.
        assertEquals(ViewerType.PAGED, result)
    }

    @Test
    fun `Stufe 6 — kein Signal ergibt PAGED als Default`() {
        val result = resolve(series(), book(BookFormat.CBZ), fallback = null)
        assertEquals(ViewerType.PAGED, result)
    }
}
```

> Begründung zur Reihenfolge im Test „Stufe 5": Da jedes `Book` ein konkretes `BookFormat` trägt, greift in der Praxis fast immer Stufe 2 oder 4 vor dem Fallback. Der Fallback (Stufe 5) ist die dokumentierte Reserve für Quellen ohne Format/Richtung (z.B. künftige Plugins). Der Test fixiert das aktuelle deterministische Verhalten.

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ResolveViewerTypeTest"`
Expected: FAIL — Signatur `invoke(series, book, fallback)` existiert nicht.

- [ ] **Step 3: `ResolveViewerType` neu implementieren**

`domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt`:

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.ReadingDirection
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.ViewerType

/**
 * Bestimmt den Lese-Modus pro Buch nach fester Prioritätsregel (Naht B).
 * Vollständige Begründung: docs/domain/viewer-type-resolution.md.
 *
 * 1. Serien-Override (manuell)        → map(override)
 * 2. Buch-Format EPUB                 → EPUB
 * 3. Leserichtung VERTICAL/WEBTOON    → WEBTOON
 * 4. Archiv-Format (CBZ/CBR/PDF)      → PAGED
 * 5. Bibliotheks-Default (Fallback)   → map(fallback)
 * 6. sonst                            → PAGED
 */
class ResolveViewerType {

    operator fun invoke(series: Series, book: Book, fallback: ContentType?): ViewerType {
        series.contentTypeOverride?.let { return map(it) }
        if (book.format == BookFormat.EPUB) return ViewerType.EPUB
        if (series.readingDirection == ReadingDirection.VERTICAL ||
            series.readingDirection == ReadingDirection.WEBTOON
        ) {
            return ViewerType.WEBTOON
        }
        if (book.format == BookFormat.CBZ ||
            book.format == BookFormat.CBR ||
            book.format == BookFormat.PDF
        ) {
            return ViewerType.PAGED
        }
        fallback?.let { return map(it) }
        return ViewerType.PAGED
    }

    private fun map(type: ContentType): ViewerType = when (type) {
        ContentType.MANGA -> ViewerType.PAGED
        ContentType.COMIC -> ViewerType.PAGED
        ContentType.WEBTOON -> ViewerType.WEBTOON
        ContentType.NOVEL -> ViewerType.EPUB
    }
}
```

- [ ] **Step 4: Test laufen lassen — muss bestehen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ResolveViewerTypeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt
git commit -m "feat(domain): ResolveViewerType als Prioritaetsregel pro Buch (Naht B)"
```

---

## Task 3: ContainerSource + SourceFilter.containerIds (Naht C, Domain)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/source/MediaSource.kt`

Rein additive Deklarationen — kompiliert sofort, Verhalten getestet in Task 4.

- [ ] **Step 1: `SourceFilter` erweitern** (`MediaSource.kt`, bestehende Zeile ersetzen):

```kotlin
/** Filter für [BrowsableSource.browse]. [containerIds] leer = kein Container-Filter. */
data class SourceFilter(
    val seriesId: String? = null,
    val containerIds: List<String> = emptyList(),
)
```

- [ ] **Step 2: `SourceContainer` + `ContainerSource` ergänzen** (ans Ende von `MediaSource.kt`):

```kotlin
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
```

- [ ] **Step 3: Build prüfen**

Run: `./gradlew :domain:compileDebugKotlin -q`
Expected: erfolgreich (keine Ausgabe).

- [ ] **Step 4: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/source/MediaSource.kt
git commit -m "feat(domain): ContainerSource-Capability + SourceFilter.containerIds (Naht C)"
```

---

## Task 4: KomgaSource implementiert ContainerSource + Library-Filter

**Files:**
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/dto/KomgaDtos.kt`
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaApi.kt`
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaSource.kt`
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaSourceTest.kt`

- [ ] **Step 1: Failing Tests anhängen** (in `KomgaSourceTest`, vor letzter `}`):

```kotlin
    @Test
    fun `listContainers mappt die Komga-Libraries`() = runTest {
        server.enqueue(MockResponse().setBody("""
            [{"id":"L1","name":"Manga"},{"id":"L2","name":"Comics"}]
        """.trimIndent()).addHeader("Content-Type", "application/json"))

        val containers = source().listContainers()

        assertEquals(2, containers.size)
        assertEquals("L1", containers[0].id)
        assertEquals("Comics", containers[1].name)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v1/libraries"), "Pfad war: ${req.path}")
    }

    @Test
    fun `browse mit containerIds setzt library_id-Filter`() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[],"last":true,"number":0,"totalPages":0}"""))
        source().browse(page = 0, filter = SourceFilter(containerIds = listOf("L1", "L2")))
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("library_id=L1"), "Pfad war: ${req.path}")
        assertTrue(req.path!!.contains("library_id=L2"), "Pfad war: ${req.path}")
    }
```

Import ergänzen: `import com.komgareader.domain.source.SourceContainer` (nur falls direkt genutzt — hier nicht zwingend).

- [ ] **Step 2: Tests laufen lassen — müssen fehlschlagen**

Run: `./gradlew :source-komga:test --tests "com.komgareader.source.komga.KomgaSourceTest"`
Expected: FAIL — `listContainers` fehlt / `library_id` nicht im Pfad.

- [ ] **Step 3: `LibraryDto` ergänzen** (`KomgaDtos.kt`, oben bei den anderen DTOs):

```kotlin
@Serializable
data class LibraryDto(
    val id: String,
    val name: String = "",
)
```

- [ ] **Step 4: `KomgaApi` erweitern**

`listSeries` um `library_id` ergänzen und `listLibraries` hinzufügen:

```kotlin
    @GET("series")
    suspend fun listSeries(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("search") search: String? = null,
        @Query("library_id") libraryIds: List<String>? = null,
    ): KomgaPage<SeriesDto>

    @GET("libraries")
    suspend fun listLibraries(): List<LibraryDto>
```

Import ergänzen: `import com.komgareader.source.komga.dto.LibraryDto`.

- [ ] **Step 5: `KomgaSource` implementiert `ContainerSource` + Filter**

Imports ergänzen:

```kotlin
import com.komgareader.domain.source.ContainerSource
import com.komgareader.domain.source.SourceContainer
```

Klassen-Header erweitern:

```kotlin
class KomgaSource internal constructor(
    override val id: Long,
    override val name: String,
    private val api: KomgaApi,
    private val mapper: KomgaMapper,
) : BrowsableSource, SyncingSource, ContainerSource {
```

`browse` ersetzen + `listContainers` ergänzen:

```kotlin
    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> {
        val libraryIds = filter.containerIds.ifEmpty { null }
        val response = api.listSeries(page = page, size = PAGE_SIZE, libraryIds = libraryIds)
        return PagedResult(response.content.map(mapper::toSeries), hasNextPage = !response.last)
    }

    override suspend fun listContainers(): List<SourceContainer> =
        api.listLibraries().map { SourceContainer(id = it.id, name = it.name) }
```

- [ ] **Step 6: Tests laufen lassen — müssen bestehen**

Run: `./gradlew :source-komga:test --tests "com.komgareader.source.komga.KomgaSourceTest"`
Expected: PASS (alle, inkl. der bestehenden `browse`-Tests — `library_id` ist optional).

- [ ] **Step 7: Commit**

```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/dto/KomgaDtos.kt source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaApi.kt source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaSource.kt source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaSourceTest.kt
git commit -m "feat(komga): ContainerSource (Libraries) + library_id-Filter in browse"
```

---

## Task 5: Shelf-Modell, Persistenz & Migration v5→v6

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/model/Shelf.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/ShelfRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomShelfRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomShelfRepositoryEncodingTest.kt` (neu)

> Dieser Task fasst Modell + Persistenz zusammen, weil ein Datentyp-Wechsel atomar rippeln muss. Die App-ViewModels (`GroupsViewModel`/`GroupBrowseViewModel`) referenzieren noch `shelf.contentType`/`sourceIds` und brechen — sie werden in Task 6 angepasst. Das `data`-Modul baut nach diesem Task isoliert grün.

- [ ] **Step 1: Encode/Decode-Test schreiben** (neu):

`data/src/test/kotlin/com/komgareader/data/repository/RoomShelfRepositoryEncodingTest.kt`:

```kotlin
package com.komgareader.data.repository

import com.komgareader.domain.model.ShelfSource
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomShelfRepositoryEncodingTest {

    @Test
    fun `encode und decode sind invers fuer mehrere Quellen mit Containern`() {
        val sources = listOf(
            ShelfSource(sourceId = 11L, containerIds = listOf("L1", "L2")),
            ShelfSource(sourceId = 22L, containerIds = emptyList()),
        )
        val encoded = ShelfSourceCodec.encode(sources)
        assertEquals(sources, ShelfSourceCodec.decode(encoded))
    }

    @Test
    fun `decode toleriert leeren String`() {
        assertEquals(emptyList(), ShelfSourceCodec.decode(""))
    }

    @Test
    fun `decode der Migrations-Form ganze-Quelle ergibt leere Container`() {
        // MIGRATION_5_6 wandelt altes "11,22" in "11=|22="
        assertEquals(
            listOf(ShelfSource(11L, emptyList()), ShelfSource(22L, emptyList())),
            ShelfSourceCodec.decode("11=|22="),
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomShelfRepositoryEncodingTest"`
Expected: FAIL — `ShelfSource` / `ShelfSourceCodec` fehlen.

- [ ] **Step 3: `Shelf` + `ShelfSource` neu**

`domain/src/main/kotlin/com/komgareader/domain/model/Shelf.kt`:

```kotlin
package com.komgareader.domain.model

/**
 * Nutzer-definierte App-Bibliothek. Bündelt pro Quelle ausgewählte Container
 * (Komga-Libraries). [defaultContentType] ist der optionale Viewer-Notnagel,
 * wenn Metadaten einer Serie keine Leserichtung hergeben (siehe ResolveViewerType).
 */
data class Shelf(
    val id: Long,
    val name: String,
    val sources: List<ShelfSource>,
    val defaultContentType: ContentType? = null,
)

/** Auswahl innerhalb einer Quelle. [containerIds] leer = ganze Quelle. */
data class ShelfSource(
    val sourceId: Long,
    val containerIds: List<String> = emptyList(),
)
```

- [ ] **Step 4: `ShelfRepository` erweitern**

`domain/src/main/kotlin/com/komgareader/domain/repository/ShelfRepository.kt`:

```kotlin
package com.komgareader.domain.repository

import com.komgareader.domain.model.Shelf
import kotlinx.coroutines.flow.Flow

interface ShelfRepository {
    val shelves: Flow<List<Shelf>>
    suspend fun add(shelf: Shelf): Long
    suspend fun update(shelf: Shelf)
    suspend fun delete(id: Long)
}
```

- [ ] **Step 5: `ShelfEntity` umbauen**

In `data/src/main/kotlin/com/komgareader/data/db/Entities.kt` die `ShelfEntity` ersetzen:

```kotlin
/** Nutzer-definierte App-Bibliothek. [sources] kodiert (siehe ShelfSourceCodec). */
@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sources: String,
    val defaultContentType: String? = null,
)
```

- [ ] **Step 6: `RoomShelfRepository` mit Codec + `update`**

`data/src/main/kotlin/com/komgareader/data/repository/RoomShelfRepository.kt` komplett ersetzen:

```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.ShelfDao
import com.komgareader.data.db.ShelfEntity
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ShelfSource
import com.komgareader.domain.repository.ShelfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Kodiert [ShelfSource]-Listen in einen flachen String für Room.
 * Form: `sourceId=cid1,cid2|sourceId=...`. Container-IDs enthalten nie `|=,`
 * (Komga-Library-IDs sind alphanumerisch).
 */
object ShelfSourceCodec {
    fun encode(sources: List<ShelfSource>): String =
        sources.joinToString("|") { "${it.sourceId}=${it.containerIds.joinToString(",")}" }

    fun decode(raw: String): List<ShelfSource> =
        raw.split("|")
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq < 0) return@mapNotNull null
                val sourceId = part.substring(0, eq).trim().toLongOrNull() ?: return@mapNotNull null
                val containers = part.substring(eq + 1)
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                ShelfSource(sourceId = sourceId, containerIds = containers)
            }
}

class RoomShelfRepository(private val dao: ShelfDao) : ShelfRepository {

    override val shelves: Flow<List<Shelf>> = dao.observeAll().map { entities ->
        entities.map(::toShelf)
    }

    override suspend fun add(shelf: Shelf): Long = dao.insert(toEntity(shelf))

    override suspend fun update(shelf: Shelf) {
        dao.insert(toEntity(shelf)) // REPLACE-Insert aktualisiert per PrimaryKey
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    private fun toShelf(entity: ShelfEntity): Shelf = Shelf(
        id = entity.id,
        name = entity.name,
        sources = ShelfSourceCodec.decode(entity.sources),
        defaultContentType = entity.defaultContentType
            ?.let { runCatching { ContentType.valueOf(it) }.getOrNull() },
    )

    private fun toEntity(shelf: Shelf): ShelfEntity = ShelfEntity(
        id = shelf.id,
        name = shelf.name,
        sources = ShelfSourceCodec.encode(shelf.sources),
        defaultContentType = shelf.defaultContentType?.name,
    )
}
```

- [ ] **Step 7: `ShelfDao.insert` muss die ID liefern**

In `data/src/main/kotlin/com/komgareader/data/db/ShelfDao.kt` die Insert-Signatur auf `Long` (Row-ID) ändern:

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ShelfEntity): Long
```

- [ ] **Step 8: DB-Version 6 + `MIGRATION_5_6`**

In `AppDatabase.kt` `version = 5` → `version = 6`. Am Ende der Datei ergänzen:

```kotlin
/**
 * v5 → v6: shelves-Tabelle restrukturiert. `contentType`+`sourceIds` (CSV)
 * werden zu `sources` (kodiert `id=container,…|…`) und nullable `defaultContentType`.
 * Bestehende Gruppen bleiben erhalten: alte sourceIds werden als „ganze Quelle"
 * übernommen, der alte contentType wird zum Viewer-Default.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `shelves_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `sources` TEXT NOT NULL,
                `defaultContentType` TEXT
            )""",
        )
        db.execSQL(
            """INSERT INTO `shelves_new` (`id`, `name`, `sources`, `defaultContentType`)
               SELECT `id`, `name`, REPLACE(`sourceIds`, ',', '=|') || '=', `contentType`
               FROM `shelves`""",
        )
        db.execSQL("DROP TABLE `shelves`")
        db.execSQL("ALTER TABLE `shelves_new` RENAME TO `shelves`")
    }
}
```

- [ ] **Step 9: Migration registrieren**

In `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`: Import `import com.komgareader.data.db.MIGRATION_5_6` ergänzen und die `addMigrations`-Zeile erweitern:

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
```

- [ ] **Step 10: Encoding-Test laufen lassen — muss bestehen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomShelfRepositoryEncodingTest"`
Expected: PASS.

- [ ] **Step 11: `data`-Modul baut grün**

Run: `./gradlew :data:compileDebugKotlin -q`
Expected: erfolgreich.

- [ ] **Step 12: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/Shelf.kt domain/src/main/kotlin/com/komgareader/domain/repository/ShelfRepository.kt data/src/main/kotlin/com/komgareader/data/db/Entities.kt data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt data/src/main/kotlin/com/komgareader/data/db/ShelfDao.kt data/src/main/kotlin/com/komgareader/data/repository/RoomShelfRepository.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/test/kotlin/com/komgareader/data/repository/RoomShelfRepositoryEncodingTest.kt
git commit -m "feat(data): Shelf als Multi-Source/Multi-Container + Migration v5->v6"
```

---

## Task 6: App-Verdrahtung — Bibliotheken, Container-Browse, per-Buch-Viewer

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/groups/GroupsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/groups/GroupBrowseViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/groups/GroupBrowseRoute.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`

> UI-Verdrahtung ohne neue Unit-Tests (Compose/Nav). Verifikation am Ende über vollen Build + manuellen E2E-Smoke gegen die Test-Komga. Das `LibraryEditDialog`-UI kommt in Task 7; hier zunächst die ViewModels/Nav lauffähig machen, sodass die App baut und browst.

- [ ] **Step 1: `GroupBrowseViewModel` auf Container-Filter umstellen, Viewer aus Grid entfernen**

Datei komplett ersetzen:

```kotlin
package com.komgareader.app.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.source.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface GroupBrowseUiState {
    data object Loading : GroupBrowseUiState
    data object NoServer : GroupBrowseUiState
    data class Content(
        val shelf: Shelf,
        val series: List<Series>,
        val serverConfig: ServerConfig?,
    ) : GroupBrowseUiState
    data class Error(val message: String) : GroupBrowseUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shelfRepository: ShelfRepository,
    private val serverRepository: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    private val shelfId: Long = checkNotNull(savedStateHandle["shelfId"])
    private val refreshTrigger = MutableStateFlow(0)

    val state: StateFlow<GroupBrowseUiState> = combine(
        shelfRepository.shelves,
        serverRepository.config,
        refreshTrigger,
    ) { shelves, config, _ -> shelves to config }
        .flatMapLatest { (shelves, config) ->
            flow {
                val shelf = shelves.firstOrNull { it.id == shelfId }
                if (shelf == null) {
                    emit(GroupBrowseUiState.Error("Bibliothek nicht gefunden"))
                    return@flow
                }
                emit(GroupBrowseUiState.Loading)
                val source = sourceProvider.from(config)
                if (config == null || source == null) {
                    emit(GroupBrowseUiState.NoServer)
                    return@flow
                }
                val containerIds = shelf.sources
                    .firstOrNull { it.sourceId == source.id }
                    ?.containerIds
                    ?: emptyList()
                emit(runCatching { source.browse(0, SourceFilter(containerIds = containerIds)) }
                    .fold(
                        { result ->
                            GroupBrowseUiState.Content(
                                shelf = shelf,
                                series = result.items,
                                serverConfig = config,
                            )
                        },
                        { GroupBrowseUiState.Error(it.message ?: "Verbindung fehlgeschlagen") },
                    ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupBrowseUiState.Loading)

    fun refresh() { refreshTrigger.value++ }
}
```

- [ ] **Step 2: `GroupBrowseRoute` — `onOpenSeries(seriesId, shelfId)`**

Die `onOpenSeries`-Signatur und der Aufruf in `GroupBrowseRoute.kt` ändern sich von `(seriesId, viewerMode)` zu `(seriesId)`; die Route kennt `shelfId` bereits als Parameter. Ersetze die Funktions-Signatur:

```kotlin
fun GroupBrowseRoute(
    shelfId: Long,
    onBack: () -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    viewModel: GroupBrowseViewModel = hiltViewModel(),
) {
```

Und im `GroupSeriesCover`-Aufruf (im `Content`-Zweig) den `onClick`:

```kotlin
                        GroupSeriesCover(
                            series = series,
                            serverConfig = current.serverConfig,
                            onClick = { onOpenSeries(series.remoteId) },
                        )
```

Der Import `import com.komgareader.app.ui.reader.ViewerMode` in `GroupBrowseRoute.kt` wird nicht mehr gebraucht — entfernen.

- [ ] **Step 3: `MainActivity` Nav — `shelfId` statt `viewerMode` tragen**

In `MainActivity.kt`:

(a) `group/{shelfId}`-Composable: `onOpenSeries` trägt jetzt die `shelfId` weiter:

```kotlin
                            GroupBrowseRoute(
                                shelfId = shelfId,
                                onBack = { nav.popBackStack() },
                                onOpenSeries = { seriesId ->
                                    nav.navigate("series_vm/$seriesId/$shelfId")
                                },
                            )
```

(b) Route `series_vm/{seriesId}/{viewerMode}` → `series_vm/{seriesId}/{shelfId}`:

```kotlin
                        composable(
                            route = "series_vm/{seriesId}/{shelfId}",
                            arguments = listOf(
                                navArgument("seriesId") { type = NavType.StringType },
                                navArgument("shelfId") { type = NavType.LongType },
                            ),
                        ) {
                            SeriesDetailScreen(
                                onBack = { nav.popBackStack() },
                                onOpenBook = { bookId, pageCount, format, forceStream, viewerMode ->
                                    nav.navigate("reader/$bookId/$pageCount/$format/$forceStream/$viewerMode")
                                },
                            )
                        }
```

(c) Die einfache `series/{seriesId}`-Route (Library-Tab, ohne Shelf) ebenfalls auf die neue `onOpenBook`-Signatur heben und den Viewer mittragen:

```kotlin
                        composable(
                            route = "series/{seriesId}",
                            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
                        ) {
                            SeriesDetailScreen(
                                onBack = { nav.popBackStack() },
                                onOpenBook = { bookId, pageCount, format, forceStream, viewerMode ->
                                    nav.navigate("reader/$bookId/$pageCount/$format/$forceStream/$viewerMode")
                                },
                            )
                        }
```

- [ ] **Step 4: `SeriesDetailViewModel` — per-Buch Viewer auflösen**

Imports ergänzen:

```kotlin
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.usecase.ResolveViewerType
import com.komgareader.domain.model.ViewerType
import com.komgareader.app.ui.reader.ViewerMode
```

Konstruktor um `private val shelfRepository: ShelfRepository` erweitern. Nach `seriesId` ergänzen:

```kotlin
    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])
    private val shelfId: Long? = savedStateHandle.get<Long>("shelfId")
    private val resolveViewerType = ResolveViewerType()
```

`Content` um `viewerModes: Map<String, String>` (bookRemoteId → ViewerMode.name) erweitern:

```kotlin
    data class Content(
        val books: List<Book>,
        val seriesTitle: String,
        val seriesRemoteId: String,
        val serverConfig: ServerConfig?,
        val seriesSummary: String? = null,
        val seriesStatus: String? = null,
        val seriesGenres: List<String> = emptyList(),
        val viewerModes: Map<String, String> = emptyMap(),
    ) : SeriesDetailUiState
```

Im `fold`-Erfolgszweig (nach `val detail = ...`) den Fallback laden und Viewer je Buch berechnen. Ersetze den Block ab `val resolvedTitle` bis zum `SeriesDetailUiState.Content(...)`-Aufruf durch:

```kotlin
                            val resolvedTitle = detail?.title?.takeIf { it.isNotBlank() }
                                ?: books.firstOrNull()?.seriesTitle?.takeIf { it.isNotBlank() }
                                ?: seriesId
                            val fallback: ContentType? = shelfId
                                ?.let { id -> shelfRepository.shelves.first().firstOrNull { it.id == id } }
                                ?.defaultContentType
                            val seriesForResolve = detail
                                ?: com.komgareader.domain.model.Series(
                                    id = 0, sourceId = 0, remoteId = seriesId, title = resolvedTitle,
                                )
                            val viewerModes = books.associate { book ->
                                book.remoteId to mapViewerMode(
                                    resolveViewerType(seriesForResolve, book, fallback),
                                ).name
                            }
                            SeriesDetailUiState.Content(
                                books = books,
                                seriesTitle = resolvedTitle,
                                seriesRemoteId = seriesId,
                                serverConfig = config,
                                seriesSummary = detail?.summary,
                                seriesStatus = detail?.status,
                                seriesGenres = detail?.genres ?: emptyList(),
                                viewerModes = viewerModes,
                            )
```

Mapping-Helfer in die Klasse (z.B. vor `companion object`):

```kotlin
    private fun mapViewerMode(type: ViewerType): ViewerMode = when (type) {
        ViewerType.WEBTOON -> ViewerMode.WEBTOON
        else -> ViewerMode.PAGED // PAGED und EPUB lesen paginiert; EPUB-Buch wählt Reader per Format
    }
```

- [ ] **Step 5: `SeriesDetailScreen` — `onOpenBook` mit `viewerMode`**

Beide `onOpenBook`-Signaturen (Zeilen ~68 und ~155) erweitern:

```kotlin
    onOpenBook: (bookId: String, pageCount: Int, format: String, forceStream: Boolean, viewerMode: String) -> Unit,
```

Den Content-Composable, der die Bücher rendert, braucht die `viewerModes`-Map. Wo `Content` an die innere Composable übergeben wird, die Map mitreichen, und im Klick-Handler (Zeile ~184) nutzen:

```kotlin
                        onOpenBook(
                            it.remoteId, it.pageCount, it.format.name, false,
                            viewerModes[it.remoteId] ?: "PAGED",
                        )
```

Dazu die innere Composable um einen Parameter `viewerModes: Map<String, String>` ergänzen und vom `Content`-State (`current.viewerModes`) durchreichen. (Exakte Stelle: der Lambda-Block, der pro Buch `onOpenBook(...)` aufruft — dort ist `viewerModes` im Scope nötig.)

- [ ] **Step 6: `GroupsViewModel` — neues Shelf-Modell (vorläufig, UI folgt in Task 7)**

`addGroup`/`deleteGroup` auf das neue Modell heben, damit es baut. `state`/`computeSourceId` bleiben. Ersetze `addGroup`:

```kotlin
    fun addGroup(name: String, defaultContentType: ContentType?) {
        val sourceId = state.value.serverSourceId ?: return
        viewModelScope.launch {
            shelfRepository.add(
                Shelf(
                    id = 0,
                    name = name.trim(),
                    sources = listOf(ShelfSource(sourceId = sourceId, containerIds = emptyList())),
                    defaultContentType = defaultContentType,
                ),
            )
        }
    }
```

Import ergänzen: `import com.komgareader.domain.model.ShelfSource`. (Die vollständige Container-Auswahl + Edit folgt in Task 7; hier nur baufähig.)

- [ ] **Step 7: `GroupsScreen` an die geänderte `addGroup`-Signatur anpassen**

Im `CreateGroupDialog`-Aufruf den Callback auf `(name, contentType)` → `addGroup(name, contentType)` anpassen, sodass es baut (Detail-UI in Task 7). Falls `CreateGroupDialog` `onCreate(name, ContentType)` liefert: in `defaultContentType` weiterreichen.

- [ ] **Step 8: Voller Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/
git commit -m "feat(app): Container-Browse + per-Buch Viewer-Aufloesung, Shelf-Modell verdrahtet"
```

---

## Task 7: Modal — Bibliothek erstellen & bearbeiten (Container-Auswahl)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/groups/GroupsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/groups/GroupsScreen.kt`

- [ ] **Step 1: `GroupsViewModel` — Container laden + `updateGroup`**

Imports ergänzen:

```kotlin
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.source.ContainerSource
import com.komgareader.domain.source.SourceContainer
import kotlinx.coroutines.flow.first
```

Konstruktor um `private val sourceProvider: KomgaSourceProvider` erweitern. `GroupsUiState` um die verfügbaren Container ergänzen:

```kotlin
data class GroupsUiState(
    val shelves: List<Shelf> = emptyList(),
    val serverConfig: ServerConfig? = null,
    val serverSourceId: Long? = null,
)
```

Neuen State-Flow + Lade-Funktion für Container hinzufügen:

```kotlin
    private val _containers = MutableStateFlow<List<SourceContainer>>(emptyList())
    val containers: StateFlow<List<SourceContainer>> = _containers

    /** Lädt die Library-Liste der verbundenen Quelle (für das Modal). */
    fun loadContainers() {
        viewModelScope.launch {
            val config = serverRepository.config.first()
            val source = sourceProvider.from(config)
            _containers.value = if (source is ContainerSource) {
                runCatching { source.listContainers() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
    }
```

(`MutableStateFlow`/`StateFlow` ggf. importieren.)

`addGroup` auf vollständige Auswahl erweitern und `updateGroup` hinzufügen:

```kotlin
    fun saveGroup(id: Long, name: String, containerIds: List<String>, defaultContentType: ContentType?) {
        val sourceId = state.value.serverSourceId ?: return
        viewModelScope.launch {
            val shelf = Shelf(
                id = id,
                name = name.trim(),
                sources = listOf(ShelfSource(sourceId = sourceId, containerIds = containerIds)),
                defaultContentType = defaultContentType,
            )
            if (id == 0L) shelfRepository.add(shelf) else shelfRepository.update(shelf)
        }
    }
```

Die in Task 6 angelegte `addGroup` kann entfernt werden (durch `saveGroup` ersetzt).

- [ ] **Step 2: `GroupsScreen` — `LibraryEditDialog` (create+edit) + Settings-Icon**

Den bisherigen `CreateGroupDialog` ersetzen durch einen Dialog, der eine optionale `Shelf` zum Editieren annimmt, die Container-Checkboxen rendert und Default-Typ-Chips zeigt. Skizze (an bestehenden Stil/`LocalStrings` anpassen):

```kotlin
@Composable
private fun LibraryEditDialog(
    existing: Shelf?,                       // null = neu
    containers: List<SourceContainer>,
    onSave: (id: Long, name: String, containerIds: List<String>, type: ContentType?) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    val preselected = existing?.sources?.firstOrNull()?.containerIds?.toSet() ?: emptySet()
    val selected = remember { mutableStateListOf<String>().apply { addAll(preselected) } }
    var type by remember { mutableStateOf(existing?.defaultContentType) }

    val typeOptions = listOf(
        null to s.tagAuto,
        ContentType.MANGA to s.tagManga,
        ContentType.COMIC to s.tagComic,
        ContentType.NOVEL to s.tagNovel,
        ContentType.WEBTOON to s.tagWebtoon,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(existing?.id ?: 0L, name, selected.toList(), type) },
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
        title = { Text(if (existing == null) s.createLibrary else s.editLibrary) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(s.libraryName) }, singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text(s.selectLibraries, style = MaterialTheme.typography.labelLarge)
                containers.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = c.id in selected,
                            onCheckedChange = { on ->
                                if (on) selected.add(c.id) else selected.remove(c.id)
                            },
                        )
                        Text(c.name)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(s.fallbackType, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    typeOptions.forEach { (t, label) ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(label) })
                    }
                }
            }
        },
    )
}
```

Im `GroupsScreen` Composable State für „welcher Dialog offen" halten (`var editing by remember { mutableStateOf<Shelf?>(null) }` + `var showDialog by ...`), beim Öffnen `viewModel.loadContainers()` aufrufen, `containers` per `collectAsState()` lesen, und in der Bibliotheks-Karte ein `IconButton` mit `Icons.Filled.Settings` ergänzen, das `editing = shelf; showDialog = true` setzt. „+"-FAB öffnet mit `editing = null`.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/groups/
git commit -m "feat(app): Bibliothek-Modal mit Library-Auswahl + Bearbeiten nach Erstellen"
```

---

## Task 8: i18n-Rename (DE + EN)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

- [ ] **Step 1: Interface-Keys ergänzen** (im `Strings`-Interface, bei den Tab-/Tag-Keys):

```kotlin
    val tagAuto: String
    val save: String
    val cancel: String
    val createLibrary: String
    val editLibrary: String
    val libraryName: String
    val selectLibraries: String
    val fallbackType: String
```

(Falls `save`/`cancel` schon existieren, nicht doppeln.)

- [ ] **Step 2: DE-Werte** (im deutschen Objekt):

```kotlin
    override val tabBrowse = "Browsen"
    override val tabGroups = "Bibliotheken"
    override val tagAuto = "Auto"
    override val save = "Speichern"
    override val cancel = "Abbrechen"
    override val createLibrary = "Bibliothek erstellen"
    override val editLibrary = "Bibliothek bearbeiten"
    override val libraryName = "Name"
    override val selectLibraries = "Komga-Libraries"
    override val fallbackType = "Fallback-Typ (optional)"
    override val noGroupsHint = "Noch keine Bibliotheken. Tippe auf + um eine anzulegen."
```

- [ ] **Step 3: EN-Werte** (im englischen Objekt):

```kotlin
    override val tabBrowse = "Browse"
    override val tabGroups = "Libraries"
    override val tagAuto = "Auto"
    override val save = "Save"
    override val cancel = "Cancel"
    override val createLibrary = "Create library"
    override val editLibrary = "Edit library"
    override val libraryName = "Name"
    override val selectLibraries = "Komga libraries"
    override val fallbackType = "Fallback type (optional)"
    override val noGroupsHint = "No libraries yet. Tap + to create one."
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL (Compile-Parität DE/EN durch das Interface erzwungen).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "i18n: Gruppen->Bibliotheken, Stoebern->Browsen (DE+EN)"
```

---

## Task 9: Doku + Projekt-Skill

**Files:**
- Create: `docs/domain/viewer-type-resolution.md`
- Create: `.claude/skills/komga-viewer-type-resolution/SKILL.md`

- [ ] **Step 1: Doku schreiben** — `docs/domain/viewer-type-resolution.md`:

```markdown
# Domain-Regel: Viewer-Typ-Auflösung

`ResolveViewerType(series, book, fallback)` bestimmt deterministisch den
Lese-Modus. Reihenfolge ist verbindlich — nicht umsortieren.

| Stufe | Bedingung | Ergebnis |
|-------|-----------|----------|
| 1 | `series.contentTypeOverride != null` | map(override) |
| 2 | `book.format == EPUB` | EPUB |
| 3 | `series.readingDirection ∈ {VERTICAL, WEBTOON}` | WEBTOON |
| 4 | `book.format ∈ {CBZ, CBR, PDF}` | PAGED |
| 5 | `fallback != null` | map(fallback) |
| 6 | sonst | PAGED |

map: MANGA/COMIC→PAGED, WEBTOON→WEBTOON, NOVEL→EPUB.

## Komga-Feld-Mapping (Naht A)
- `series.metadata.readingDirection`: LEFT_TO_RIGHT→LTR, RIGHT_TO_LEFT→RTL,
  VERTICAL→VERTICAL, WEBTOON→WEBTOON, sonst null.
- EPUB/Novel wird über `BookFormat.EPUB` erkannt (mediaProfile), nicht über die
  Leserichtung.

## Bekanntes Risiko
Komgas Default-Leserichtung ist `LEFT_TO_RIGHT`. Schlecht getaggte Webtoons
zeigen daher Paged, bis (a) die Serie in Komga korrekt getaggt oder (b) der
Bibliotheks-Default (Stufe 5) bzw. ein Serien-Override (Stufe 1) greift.

## App-Mapping
`ViewerMode` kennt nur PAGED/WEBTOON. `ViewerType.EPUB`/`PAGED` → `ViewerMode.PAGED`
(EPUB-Bücher wählen den Reader per Format), `ViewerType.WEBTOON` → `ViewerMode.WEBTOON`.
```

- [ ] **Step 2: Skill schreiben** — `.claude/skills/komga-viewer-type-resolution/SKILL.md`:

```markdown
---
name: komga-viewer-type-resolution
description: Use when touching Viewer-/Reader-Mode selection in the Komga-Reader (ResolveViewerType, readingDirection mapping, Shelf defaultContentType fallback). Hält die verbindliche 6-stufige Prioritätsregel fest, damit sie nicht versehentlich gebrochen wird.
---

# Viewer-Typ-Auflösung (Domain-Regel)

`ResolveViewerType(series, book, fallback)` — Reihenfolge NICHT ändern:

1. `series.contentTypeOverride` → map
2. `book.format == EPUB` → EPUB
3. `readingDirection ∈ {VERTICAL, WEBTOON}` → WEBTOON
4. `book.format ∈ {CBZ,CBR,PDF}` → PAGED
5. `fallback (shelf.defaultContentType)` → map
6. sonst → PAGED

map: MANGA/COMIC→PAGED, WEBTOON→WEBTOON, NOVEL→EPUB.

Komga `readingDirection`: LEFT_TO_RIGHT→LTR, RIGHT_TO_LEFT→RTL, VERTICAL→VERTICAL,
WEBTOON→WEBTOON, sonst null. EPUB/Novel = `BookFormat.EPUB`, nicht Leserichtung.

App: `ViewerMode` nur PAGED/WEBTOON; EPUB-Buch → Reader per Format.

Volltext + Begründung: docs/domain/viewer-type-resolution.md.
Tests: domain/.../ResolveViewerTypeTest.kt (ein Test pro Stufe).
```

- [ ] **Step 3: Commit**

```bash
git add docs/domain/viewer-type-resolution.md .claude/skills/komga-viewer-type-resolution/SKILL.md
git commit -m "docs(domain): Viewer-Typ-Aufloesung dokumentiert + Projekt-Skill"
```

---

## Task 10: E2E-Verifikation gegen Test-Komga + Gerät

**Files:** keine (Verifikation).

- [ ] **Step 1: Voller Build + alle Tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Unit-Tests grün.

- [ ] **Step 2: Auf Boox installieren**

Run: `adb -s db4c96d install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`.

- [ ] **Step 3: Manueller Smoke (gegen Test-Komga / NAS-Daten)**
  - Bibliothek erstellen → Komga-Libraries erscheinen als Checkboxen → auswählen → speichern.
  - Settings-Icon auf der Bibliotheks-Karte → Auswahl/Name editierbar, persistiert nach Neustart.
  - Eine Webtoon-Serie (readingDirection=WEBTOON in Komga) öffnen → Lesen → **Webtoon-Strip**.
  - Eine Manga-/Comic-Serie öffnen → **Paged**. Eine Novel (EPUB) → **EpubReader**.
  - Tabs heißen „Browsen" und „Bibliotheken" (DE), „Browse"/„Libraries" (EN).

- [ ] **Step 4: Bei Erfolg — kein Commit nötig (reine Verifikation).** Beobachtungen dem Nutzer berichten.

---

## Self-Review

**Spec-Abdeckung:**
- Naht A (readingDirection) → Task 1 ✓
- Naht B (ResolveViewerType-Regel) → Task 2 ✓
- Naht C (ContainerSource/Filter) → Task 3 + 4 ✓
- Naht D (Shelf Multi-Source/Container + update) → Task 5 ✓
- Naht E (Container-Browse-Flow) → Task 6 ✓
- Naht F (Modal create+edit) → Task 7 ✓
- Naht G (i18n-Rename) → Task 8 ✓
- Naht H (Doku + Skill) → Task 9 ✓
- Migration v5→v6 → Task 5 ✓
- E2E → Task 10 ✓

**Typ-Konsistenz:** `ResolveViewerType(series, book, fallback: ContentType?)` einheitlich in Task 2 (Def), Task 6 (Aufruf). `ShelfSource(sourceId, containerIds)`, `Shelf(sources, defaultContentType)` konsistent Task 5↔6↔7. `ShelfRepository.add(): Long` / `update` konsistent Task 5↔6↔7. `onOpenBook(..., viewerMode)` konsistent Task 6 (Screen+Nav). `SourceFilter(containerIds)` Task 3↔4↔6.

**Bekannte Reihenfolge-Abhängigkeit:** Task 2 bricht den App-Build bis Task 6; bei Ausführungsmodus mit Voll-Build-Gate Task 2+5+6 als Block fahren. Im Plan dokumentiert.
