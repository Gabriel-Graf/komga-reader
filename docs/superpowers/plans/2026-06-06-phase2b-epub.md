# Phase 2b — EPUB-Reader (3. Lesemodus, via MuPDF)

> REQUIRED SUB-SKILL: subagent-driven-development.

**Goal:** EPUB-Bücher lesen. EPUB ist reflowbar — Komga liefert dafür keine fertigen Seitenbilder. Daher: ganze EPUB-Datei von Komga laden, mit **MuPDF (`:render-core`)** zu Seiten rendern, im Reader anzeigen. Vervollständigt die 3 Lesemodi (Paged-Bild / Webtoon / EPUB-Reflow).

**Architecture:** `KomgaSource.downloadFile(bookRemoteId)` → ganze Datei-Bytes (`GET /api/v1/books/{id}/file`). `:app` bekommt `:render-core` als Dependency. Der Reader unterscheidet per `BookFormat`: `EPUB` → MuPDF-Pfad (Bytes laden, `MupdfDocumentFactory().open(bytes, ".epub")`, Seiten zu `Bitmap` rendern), sonst → bestehender Streaming-Bild-Pfad. MuPDF-Rendering läuft auf `Dispatchers.IO`, Bitmaps werden pro Seite gecacht; das `Document` wird beim VM-Cleanup geschlossen.

**Tech:** `:render-core` (`Document`/`RenderedPage`/`MupdfDocumentFactory`), Coroutines IO, Compose `HorizontalPager` + `Image(bitmap)`.

## Test-Komga läuft: EPUB-Serie „Novels" (id `0QKW4K6NW233B`) → Buch id `0QKW4K6NW233C` (`application/epub+zip`). Emulator-URL `http://10.0.2.2:25600/api/v1/`, Key `2243c9f4ecc5404992ddf8eba4bf6488`.

---

### Task 0: KomgaSource.downloadFile (ganze Datei streamen)

**Files:** modify `source-komga/.../KomgaApi.kt`, `KomgaSource.kt`; test `source-komga/.../KomgaDownloadTest.kt`.

- [ ] **Step 1 (TDD)** Test (MockWebServer): `downloadFile("B1")` ruft `GET books/B1/file` und liefert die Bytes.
```kotlin
@Test fun `downloadFile laedt die ganze Datei`() = runTest {
    server.enqueue(MockResponse().setBody("EPUBBYTES"))
    val bytes = source().downloadFile("B1")
    assertEquals("EPUBBYTES", bytes.decodeToString())
    assertTrue(server.takeRequest().path!!.endsWith("/books/B1/file"))
}
```
(Reuse the `source()`/server helpers from `KomgaSourceTest.kt` — put this test in a new file with its own MockWebServer setup, mirroring that pattern.) → RED.
- [ ] **Step 2** `KomgaApi`: `@GET("books/{id}/file") @Streaming suspend fun getFile(@Path("id") bookId: String): okhttp3.ResponseBody`. `KomgaSource`: `suspend fun downloadFile(bookRemoteId: String): ByteArray = api.getFile(bookRemoteId).bytes()`. → GREEN. Commit: `feat(komga): downloadFile (ganze Buchdatei)`.

---

### Task 1: :app → :render-core Dependency

**Files:** modify `app/build.gradle.kts`.

- [ ] `implementation(project(":render-core"))` ergänzen. `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `build(app): render-core-Dependency`.

---

### Task 2: Reader unterscheidet Format; EPUB lädt + rendert via MuPDF

**Files:** modify `app/.../ui/series/SeriesDetailScreen.kt` (onOpenBook bekommt format), `MainActivity.kt` (reader-Route + format-Arg), `ReaderViewModel.kt`; Create `app/.../ui/reader/EpubReaderScreen.kt`, `app/.../ui/reader/ReaderContent.kt`.

- [ ] **Step 1** Reader-Route erweitern: `reader/{bookId}/{pageCount}/{format}` (format = `book.format.name`, NavType.StringType). SeriesDetail `onOpenBook(remoteId, pageCount, format.name)`.
- [ ] **Step 2** `ReaderContent.kt`:
```kotlin
package com.komgareader.app.ui.reader
import com.komgareader.domain.render.Document
import com.komgareader.domain.source.PageRef

sealed interface ReaderContent {
    data object Loading : ReaderContent
    data class Streamed(val pages: List<PageRef>, val apiKey: String, val initialPage: Int) : ReaderContent
    data class Epub(val pageCount: Int, val initialPage: Int) : ReaderContent
    data class Error(val message: String) : ReaderContent
}
```
- [ ] **Step 3** `ReaderViewModel`: `format` aus SavedStateHandle. `content: StateFlow<ReaderContent>`. Beim Laden:
  - EPUB: `val bytes = source.downloadFile(bookId)` (auf IO); `document = MupdfDocumentFactory().open(bytes, ".epub")`; `ReaderContent.Epub(document.pageCount(), initialPage)`. Document in einem Feld halten; `onCleared { document?.close() }`.
  - sonst: wie bisher `Streamed(pages, apiKey, initialPage)`.
  - `suspend fun renderEpubPage(index: Int): Bitmap` (auf `Dispatchers.IO`, gecacht in `MutableMap<Int, Bitmap>`): `val rp = document!!.renderPage(index, zoom = 2f, rotation = 0); Bitmap.createBitmap(rp.pixels, rp.width, rp.height, Bitmap.Config.ARGB_8888)`.
  - Progress-Push wie gehabt (page = index+1, totalPages = pageCount).
- [ ] **Step 4** `EpubReaderScreen`: `HorizontalPager(pageCount)`; pro Seite `val bmp by produceState<Bitmap?>(null, page) { value = viewModel.renderEpubPage(page) }`; `if (bmp != null) Image(bmp!!.asImageBitmap(), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) else CircularProgressIndicator()`. Weißer Hintergrund (EPUB-Text). Tap-Zonen wie Paged (links/rechts blättern, Mitte Chrome), Chrome mit „i / n".
- [ ] **Step 5** `ReaderRoute`: bei `content is ReaderContent.Epub` → `EpubReaderScreen`, sonst `Streamed` → bestehender Paged/Webtoon-Switch. `./gradlew :app:assembleDebug` → SUCCESSFUL + Smoke PASS. Commit: `feat(reader): EPUB-Modus via MuPDF (Download + Render)`.

---

### Task 3: Instrumented-E2E (EPUB von Komga via MuPDF rendern)

**Files:** Create `app/src/androidTest/kotlin/com/komgareader/app/EpubReaderInstrumentedTest.kt`.

- [ ] **Step 1**
```kotlin
@Test fun rendert_epub_von_komga() = runTest {
    val source = KomgaSourceProvider().from(ServerConfig(
        name="T", baseUrl="http://10.0.2.2:25600/api/v1/", apiKey="2243c9f4ecc5404992ddf8eba4bf6488"))!!
    val bytes = source.downloadFile("0QKW4K6NW233C")     // Novels/mistborn.epub
    assertTrue("epub bytes", bytes.size > 500)
    val doc = MupdfDocumentFactory().open(bytes, ".epub")
    assertTrue("seiten > 0", doc.pageCount() > 0)
    val page = doc.renderPage(0, 2f, 0)
    val dark = page.pixels.count { val r=(it shr 16)and 0xff; val g=(it shr 8)and 0xff; val b=it and 0xff; (r+g+b)/3 < 80 }
    assertTrue("nicht leer: $dark", dark > 50)
    doc.close()
}
```
- [ ] **Step 2** `docker start komga-test`; `./gradlew :app:connectedDebugAndroidTest` → alle grün (Library + Reader + Epub). Optional Screenshot des EPUB-Readers nach `/tmp/epub.png` (best-effort). Commit: `test(app): Instrumented-E2E EPUB-Render von Komga`.

---

## Self-Review
- **Spec §6:** EPUB-Reflow-Modus via MuPDF auf geladene Datei → Tasks 0,2; render-core endlich in der App → Task 1.
- **Verschoben:** EPUB-Text-Settings (Aa: Schrift/Größe), Offline-Persistenz der geladenen Datei (Phase Download), Webtoon-für-lokale-Dateien.
- **Abnahme:** Build grün, Smoke PASS, `connectedDebugAndroidTest` rendert echtes EPUB von Komga via MuPDF.
