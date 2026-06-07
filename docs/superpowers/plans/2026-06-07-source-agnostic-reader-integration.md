# Source-Agnostic Reader Integration + OPDS-Canary — Implementierungs-Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die App von Komga-Hartverdrahtung lösen — ViewModels und Bild-/Byte-Laden gehen über `SourceManager` + die `MediaSource`-Naht statt über `KomgaSourceProvider`/`AuthHeaders`/Komga-URLs — und mit OPDS als end-to-end lesbarer Quelle beweisen, dass die Naht trägt.

**Architecture:** Eine agnostische Quellen-Grenze (`SourceManager` + `ActiveSource`-Resolver in `app/data`) ersetzt die `KomgaSourceProvider`-Injektion in allen ViewModels. Byte-/Seiten-Zugriff fließt über `BrowsableSource` (neue Naht-Methoden `downloadFile`, `seriesIdOf`; bestehende `openPage`), Bilder über einen Coil-`Fetcher`, der `openPage` aufruft — keine quellen-spezifischen URLs/Auth mehr in der UI. Der konkrete Quellen-Typ lebt nur noch in *einer* Wiring-Klasse. OPDS wird registriert und als Canary durchgelesen.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Coil 3 (custom `Fetcher`), Retrofit/OkHttp, Room, JUnit + MockWebServer.

**Verbindliche Regeln/Skills:** `source-agnostic-integration` (diese Arbeit setzt sie um), `big-picture-and-goals`, `architecture-seams` (Soll vs. Ist), `source-extensibility` (Kochrezept A/B), `docs-match-code`, `tdd`, `roadmap-and-invariants` (E2E pro Feature), `concurrent-worktree-sessions` (Novel-Branch ist parallele Session).

---

## Vorbemerkung — Stand & Koordination (lesen)

Der Roman-Reader (`feat/novel-reflow-reader`) ist weit: `ReaderChromeState` + `ReaderScaffold`
(geteilte Reader-Basis), `NovelReaderViewModel` + `NovelReaderScreen`, `EpubBytesLoader`
(zentralisiertes Byte-Holen für paged-EPUB **und** Novel), crengine-Engine, EPUB→NOVEL. Dieser
Refactor **baut auf diesem Stand auf** — nicht auf dem alten `master`.

**Zwei Naht-Punkte, die die Novel-Arbeit schon geschaffen hat und die dieser Plan agnostisch macht:**
1. `EpubBytesLoader` (`app/ui/reader/EpubBytesLoader.kt`) — zentralisiert „gib mir die Bytes eines
   Buchs", injiziert aber noch `KomgaSourceProvider` + ruft `source.downloadFile()`.
2. `ReaderScaffold`/`ReaderChromeState` — die geteilte Reader-Basis (Chrome/Tap/HW), schon agnostisch.

**Koordinationsstrategie (vom Koordinator bestätigt):** Novel zuerst. Phase 0 merget die aktuellen
Novel-Commits in den Refactor-Branch; gearbeitet wird darauf. Der Novel-Agent läuft parallel weiter;
der finale Merge wird vom Koordinator (Mensch) gemacht, wenn beide fertig sind.

**Ist-Kopplung, die dieser Plan beseitigt (verifiziert im Code):**
- 6 ViewModels injizieren `KomgaSourceProvider` (Library, Groups, GroupBrowse, SeriesDetail, ColorFilter, Reader).
- `EpubBytesLoader` injiziert `KomgaSourceProvider`.
- Konkrete KomgaSource-Methoden im App-Pfad: `downloadFile`, `seriesIdOf`, `pageRefsFromCount` (alle **nicht** auf `BrowsableSource`).
- `AuthHeaders.forCovers(config)` + Komga-URLs in 7 UI-Dateien → Coil lädt direkt mit Komga-Auth.
- `SourceManager` (domain) + `OpdsSource` (Modul) existieren, sind aber in `app` **nicht verdrahtet**.

---

## File Structure (was entsteht/sich ändert)

**Neu:**
- `app/src/main/kotlin/com/komgareader/app/data/ActiveSource.kt` — agnostische Quellen-Grenze (kapselt den konkreten Typ).
- `app/src/main/kotlin/com/komgareader/app/data/SourceRegistration.kt` — registriert Quellen aus `ServerConfig` in `SourceManager`.
- `app/src/main/kotlin/com/komgareader/app/data/coil/SourcePageFetcher.kt` + `SourcePageKeyer.kt` — Coil-`Fetcher`, der Seiten/Cover über `openPage` lädt.
- `app/src/main/kotlin/com/komgareader/app/data/coil/SourceImage.kt` — Coil-Model `data class SourceImage(sourceId, bookRemoteId, pageNumber)`.
- `domain/src/main/kotlin/com/komgareader/domain/source/PageRefs.kt` — pure Helper `buildPageRefs(bookRemoteId, pageCount)`.

**Geändert (domain — Naht erweitern, Kochrezept A):**
- `domain/source/MediaSource.kt` — `BrowsableSource` bekommt `downloadFile(bookRemoteId): ByteArray` und `seriesIdOf(bookRemoteId): String`.

**Geändert (Quellen-Module — jede implementiert die neuen Methoden):**
- `source-komga/.../KomgaSource.kt` — `downloadFile`/`seriesIdOf` werden `override` (Logik existiert schon konkret); `pageRefsFromCount` entfällt zugunsten des Domain-Helpers.
- `source-opds/.../OpdsSource.kt` — `downloadFile` (Acquisition-Link), `seriesIdOf` (Feed-Hierarchie oder leer).

**Geändert (app — Entkopplung):**
- `EpubBytesLoader.kt`, `ReaderViewModel.kt`, alle 6 oben genannten ViewModels, die Cover-ladenden Screens, `MainActivity` (Registrierung), DI-Module.

**Entfernt am Ende:**
- `KomgaSourceProvider` als VM-Dependency (nur noch von `ActiveSource`/`SourceRegistration` genutzt), `AuthHeaders` (durch Fetcher ersetzt).

---

## Phase 0 — Refactor-Branch + Novel-Basis hereinmergen

### Task 0: Branch anlegen, Novel-Commits mergen, grün verifizieren

**Files:** keine (Git + Build).

- [ ] **Step 1: Rule-Änderungen committen** (sie liegen uncommittet auf `master`)

```bash
git add .claude/rules CLAUDE.md && git commit -m "docs(rules): source-agnostic-integration + big-picture-and-goals + seams Soll/Ist"
```

- [ ] **Step 2: Refactor-Branch von master**

```bash
git switch -c feat/source-agnostic-integration
```

- [ ] **Step 3: Aktuellen Novel-Stand hereinmergen**

```bash
git merge --no-ff feat/novel-reflow-reader -m "merge: novel-reflow-reader Basis (ReaderScaffold + EpubBytesLoader) fuer agnostik-Refactor"
```
Erwartung: Merge ohne Konflikte (Novel zweigte von master ab, master hat seither nur Doku-Änderungen). Bei Konflikt nur in `.claude/`/Doku: zugunsten beider auflösen.

- [ ] **Step 4: Build grün**

Run: `./gradlew :domain:test :data:test :source-komga:test :source-opds:test`
Erwartung: BUILD SUCCESSFUL (Novel-Basis + bestehende Tests grün).

- [ ] **Step 5: Ausgangs-Kopplung dokumentieren (Beweis-Baseline)**

Run: `grep -rln 'KomgaSourceProvider\|AuthHeaders' app/src/main/kotlin | sort`
Erwartung: die bekannte Liste (6 VMs + EpubBytesLoader + Cover-Screens). Festhalten — am Planende muss sie leer/auf `ActiveSource`+`SourceRegistration` reduziert sein.

---

## Phase 1 — Domain: Naht erweitern (TDD, pure)

### Task 1: `buildPageRefs` — quellen-neutraler Seiten-Ref-Builder

Ersetzt das Komga-spezifische `pageRefsFromCount`. `PageRef.url` wird leer gelassen — Bilder lädt künftig der Coil-`Fetcher` über `openPage`, nicht über die URL.

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/source/PageRefs.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/source/PageRefsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.domain.source
import kotlin.test.Test
import kotlin.test.assertEquals

class PageRefsTest {
    @Test fun `buildPageRefs erzeugt 1-basierte pageNumbers und 0-basierte indizes`() {
        val refs = buildPageRefs(bookRemoteId = "b1", pageCount = 3)
        assertEquals(3, refs.size)
        assertEquals(PageRef(index = 0, bookRemoteId = "b1", pageNumber = 1, url = ""), refs[0])
        assertEquals(PageRef(index = 2, bookRemoteId = "b1", pageNumber = 3, url = ""), refs[2])
    }
    @Test fun `buildPageRefs bei 0 Seiten ist leer`() {
        assertEquals(emptyList(), buildPageRefs("b1", 0))
    }
}
```

- [ ] **Step 2: Run, verify fail** — `./gradlew :domain:test --tests '*PageRefsTest*'` → FAIL (unresolved `buildPageRefs`).

- [ ] **Step 3: Implementieren**

```kotlin
package com.komgareader.domain.source

/** Baut deterministisch [PageRef]s aus einer Seitenzahl — ohne Netzabruf, quellen-neutral.
 *  `url` bleibt leer; Bytes liefert die Quelle über [BrowsableSource.openPage]. */
fun buildPageRefs(bookRemoteId: String, pageCount: Int): List<PageRef> =
    (1..pageCount).map { n -> PageRef(index = n - 1, bookRemoteId = bookRemoteId, pageNumber = n, url = "") }
```

- [ ] **Step 4: Run, verify pass** → PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(domain): buildPageRefs (quellen-neutral, ersetzt pageRefsFromCount)"`

### Task 2: `BrowsableSource` um `downloadFile` + `seriesIdOf` erweitern

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/source/MediaSource.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/source/BrowsableSourceContractTest.kt`

- [ ] **Step 1: Failing test** (ein Fake erfüllt den erweiterten Vertrag)

```kotlin
package com.komgareader.domain.source
import com.komgareader.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeBrowsable : BrowsableSource {
    override val id = 1L; override val name = "f"; override val kind = SourceKind.PLUGIN
    override suspend fun browse(page: Int, filter: SourceFilter) = PagedResult<Series>(emptyList(), false)
    override suspend fun search(query: String, page: Int) = PagedResult<Series>(emptyList(), false)
    override suspend fun books(seriesRemoteId: String) = emptyList<Book>()
    override suspend fun seriesDetail(seriesRemoteId: String): Series? = null
    override suspend fun pages(bookRemoteId: String) = buildPageRefs(bookRemoteId, 2)
    override suspend fun openPage(ref: PageRef) = byteArrayOf(ref.pageNumber.toByte())
    override suspend fun downloadFile(bookRemoteId: String) = "epub:$bookRemoteId".toByteArray()
    override suspend fun seriesIdOf(bookRemoteId: String) = "s-$bookRemoteId"
}

class BrowsableSourceContractTest {
    @Test fun `downloadFile und seriesIdOf erfuellbar ueber das interface`() = kotlinx.coroutines.test.runTest {
        val s: BrowsableSource = FakeBrowsable()
        assertEquals("epub:b1", String(s.downloadFile("b1")))
        assertEquals("s-b1", s.seriesIdOf("b1"))
    }
}
```

- [ ] **Step 2: Run, verify fail** → FAIL (Interface kennt die Methoden nicht).

- [ ] **Step 3: Interface erweitern** (in `MediaSource.kt`, in `interface BrowsableSource`)

```kotlin
    /** Rohe Bytes des kompletten Buchs (z. B. EPUB-Download / Reflow-Quelle). */
    suspend fun downloadFile(bookRemoteId: String): ByteArray

    /** Die Serien-Remote-ID, zu der ein Buch gehört (für kapitelübergreifende Ansichten). */
    suspend fun seriesIdOf(bookRemoteId: String): String
```

- [ ] **Step 4: Run, verify pass** → PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(domain): BrowsableSource.downloadFile + seriesIdOf (Naht A erweitert)"`

---

## Phase 2 — Quellen implementieren die neue Naht (TDD pro Quelle)

### Task 3: KomgaSource — `downloadFile`/`seriesIdOf` zu `override`, `pageRefsFromCount` raus

**Files:**
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaSource.kt`
- Modify: Aufrufer von `pageRefsFromCount` (siehe Phase 3).
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaSourceContractTest.kt` (MockWebServer-Muster wie vorhandene Komga-Tests)

- [ ] **Step 1: Failing test** — `downloadFile`/`seriesIdOf` über das Interface-Handle

```kotlin
// Arrange: MockWebServer liefert book-bytes + series-Zuordnung (Muster aus bestehenden KomgaSource-Tests übernehmen)
@Test fun `downloadFile laedt buch-bytes ueber interface-handle`() = runTest {
    val source: BrowsableSource = komgaSourcePointingAt(server)   // Helper wie in bestehenden Tests
    server.enqueue(MockResponse().setBody(Buffer().write("EPUBBYTES".toByteArray())))
    assertEquals("EPUBBYTES", String(source.downloadFile("book-1")))
}
```

- [ ] **Step 2: Run, verify fail** → FAIL (kein `override`, Typ `BrowsableSource` kennt's noch nicht via KomgaSource bis override gesetzt — bzw. Signatur-Mismatch).

- [ ] **Step 3: Implementieren** — bestehende Methoden als `override` markieren

In `KomgaSource.kt`: `suspend fun downloadFile(...)` → `override suspend fun downloadFile(bookRemoteId: String): ByteArray` (Signatur an Interface angleichen — bestehende Mehr-Parameter-Variante als privates Detail behalten, falls nötig). `suspend fun seriesIdOf(...)` → `override`. `fun pageRefsFromCount(...)` **löschen** (Aufrufer nutzen ab Phase 3 `buildPageRefs`).

- [ ] **Step 4: Run, verify pass** → `./gradlew :source-komga:test` PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(source-komga): downloadFile/seriesIdOf override, pageRefsFromCount entfernt"`

### Task 4: OpdsSource — `downloadFile`/`seriesIdOf` implementieren

**Files:**
- Modify: `source-opds/src/main/kotlin/com/komgareader/source/opds/OpdsSource.kt`
- Test: `source-opds/src/test/kotlin/com/komgareader/source/opds/OpdsSourceContractTest.kt`

- [ ] **Step 1: Failing test** — `downloadFile` über den OPDS-Acquisition-Link

```kotlin
@Test fun `downloadFile folgt dem acquisition-link des eintrags`() = runTest {
    val source: BrowsableSource = opdsSourcePointingAt(server)
    // Feed-Entry mit <link rel="http://opds-spec.org/acquisition" href="/dl/book-1.epub"/>
    server.enqueue(MockResponse().setBody(opdsEntryFeed))      // erst Entry/Link auflösen ...
    server.enqueue(MockResponse().setBody(Buffer().write("OPDSBYTES".toByteArray())))  // ... dann Bytes
    assertEquals("OPDSBYTES", String(source.downloadFile("book-1")))
}
@Test fun `seriesIdOf faellt auf bookId zurueck wenn der feed keine serie kennt`() = runTest {
    val source: BrowsableSource = opdsSourcePointingAt(server)
    assertEquals("book-1", source.seriesIdOf("book-1"))
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren** — `downloadFile` lädt über den Acquisition-Link des Eintrags (vorhandener OPDS-Parser liefert Links); `seriesIdOf` nutzt die Feed-Hierarchie, sonst `bookRemoteId` als Fallback (leerer/flacher Feed). `openPage` für OPDS: falls der Feed keine Einzelseiten liefert, über den heruntergeladenen Container — oder dokumentiert nicht unterstützt (dann ist OPDS reflow-/download-only; im Canary über NOVEL/Download-Pfad lesbar).

- [ ] **Step 4: Run, verify pass** → `./gradlew :source-opds:test` PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(source-opds): downloadFile (Acquisition) + seriesIdOf"`

---

## Phase 3 — Agnostische Quellen-Grenze in `app` (Registrierung + Resolver)

### Task 5: `SourceRegistration` — Quellen aus `ServerConfig` in `SourceManager` registrieren

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/SourceRegistration.kt`
- Modify: `domain/source/SourceManager.kt` (nichts — API reicht: `register`/`unregister`/`get`)
- Test: `app/src/test/kotlin/com/komgareader/app/data/SourceRegistrationTest.kt`

- [ ] **Step 1: Failing test** — bei Config-Wechsel wird genau eine Quelle registriert

```kotlin
@Test fun `config setzt registriert komga-quelle mit deterministischer id`() = runTest {
    val sm = SourceManager()
    val reg = SourceRegistration(sm, KomgaSourceProvider())
    val cfg = ServerConfig(name = "Heim", baseUrl = "http://h", apiKey = "k")
    val id = reg.activate(cfg)                        // registriert + liefert sourceId
    assertEquals(SourceId.of("Heim", SourceKind.KOMGA, "http://h"), id)
    assertTrue(sm.get(id) is BrowsableSource)
}
@Test fun `null-config deaktiviert die zuvor aktive quelle`() = runTest {
    val sm = SourceManager(); val reg = SourceRegistration(sm, KomgaSourceProvider())
    val id = reg.activate(ServerConfig("Heim", "http://h", apiKey = "k"))
    reg.activate(null)
    assertNull(sm.get(id))
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren**

```kotlin
package com.komgareader.app.data

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.source.SourceManager
import javax.inject.Inject
import javax.inject.Singleton

/** Registriert die aktive Quelle (heute: eine Komga-Verbindung) im [SourceManager].
 *  Der konkrete Quellen-Typ (KomgaSourceProvider) lebt NUR hier in der Wiring-Schicht. */
@Singleton
class SourceRegistration @Inject constructor(
    private val sources: SourceManager,
    private val komgaProvider: KomgaSourceProvider,
) {
    private var activeId: Long? = null

    /** Aktiviert die Quelle aus [config] (registriert sie); null deaktiviert die bisherige. Gibt die aktive sourceId zurück (null wenn keine). */
    fun activate(config: ServerConfig?): Long? {
        activeId?.let { sources.unregister(it) }
        if (config == null) { activeId = null; return null }
        val id = SourceId.of(config.name, SourceKind.KOMGA, config.baseUrl)
        val source = komgaProvider.from(config) ?: run { activeId = null; return null }
        sources.register(SourceHandle(id, source))   // SourceHandle: dünner Wrapper, der MediaSource.id = id erzwingt
        activeId = id
        return id
    }

    fun activeSourceId(): Long? = activeId
}
```
Hinweis: Falls `KomgaSource.id` nicht bereits `SourceId.of(...)` liefert, statt `SourceHandle` die `id` direkt in `KomgaSourceFactory.create(...)` durchreichen (sauberer). Den Weg wählen, der ohne Wrapper auskommt — Wrapper nur falls `id` sonst nicht setzbar.

- [ ] **Step 4: Run, verify pass** → PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(app): SourceRegistration verdrahtet SourceManager (konkreter Typ nur hier)"`

### Task 6: `ActiveSource` — agnostischer Resolver für ViewModels

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/ActiveSource.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/data/ActiveSourceTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
@Test fun `current liefert die aktive quelle als BrowsableSource`() = runTest {
    val sm = SourceManager()
    val servers = FakeServerRepository(ServerConfig("Heim", "http://h", apiKey = "k"))
    val reg = SourceRegistration(sm, KomgaSourceProvider())
    val active = ActiveSource(sm, servers, reg)
    val s = active.current()
    assertTrue(s is BrowsableSource)
}
@Test fun `current ist null ohne server`() = runTest {
    val active = ActiveSource(SourceManager(), FakeServerRepository(null), reg)
    assertNull(active.current())
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren**

```kotlin
package com.komgareader.app.data

import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Die einzige Art, wie ein ViewModel eine Quelle bekommt: agnostisch, als [BrowsableSource].
 *  Kein ViewModel kennt KomgaSourceProvider — siehe Regel source-agnostic-integration. */
@Singleton
class ActiveSource @Inject constructor(
    private val sources: SourceManager,
    private val servers: ServerRepository,
    private val registration: SourceRegistration,
) {
    /** Stellt sicher, dass die aktuelle Config registriert ist, und liefert die aktive Quelle (oder null). */
    suspend fun current(): BrowsableSource? {
        val config = servers.config.first()
        val id = registration.activate(config) ?: return null
        return sources.get(id) as? BrowsableSource
    }
}
```

- [ ] **Step 4: Run, verify pass** → PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(app): ActiveSource — agnostische Quellen-Grenze fuer ViewModels"`

---

## Phase 4 — Bild-Laden über die Naht (Coil-Fetcher statt URL+Auth)

### Task 7: Coil-`SourceImage`-Model + `Fetcher` über `openPage`

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/coil/SourceImage.kt`
- Create: `app/src/main/kotlin/com/komgareader/app/data/coil/SourcePageFetcher.kt`
- Modify: Coil-`ImageLoader`-Setup (DI/`MainActivity`) — `Fetcher.Factory` registrieren.
- Test: `app/src/test/kotlin/com/komgareader/app/data/coil/SourcePageFetcherTest.kt`

- [ ] **Step 1: Failing test** — Fetcher liefert die Bytes aus `openPage`

```kotlin
@Test fun `fetcher laedt seite ueber openPage der registrierten quelle`() = runTest {
    val sm = SourceManager()
    sm.register(fakeSourceReturning(pageBytes = "PAGE7".toByteArray(), forPage = 7, id = 42L))
    val fetcher = SourcePageFetcher(SourceImage(sourceId = 42L, bookRemoteId = "b1", pageNumber = 7), sm)
    val result = fetcher.fetch()
    assertEquals("PAGE7", result.readBytesAsString())   // SourceResult → Buffer prüfen
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren**

```kotlin
// SourceImage.kt
package com.komgareader.app.data.coil
/** Coil-Model für ein quellen-geladenes Bild (Seite/Cover). Keyer macht daraus den Cache-Key. */
data class SourceImage(val sourceId: Long, val bookRemoteId: String, val pageNumber: Int)
```
```kotlin
// SourcePageFetcher.kt — Coil 3 Fetcher
package com.komgareader.app.data.coil

import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.SourceManager
import okio.Buffer

class SourcePageFetcher(
    private val model: SourceImage,
    private val sources: SourceManager,
) : Fetcher {
    override suspend fun fetch(): coil3.fetch.FetchResult {
        val source = sources.get(model.sourceId) as? BrowsableSource
            ?: error("Quelle ${model.sourceId} nicht registriert")
        val bytes = source.openPage(PageRef(model.pageNumber - 1, model.bookRemoteId, model.pageNumber, url = ""))
        return SourceFetchResult(
            source = coil3.decode.ImageSource(Buffer().apply { write(bytes) }, fileSystem = okio.FileSystem.SYSTEM),
            mimeType = null,
            dataSource = coil3.decode.DataSource.NETWORK,
        )
    }
    class Factory(private val sources: SourceManager) : Fetcher.Factory<SourceImage> {
        override fun create(data: SourceImage, options: coil3.request.Options, imageLoader: coil3.ImageLoader) =
            SourcePageFetcher(data, sources)
    }
}
```
(API-Details von Coil 3 via Context7 verifizieren — `Fetcher`/`SourceFetchResult`/`ImageSource`-Signaturen.)

- [ ] **Step 4: Run, verify pass** → PASS.

- [ ] **Step 5: `Fetcher.Factory` im ImageLoader registrieren** (DI/`MainActivity`): `add(SourcePageFetcher.Factory(sourceManager))` zum `ImageLoader.Builder().components { … }`. Build grün.

- [ ] **Step 6: Commit** — `git commit -am "feat(app): Coil SourcePageFetcher laedt Bilder ueber openPage (Naht)"`

---

## Phase 5 — Reader entkoppeln (auf der Novel-Basis)

### Task 8: `EpubBytesLoader` → `SourceContentLoader` (agnostisch)

Generalisiert den von der Novel-Arbeit geschaffenen Loader: `KomgaSourceProvider` → `ActiveSource`, `source.downloadFile`/`pullProgress` über das Interface. **NovelReaderViewModel profitiert ohne eigene Änderung** (es injiziert nur den Loader).

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/EpubBytesLoader.kt` (Rename optional → `SourceContentLoader`)
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/SourceContentLoaderTest.kt`

- [ ] **Step 1: Failing test** — Loader nutzt `ActiveSource`, kein KomgaSourceProvider

```kotlin
@Test fun `load holt bytes ueber die aktive quelle wenn kein download da ist`() = runTest {
    val active = FakeActiveSource(sourceReturningDownload("EPUB!".toByteArray()))
    val loader = SourceContentLoader(active, FakeDownloadRepository(empty = true), downloadManager)
    assertEquals("EPUB!", String(loader.load("b1", forceStream = true)))
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren** — Konstruktor: `(active: ActiveSource, downloadRepository, downloadManager)`. `load`: lokaler Download bevorzugt; sonst `active.current()?.downloadFile(bookId) ?: error(...)`. `startProgressFraction`: `active.current()?.pullProgress(bookId)`. **Kein** `servers`/`sourceProvider` mehr.

- [ ] **Step 4: Run, verify pass** → PASS.

- [ ] **Step 5: Commit** — `git commit -am "refactor(reader): EpubBytesLoader -> agnostischer SourceContentLoader (ActiveSource)"`

### Task 9: `ReaderViewModel` entkoppeln (paged + webtoon + epub)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderContent.kt` (Streamed/Webtoon: `authHeaders`/`url` raus → `SourceImage`-Modelle)
- Modify: betroffene Screens (`PagedReaderScreen`, `WebtoonReaderScreen`) — Coil lädt `SourceImage` statt URL.
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderViewModelTest.kt` (Fake `ActiveSource`)

- [ ] **Step 1: Failing test** — VM ohne `KomgaSourceProvider`/`KomgaSource`

```kotlin
@Test fun `paged-laden nutzt active source und liefert SourceImage-seiten`() = runTest {
    val vm = readerVmWith(active = FakeActiveSource(sourceWithPages("b1", count = 5)), bookId = "b1", mode = PAGED)
    val content = vm.content.value as ReaderContent.Streamed
    assertEquals(5, content.pages.size)
    assertEquals(SourceImage(sourceId = anyId, "b1", 1), content.pages.first())   // kein url/authHeaders mehr
}
@Test fun `webtoon-strip baut refs ueber seriesIdOf + books + buildPageRefs`() = runTest { /* … */ }
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren**
  - Konstruktor: `sourceProvider: KomgaSourceProvider` → `active: ActiveSource`; `servers`/`AuthHeaders` raus.
  - `loadBook`: `val source = active.current() ?: error("Kein Server")`; EPUB-Pfad nutzt `SourceContentLoader`; paged: `source.pages(bookId)` → `ReaderContent.Streamed(pages = refs.map { SourceImage(source.id, bookId, it.pageNumber) }, initialPage)`.
  - `loadWebtoonStrip(source: BrowsableSource, …)`: `source.seriesIdOf(bookId)`, `source.books(seriesId)`, je Kapitel `buildPageRefs(remoteId, pageCount)` → `SourceImage`s. (`pageRefsFromCount` entfällt.)
  - `MupdfDocumentFactory()` als injizierte `DocumentFactory` aufnehmen (DIP; siehe Task 10) — hier mindestens nicht mehr direkt für den Stream-Pfad nötig.
  - `ReaderContent.Streamed`/`Webtoon`: `pages: List<SourceImage>`, `authHeaders` Feld entfernen.

- [ ] **Step 4: Run, verify pass** → `./gradlew :app:testDebugUnitTest --tests '*ReaderViewModelTest*'` PASS.

- [ ] **Step 5: Screens anpassen** — `AsyncImage(model = sourceImage)` statt `model = url + headers`. Build grün, Instrumented-Smoke (paged + webtoon öffnen, Seiten erscheinen).

- [ ] **Step 6: Commit** — `git commit -am "refactor(reader): ReaderViewModel ueber ActiveSource + SourceImage, AuthHeaders raus"`

### Task 10: `DocumentFactory` injizieren (DIP, Engine-Wahl)

**Files:**
- Modify: DI-Modul (`app/.../di/…`) — `@Provides DocumentFactory = MupdfDocumentFactory()`.
- Modify: `ReaderViewModel` (und ggf. `SourceContentLoader`-Konsumenten) — `MupdfDocumentFactory()` → injiziertes `DocumentFactory`.
- Test: vorhandene Reader-Tests bleiben grün (Fake `DocumentFactory` möglich).

- [ ] **Step 1: Failing test** — VM nutzt injizierte Factory (Fake liefert Fake-Document)
- [ ] **Step 2: Run, verify fail** → FAIL.
- [ ] **Step 3: Implementieren** — `@Provides fun documentFactory(): DocumentFactory = MupdfDocumentFactory()`; Konstruktor-Param ergänzen, `MupdfDocumentFactory()`-Direktaufrufe ersetzen.
- [ ] **Step 4: Run, verify pass** → PASS.
- [ ] **Step 5: Commit** — `git commit -am "refactor(render): DocumentFactory injiziert (DIP, Engine-Wahl vorbereitet)"`

---

## Phase 6 — Restliche ViewModels + Cover-Laden entkoppeln

### Task 11: 5 Browse-/Detail-ViewModels auf `ActiveSource` umstellen

Betrifft: `LibraryViewModel`, `GroupsViewModel`, `GroupBrowseViewModel`, `SeriesDetailViewModel`, `ColorFilterViewModel`.

**Files (je VM):** Modify VM + zugehöriger Test.

- [ ] **Step 1: Failing test (je VM, gleiche Form)** — VM-Konstruktor nimmt `ActiveSource`, lädt über das Interface; Fake mit OPDS-artiger Quelle liefert dieselben Ergebnisse.

```kotlin
@Test fun `library laedt serien ueber active source (quellen-agnostisch)`() = runTest {
    val vm = LibraryViewModel(active = FakeActiveSource(sourceWithSeries(3)), shelfRepository, …)
    assertEquals(3, (vm.state.value as Content).series.size)
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.
- [ ] **Step 3: Implementieren (je VM)** — `KomgaSourceProvider`/`servers.config→from(config)` → `active.current()` (typisiert `BrowsableSource`). `runCatching { … }.getOrNull()`-Fallbacks beibehalten.
- [ ] **Step 4: Run, verify pass** → `./gradlew :app:testDebugUnitTest` PASS.
- [ ] **Step 5: Commit (einer pro VM)** — `git commit -am "refactor(<feature>): ViewModel ueber ActiveSource statt KomgaSourceProvider"`

> Diese 5 sind dieselbe mechanische Änderung; je VM ein eigener Test + Commit (Confidence + reviewbar).

### Task 12: Cover-Laden über `SourceImage`-Fetcher (Browse-Screens)

Betrifft die Cover-ladenden Screens: `LibraryScreen`, `GroupsScreen`, `GroupBrowseRoute/Screen`, `SeriesDetailScreen`, `ColorFilterViewModel`-Vorschau.

**Files (je Screen):** Modify Screen.

- [ ] **Step 1: Cover-Fetcher-Pfad ergänzen** — Cover braucht u. U. eine eigene Capability. Pragmatisch: `SourceImage` mit `pageNumber = 0` = „Cover"; `SourcePageFetcher` ruft dann `source.seriesCover(bookRemoteId)`/`bookCover(...)`. Falls noch nicht auf der Naht: **Task 12a** zuvor — `BrowsableSource.coverBytes(remoteId, isSeriesCover): ByteArray` (Kochrezept A, KomgaSource: vorhandene Thumbnail-URL; OPDS: Thumbnail-Link). TDD wie Task 2/3/4.
- [ ] **Step 2: Screens umstellen** — `AsyncImage(model = SourceImage(activeSourceId, remoteId, 0))` statt `"${baseUrl}…/thumbnail"` + `AuthHeaders`. Je Screen Build + Screenshot-Smoke.
- [ ] **Step 3: Commit (einer pro Screen)** — `git commit -am "refactor(<screen>): Cover ueber SourceImage-Fetcher statt URL+AuthHeaders"`

### Task 13: `AuthHeaders` entfernen, Kopplungs-Beweis

**Files:** Delete `app/.../data/AuthHeaders.kt`; letzte Aufrufer entfernen.

- [ ] **Step 1: Datei + Reste löschen.** Run: `grep -rln 'AuthHeaders' app/src/main` → erwartet **leer**.
- [ ] **Step 2: VM-Kopplungs-Beweis.** Run: `grep -rln 'KomgaSourceProvider' app/src/main/kotlin` → erwartet **nur** `ActiveSource.kt`/`SourceRegistration.kt` (Wiring), **kein** ViewModel, **kein** `EpubBytesLoader`/`SourceContentLoader`.
- [ ] **Step 3: Build + alle Unit-Tests grün.** Run: `./gradlew testDebugUnitTest`.
- [ ] **Step 4: Commit** — `git commit -am "refactor(app): AuthHeaders entfernt — Bild-Laden vollstaendig ueber die Naht"`

---

## Phase 7 — OPDS-Canary (der Beweis, dass die Naht trägt)

### Task 14: OPDS als zweite registrierbare Quelle + Auswahl

**Files:**
- Modify: `SourceRegistration` — Quellenart aus Config ableiten (Komga vs OPDS); `OpdsSourceFactory` injizieren.
- Modify: `ServerConfig` — `kind: SourceKind` ergänzen (Default KOMGA, migrationsfrei da String-Setting; Server-Anlage-UI um Typ-Wahl erweitern).
- Test: `SourceRegistrationTest` — OPDS-Config registriert eine `OpdsSource`.

- [ ] **Step 1: Failing test**

```kotlin
@Test fun `opds-config registriert opds-quelle`() = runTest {
    val sm = SourceManager()
    val reg = SourceRegistration(sm, KomgaSourceProvider(), OpdsSourceFactory)
    val id = reg.activate(ServerConfig("Feed", "http://o/opds", kind = SourceKind.OPDS))
    assertEquals(SourceId.of("Feed", SourceKind.OPDS, "http://o/opds"), id)
    assertTrue(sm.get(id) is BrowsableSource)
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.
- [ ] **Step 3: Implementieren** — `SourceRegistration.activate`: `when (config.kind) { KOMGA -> komgaProvider.from(...); OPDS -> opdsFactory.create(...) }`. `ServerConfig.kind` + UI-Typwahl bei Server-Anlage.
- [ ] **Step 4: Run, verify pass** → PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(app): OPDS als registrierbare Quelle (SourceRegistration nach kind)"`

### Task 15: E2E — OPDS browsen + ein Buch lesen

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/OpdsCanaryInstrumentedTest.kt`
- Tooling: lokaler OPDS-Feed (Komga liefert selbst `/opds/v2` — gegen die lokale Test-Komga, [[local-test-komga]]) oder gemockter Feed-Server.

- [ ] **Step 1: Test** — OPDS-Server konfigurieren → Bibliothek zeigt Serien → Buch öffnen → Reader rendert Seite 1 (Download/NOVEL-Pfad). Kein Komga-spezifischer Code im Pfad.

```kotlin
@Test fun opds_browse_and_read_works_without_komga() { /* compose-rule: connect OPDS, open series, open book, assert page rendered */ }
```

- [ ] **Step 2: Run** — `./gradlew :app:connectedDebugAndroidTest --tests '*OpdsCanary*'` → PASS auf `eink_test`.
- [ ] **Step 3: Beweis festhalten** — Screenshot/Log: OPDS-Serie gelesen. **Das ist der Lackmustest aus `source-agnostic-integration.md`.**
- [ ] **Step 4: Commit** — `git commit -am "test(e2e): OPDS-Canary — browsen + lesen ohne Komga-Pfad"`

---

## Phase 8 — Doku nachziehen + Abschluss

### Task 16: Regeln/Doku auf neuen Ist-Stand (docs-match-code)

**Files:** `.claude/rules/source-agnostic-integration.md`, `architecture-seams.md`, `big-picture-and-goals.md`.

- [ ] **Step 1:** In `source-agnostic-integration.md` das „SourceManager noch nicht verdrahtet"-Caveat entfernen (jetzt verdrahtet), `ActiveSource`/`SourceRegistration`/`SourcePageFetcher` als Ist-Pattern eintragen.
- [ ] **Step 2:** In `architecture-seams.md` den Ist-Stand aktualisieren: App geht jetzt über `SourceManager`; Bild-Laden über `SourcePageFetcher`; OPDS end-to-end lesbar (Naht-Beweis).
- [ ] **Step 3:** `big-picture-and-goals.md`: Multi-Server-Ziel als „Naht bewiesen (OPDS)" markieren.
- [ ] **Step 4: Commit** — `git commit -am "docs: Naht-Ist-Stand nach Agnostik-Refactor (docs-match-code)"`

### Abschluss-Checks

- [ ] `./gradlew testDebugUnitTest` (alle Module) grün.
- [ ] `grep -rln 'KomgaSourceProvider' app/src/main/kotlin` → nur `ActiveSource`/`SourceRegistration`.
- [ ] `grep -rln 'AuthHeaders' app/src/main` → leer.
- [ ] Kein ViewModel-Konstruktor nennt `KomgaSource`/`*SourceProvider` (Red-Flag-Scan aus `source-agnostic-integration.md`).
- [ ] Lackmustest grün: OPDS-Canary-E2E (browsen + lesen) **und** Komga weiter funktionsfähig (Regressionstest).
- [ ] Novel-Reader funktioniert unverändert (NovelReaderViewModel über den jetzt-agnostischen `SourceContentLoader`).
- [ ] Koordinations-Hinweis an den Menschen: bereit für finalen Merge mit `feat/novel-reflow-reader`.

---

## Self-Review-Notiz

- **Webtoon multi-chapter:** hing an Komgas `pageRefsFromCount`; ersetzt durch `seriesIdOf`+`books`+`buildPageRefs` (alles Naht/Domain). Verifizieren, dass kein N-Request-Sturm entsteht (Refs aus pageCount, Bilder lazy via Fetcher).
- **OPDS-Seitenmodell:** Liefert ein OPDS-Feed keine Einzelseiten, ist OPDS download-/reflow-only — Canary dann über NOVEL/Download-Pfad lesen (nicht paged-streamed). Das ist akzeptabel und beweist die Naht trotzdem.
- **Coil 3 API:** `Fetcher`/`SourceFetchResult`/`ImageSource`-Signaturen vor Implementierung via Context7 gegen die im Projekt genutzte Coil-Version prüfen.
- **Offen gelassen (YAGNI):** echtes Multi-Source-gleichzeitig (mehrere `ServerConfig`s) — `ActiveSource` kapselt die Auswahl an einer Stelle, sodass das später ohne VM-Änderung kommt.
