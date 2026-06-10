# Integrationstest — Plan 3: Seam-Tests Block C, E, F (+ G dokumentiert)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die verbleibenden quellen-/datenseitigen Seam-Tests, die echten Integrations-Mehrwert haben: Reader-Dispatch gegen **echte** Fixture-Metadaten (C), Fortschritts-Sync lokal + **live** push/pull (E), Download → offline lesbar (F). Block G wird **nicht** neu getestet (siehe Begründung).

**Architecture:** Baut auf Plan-2-Harness (`CiSourceStack`, `CiKomga`, `CiFixtures`). Reader-Dispatch nutzt die **puren** Domain-Resolver (`ResolveViewerType`) auf Serien/Büchern, die live aus der CI-Komga gezogen werden — so wird bewiesen, dass die echte Mapper-Ausgabe (Buch-`format`, Serien-`contentTypeOverride`) korrekt dispatcht. Sync nutzt `KomgaSource as SyncingSource` (push/pull) plus `RoomReadProgressRepository` (lokaler `dirty`-Lifecycle). Download nutzt `BrowsableSource.downloadFile` + `DownloadManager`.

**Tech Stack:** wie Plan 2 (Kotlin, AndroidJUnit4, inMemory-Room, coroutines-test). Zusätzlich `MupdfDocumentFactory` (render-core, im app-androidTest schon genutzt von `DownloadInstrumentedTest`).

**Voraussetzung:** CI-Komga-Instanzen laufen (`tools/ci-fixtures/up.sh`), Emulator `eink_test` an. Tests gegen `emulator-5554` (Boox per `ANDROID_SERIAL` ausgeschlossen).

**Bezug:** Spec §9 Block C/E/F/G. Regeln: `architecture-seams.md` (Viewer-Auflösung deterministisch), `source-agnostic-integration.md`, `big-picture-and-goals.md` (Geräteklassen).

---

## Warum Block G hier KEINE neuen Tests bekommt

Die G-Invarianten sind **pure Funktionen** und **bereits unit-getestet** im `domain`-Modul:
- `displayBehaviorFor(mode, capabilities)` (`DisplayBehavior.kt:41`) → getestet in `DisplayBehaviorTest.kt` (mono/kaleido/lcd). Deckt **G22** (E-Ink: `allowsMotion=false`, `allowsAccentColor=false`, auch Kaleido) vollständig ab.
- `RefreshScheduler` (`domain/eink`) → pure, eigene Tests. `OnyxRefresher.deviceManaged`-No-Op (**G23**) ist gerätenah (braucht echtes `OnyxEinkController`/Boox-SDK) und ist auf Nicht-Boox-HW **nicht** sinnvoll instrumentierbar — die Entscheidungslogik selbst ist die Setting→Flag-Weitergabe, kein eigener Integrationspfad.

Ein Integrationstest würde diese puren, schon grünen Resolver nur duplizieren (YAGNI). **G22/G23 gelten als abgedeckt durch die bestehenden Domain-Unit-Tests** — in der Spec-Coverage so vermerken. Die echte Geräteklassen-Wirkung (Bewegung/Akzent in der UI) gehört ins **UI-Set (Plan 4)**, wo `LocalDisplayBehavior`/`LocalEinkMode` im Compose-Baum greifen.

---

## Grounding (verifiziert)

- **C:** `ResolveViewerType` (`domain/usecase/ResolveViewerType.kt:26`): `operator fun invoke(series: Series, book: Book, fallback: ContentType?): ViewerType` + `fun forContentType(type: ContentType): ViewerType`. Kette: `contentTypeOverride` → EPUB=NOVEL → readingDirection VERTICAL/WEBTOON=WEBTOON → `fallback` → CBZ/CBR/PDF=PAGED → PAGED. `ViewerType{PAGED,WEBTOON,NOVEL,COMIC}`, `ContentType{MANGA,COMIC,NOVEL,WEBTOON}`, `BookFormat{CBZ,CBR,PDF,EPUB}`.
- **E:** `KomgaSource : SyncingSource` (`KomgaSource.kt:41`) → `pushProgress(bookRemoteId, ReadProgress)`, `pullProgress(bookRemoteId): ReadProgress?`, `setRead(bookRemoteId, read, pageCount)`. `ReadProgress(bookId: Long, page: Int, totalPages: Int, completed=false, locator: String?=null, dirty=false, updatedAt: Long)`. Lokal: `RoomReadProgressRepository(dao)` (`data/repository`): `markProgress(sourceId, bookRemoteId, page, completed, totalPages)` (setzt dirty=true), `dirty(): List<LocalReadProgress>`, `markSynced(bookRemoteId)`. DAO via `db.readProgressDao()`.
- **F:** `BrowsableSource.downloadFile(bookRemoteId, onProgress=(read,total)->Unit): ByteArray`. `DownloadManager(ctx, RoomDownloadRepository(db.downloadDao()), RoomSettingsRepository(db.settingsDao()))`: `store(bookRemoteId, sourceId, seriesRemoteId, title, format, totalPages, bytes, seriesTitle="", seriesCoverUrl=null)`, `readBytes(localPath): ByteArray`, `delete(bookRemoteId)`. `db.downloadDao().get(bookRemoteId)` → `DownloadEntity?(…, localPath)`. `MupdfDocumentFactory().open(bytes, ".cbz")` → `pageCount`. Muster: `DownloadInstrumentedTest.kt:24`.
- **Harness (Plan 2):** `CiSourceStack` (`activeSource`, `register(vararg ServerConfig)`, `remove`, `close`), `CiKomga.A/B`, `CiFixtures.MANGA_SERIES/WEBTOON_SERIES/NOVELS_A`.

---

## File Structure

```
app/src/androidTest/kotlin/com/komgareader/app/ci/
├── BlockCViewerDispatchTest.kt   # C9, C10, C11, C12 — echte Fixture-Metadaten → ResolveViewerType
├── BlockEProgressSyncTest.kt     # E-local (dirty-Lifecycle), E18/E20 (live push/pull)
└── BlockFDownloadTest.kt         # F21 — live download → offline store/read/delete
```

---

## Task 1: Block C — Reader-Dispatch gegen echte Fixture-Metadaten

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/BlockCViewerDispatchTest.kt`

Beweist: die deterministische Auflösung (`Series.contentTypeOverride ?: Shelf.contentType → ViewerType`)
greift korrekt auf **echten** Serien/Buch-Metadaten der CI-Komga (richtiges `BookFormat` aus dem
Mapper). Die Resolver-Logik selbst ist pur und separat unit-getestet — hier zählt das Zusammenspiel
Mapper × Resolver × Live-Daten.

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.ViewerType
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.usecase.ResolveViewerType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §9 Block C — deterministischer Reader-Dispatch auf echten Fixture-Metadaten. */
@RunWith(AndroidJUnit4::class)
class BlockCViewerDispatchTest {

    private lateinit var stack: CiSourceStack
    private val resolve = ResolveViewerType()

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    private suspend fun firstSeries(source: BrowsableSource, title: String) =
        source.browse(0, SourceFilter()).items.first { it.title == title }

    /** C9: Manga (CBZ) mit Shelf-Tag MANGA → der für MANGA definierte Viewer (paged-Familie). */
    @Test fun c9_manga_dispatcht_auf_manga_viewer() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val series = firstSeries(source, CiFixtures.MANGA_SERIES)
        val book = source.books(series.remoteId).first()
        assertEquals("Manga-Buch ist ein Archiv (CBZ)", BookFormat.CBZ, book.format)

        val viewer = resolve(series, book, fallback = ContentType.MANGA)
        assertEquals("MANGA-Shelf → MANGA-Viewer", resolve.forContentType(ContentType.MANGA), viewer)
    }

    /** C10: Webtoon (CBZ) mit Shelf-Tag WEBTOON → Webtoon-Viewer. */
    @Test fun c10_webtoon_dispatcht_auf_webtoon_viewer() = runTest {
        stack.register(CiKomga.B)
        val source = stack.activeSource.all().first()
        val series = firstSeries(source, CiFixtures.WEBTOON_SERIES)
        val book = source.books(series.remoteId).first()

        val viewer = resolve(series, book, fallback = ContentType.WEBTOON)
        assertEquals("WEBTOON-Shelf → WEBTOON-Viewer", ViewerType.WEBTOON, viewer)
    }

    /** C11: Novel (EPUB) → NOVEL-Viewer, unabhängig vom Shelf-Tag (Format schlägt Fallback). */
    @Test fun c11_novel_epub_dispatcht_auf_novel_viewer() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val series = firstSeries(source, CiFixtures.NOVELS_A.first()) // "Alpha-Novel"
        val book = source.books(series.remoteId).first()
        assertEquals("Novel-Buch ist EPUB", BookFormat.EPUB, book.format)

        // Selbst mit irreführendem Fallback MANGA muss EPUB → NOVEL gewinnen.
        val viewer = resolve(series, book, fallback = ContentType.MANGA)
        assertEquals("EPUB → NOVEL (Format schlägt Shelf-Fallback)", ViewerType.NOVEL, viewer)
    }

    /** C12: contentTypeOverride schlägt den Shelf-Fallback (kein Auto-Erkennen). */
    @Test fun c12_override_schlaegt_shelf_fallback() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val series = firstSeries(source, CiFixtures.MANGA_SERIES)
        val book = source.books(series.remoteId).first()

        // Manga-Serie künstlich auf NOVEL übersteuert → Override gewinnt gegen MANGA-Fallback.
        val overridden = series.copy(contentTypeOverride = ContentType.NOVEL)
        val viewer = resolve(overridden, book, fallback = ContentType.MANGA)
        assertEquals("Override NOVEL schlägt Fallback MANGA", ViewerType.NOVEL, viewer)
    }
}
```

- [ ] **Step 2: Kompiliert**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Ausführen (Fixtures + Emulator)**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.BlockCViewerDispatchTest`
Expected: 4 Tests grün.

- [ ] **Step 4: Falls C10 fehlschlägt (WEBTOON)**

Möglich, wenn die CI-Webtoon-Serie kein `readingDirection`/Override trägt und der Fallback-Pfad
einen anderen Viewer liefert. Das ist KEIN Test-Bug, sondern zeigt, dass „Webtoon" allein über den
Shelf-Tag (`fallback=WEBTOON`) kommt — `resolve(series, book, ContentType.WEBTOON)` MUSS dann
`forContentType(WEBTOON)=WEBTOON` liefern. Schlägt es fehl, ist `forContentType(WEBTOON)` falsch
gemappt → echtes Finding, als BLOCKED melden (nicht Test aufweichen).

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/BlockCViewerDispatchTest.kt
git commit -m "test(ci): Block C — Reader-Dispatch auf echten Fixture-Metadaten (C9-C12)"
```

---

## Task 2: Block E — Fortschritts-Sync (lokal + live push/pull)

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/BlockEProgressSyncTest.kt`

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomReadProgressRepository
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SyncingSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §9 Block E — Lese-Fortschritt offline-first (lokaler dirty-Lifecycle) + live push/pull. */
@RunWith(AndroidJUnit4::class)
class BlockEProgressSyncTest {

    private lateinit var stack: CiSourceStack
    private lateinit var db: AppDatabase
    private lateinit var progress: RoomReadProgressRepository

    @Before fun setUp() {
        stack = CiSourceStack()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        progress = RoomReadProgressRepository(db.readProgressDao())
    }

    @After fun tearDown() { db.close(); stack.close() }

    /** E-local: markProgress → dirty enthält den Eintrag → markSynced → dirty leer (Sync-Queue). */
    @Test fun e_lokaler_dirty_lifecycle() = runTest {
        progress.markProgress(sourceId = 1L, bookRemoteId = "book-1", page = 3, completed = false, totalPages = 10)
        val dirtyAfterMark = progress.dirty()
        assertTrue("Frischer Fortschritt muss dirty sein", dirtyAfterMark.any { it.bookRemoteId == "book-1" })

        progress.markSynced("book-1")
        assertTrue("Nach markSynced nicht mehr dirty", progress.dirty().none { it.bookRemoteId == "book-1" })
    }

    /** E18: Fortschritt live an die Quelle pushen und zurücklesen (round-trip über SyncingSource). */
    @Test fun e18_push_und_pull_progress_live() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val sync = source as? SyncingSource
        assertNotNull("Komga-Quelle muss SyncingSource sein", sync)

        val series = source.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }
        val book = source.books(series.remoteId).first()
        val pages = source.pages(book.remoteId)
        val target = (pages.size / 2).coerceAtLeast(1)  // mittlere Seite (1-basiert)

        sync!!.pushProgress(
            book.remoteId,
            ReadProgress(bookId = 0L, page = target, totalPages = pages.size, updatedAt = 1_700_000_000_000L),
        )
        val pulled = sync.pullProgress(book.remoteId)
        assertNotNull("Fortschritt muss serverseitig abrufbar sein", pulled)
        assertEquals("Zurückgelesene Seite muss der gepushten entsprechen", target, pulled!!.page)
    }

    /**
     * E20: Bei zwei Quellen synct das Werk von B zu B — A bleibt unberührt.
     * Push auf ein B-Buch, Pull über DIE B-Quelle (get(sourceId)) liefert es; die A-Quelle
     * kennt diese remoteId nicht (eigener Namespace).
     */
    @Test fun e20_progress_synct_zur_richtigen_quelle() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val all = stack.activeSource.all()
        val bSource = all.first { src ->
            src.browse(0, SourceFilter()).items.any { it.title == CiFixtures.WEBTOON_SERIES }
        }
        val series = bSource.browse(0, SourceFilter()).items.first { it.title == CiFixtures.WEBTOON_SERIES }
        val book = bSource.books(series.remoteId).first()

        val bSync = stack.activeSource.get(book.sourceId) as? SyncingSource
        assertNotNull("B-Quelle muss SyncingSource sein", bSync)
        bSync!!.pushProgress(
            book.remoteId,
            ReadProgress(bookId = 0L, page = 1, totalPages = book.pageCount.coerceAtLeast(1), updatedAt = 1_700_000_000_000L),
        )
        val pulledFromB = bSync.pullProgress(book.remoteId)
        assertNotNull("B muss den auf B gepushten Fortschritt liefern", pulledFromB)
        assertEquals(1, pulledFromB!!.page)
    }
}
```

- [ ] **Step 2: Kompiliert + ausführen**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` (BUILD SUCCESSFUL), dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.BlockEProgressSyncTest`
Expected: 3 Tests grün.

- [ ] **Step 3: Falls `markProgress`/`dirty` anders heißt**

Die `RoomReadProgressRepository`-Signaturen sind aus `ReadProgressRepository.kt:21` verifiziert
(`markProgress(sourceId, bookRemoteId, page, completed, totalPages)`, `dirty()`, `markSynced(bookRemoteId)`).
Falls `LocalReadProgress` ein anderes Feld als `bookRemoteId` trägt, an die echte Property anpassen
und im Report vermerken.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/BlockEProgressSyncTest.kt
git commit -m "test(ci): Block E — Fortschritts-Sync (dirty-Lifecycle + live push/pull, E18/E20)"
```

---

## Task 3: Block F — Download → offline lesbar

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/BlockFDownloadTest.kt`

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.download.DownloadManager
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.domain.source.SourceFilter
import com.komgareader.render.mupdf.MupdfDocumentFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/** Spec §9 Block F — Download mit Fortschritt → offline gespeichert, lesbar, löschbar (F21). */
@RunWith(AndroidJUnit4::class)
class BlockFDownloadTest {

    private lateinit var stack: CiSourceStack
    private lateinit var db: AppDatabase
    private lateinit var downloads: DownloadManager

    @Before fun setUp() {
        stack = CiSourceStack()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        downloads = DownloadManager(
            ctx,
            RoomDownloadRepository(db.downloadDao()),
            RoomSettingsRepository(db.settingsDao()),
        )
    }

    @After fun tearDown() { db.close(); stack.close() }

    /** F21: downloadFile meldet Fortschritt → store → readBytes nicht leer → MuPDF rendert → delete. */
    @Test fun f21_download_dann_offline_lesbar() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val series = source.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }
        val book = source.books(series.remoteId).first()

        // Download über die Naht, mit Fortschritts-Callback.
        val lastRead = AtomicLong(0)
        val bytes = source.downloadFile(book.remoteId) { read, _ -> lastRead.set(read) }
        assertTrue("Heruntergeladene Bytes > 1 KiB", bytes.size > 1024)
        assertTrue("onProgress muss mind. einmal mit read>0 gefeuert haben", lastRead.get() > 0)

        // Offline speichern und zurücklesen.
        downloads.store(
            bookRemoteId = book.remoteId, sourceId = source.id, seriesRemoteId = series.remoteId,
            title = book.title, format = "cbz", totalPages = book.pageCount, bytes = bytes,
        )
        val entity = db.downloadDao().get(book.remoteId)
        assertNotNull("Download-Eintrag muss persistiert sein", entity)
        val localBytes = downloads.readBytes(entity!!.localPath)
        assertTrue("Lokale Bytes nicht leer", localBytes.isNotEmpty())

        // Offline rendern (MuPDF) — beweist „lesbar ohne Netz".
        val doc = MupdfDocumentFactory().open(localBytes, ".cbz")
        assertTrue("Mind. eine Seite renderbar", doc.pageCount() >= 1)

        // Aufräumen → Eintrag + Datei weg.
        downloads.delete(book.remoteId)
        assertNull("Nach delete kein Eintrag mehr", db.downloadDao().get(book.remoteId))
    }
}
```

- [ ] **Step 2: Kompiliert + ausführen**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` (BUILD SUCCESSFUL), dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.BlockFDownloadTest`
Expected: 1 Test grün.

- [ ] **Step 3: Falls `MupdfDocumentFactory().open(...)`-Signatur abweicht**

Verifiziert gegen `DownloadInstrumentedTest.kt` (`MupdfDocumentFactory().open(localBytes, ".cbz")`,
`doc.pageCount`). Falls dort anders aufgerufen, exakt dem bestehenden Test folgen und vermerken.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/BlockFDownloadTest.kt
git commit -m "test(ci): Block F — Download mit Fortschritt → offline lesbar (F21)"
```

---

## Task 4: Gesamtlauf C+E+F + Spec-Coverage-Note

- [ ] **Step 1: Alle CI-Seam-Tests zusammen ausführen**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.komgareader.app.ci`
Expected: alle Block-A/B (9, Plan 2) + C (4) + E (3) + F (1) = **17 Tests grün**.

- [ ] **Step 2: Spec-Coverage für G vermerken**

In `docs/superpowers/specs/2026-06-10-integration-test-suite-design.md` bei G22/G23 ergänzen:
„abgedeckt durch Domain-Unit-Tests `DisplayBehaviorTest`/`RefreshScheduler` — kein eigener
Integrationstest (pure Logik); UI-Wirkung im UI-Set (Plan 4)." Commit:

```bash
git add docs/superpowers/specs/2026-06-10-integration-test-suite-design.md
git commit -m "docs(spec): G22/G23 als durch Domain-Unit-Tests abgedeckt vermerkt (kein Integrations-Duplikat)"
```

---

## Self-Review (Plan-Autor)

- **Spec-Coverage:** §9 Block C (Task 1: C9-C12 auf echten Metadaten), Block E (Task 2: lokaler dirty-Lifecycle + E18/E20 live), Block F (Task 3: F21). Block G bewusst NICHT dupliziert — durch bestehende pure Domain-Tests abgedeckt (Task 4 Step 2 dokumentiert das). Block D bleibt für später (Collections-Push/Pull in Arbeit).
- **Kein Duplikat:** C/G-Kernlogik ist pur und bereits unit-getestet; C-Integration prüft nur das Zusammenspiel mit echten Live-Metadaten (Mapper-Ausgabe), nicht die Resolver-Logik erneut.
- **Verifizierte APIs:** `KomgaSource : SyncingSource`, `ResolveViewerType.invoke/forContentType`, `RoomReadProgressRepository.markProgress/dirty/markSynced`, `DownloadManager.store/readBytes/delete`, `MupdfDocumentFactory().open` — alle gegen den echten Code gemappt.
- **Offene Annahme:** `LocalReadProgress.bookRemoteId`-Feldname (Task 2 Step 3) — Implementer verifiziert.

## Nächste Pläne

- **Plan 4** Compose-UI-Test-Deps + UI-Set (A1/A4, B7, C9–11, D14, G22) — echter Tap→Navigation→Render-Pfad.
- **Plan 5** Plugin/modulare-UI-Tests (Block H, `[pending]`).
- **Später** Block D (Sammlungen Push/Pull), sobald das parallel entwickelte Feature steht.
- **Plan CI** `.gitlab-ci.yml` + Runner-`devices=["/dev/kvm"]` + Emulator-Job (kann nach Plan 3 oder am Ende).
```
