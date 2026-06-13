# Phase 2c — Download / Offline

> REQUIRED SUB-SKILL: subagent-driven-development.

**Goal:** Ein Buch von Komga herunterladen (ganze Datei → lokaler App-Speicher), Status-Badge REMOTE→DOWNLOADING→LOCAL, und heruntergeladene Bücher **offline** lesen — gerendert via MuPDF (`:render-core`) direkt aus der lokalen Datei (cbz/cbr/pdf/epub).

**Architecture:** `DownloadEntity` + `DownloadDao` in `:data` (Room) merken `bookRemoteId → lokaler Pfad + Format + Titel`. Ein `DownloadManager` (in `:data`, Hilt) lädt via `KomgaSource.downloadFile` und schreibt nach `context.filesDir/downloads/{bookId}.{ext}`. Der Reader prüft beim Öffnen: existiert ein Download → lokale Datei-Bytes → `MupdfDocumentFactory().open(bytes, ".ext")` (MuPDF rendert alle Formate, kein Netz nötig); sonst bestehender Streaming-/EPUB-Pfad.

**Tech:** Room · Hilt · `:render-core` · `:source-komga` · Coroutines IO.

## Test-Komga: Berserk vol01 (id `0QKVPRDV42BFA`, cbz, 4 Seiten) @ `http://10.0.2.2:25600/api/v1/`, Key `<KOMGA_API_KEY>`.

---

### Task 0: DownloadEntity + DAO + DB-Migration

**Files:** `data/.../db/Entities.kt` (DownloadEntity ergänzen), neue `DownloadDao.kt`, `AppDatabase.kt` (Version 2 + Dao).

- [ ] `DownloadEntity(@PrimaryKey bookRemoteId: String, sourceId: Long, seriesRemoteId: String, title: String, format: String, localPath: String, totalPages: Int)`.
- [ ] `DownloadDao`: `@Query("SELECT * FROM downloads") fun observeAll(): Flow<List<DownloadEntity>>`, `@Query("SELECT * FROM downloads WHERE bookRemoteId=:id") suspend fun get(id:String): DownloadEntity?`, `@Insert(onConflict=REPLACE) suspend fun put(e)`, `@Query("DELETE FROM downloads WHERE bookRemoteId=:id") suspend fun delete(id:String)`.
- [ ] `AppDatabase`: `entities` um `DownloadEntity` ergänzen, `version = 2`, `abstract fun downloadDao(): DownloadDao`. In der Hilt-DB-Provider `Room.databaseBuilder(...).fallbackToDestructiveMigration().build()` (kein released Schema → destruktiv ok).
- [ ] `./gradlew :data:assembleDebug` → SUCCESSFUL. Commit: `feat(data): DownloadEntity + DAO (DB v2)`.

---

### Task 1: Domain — DownloadRepository

**Files:** `domain/.../repository/DownloadRepository.kt`; `data/.../repository/RoomDownloadRepository.kt`; `data/.../di/DataModule.kt` (Provider).

- [ ] Domain:
```kotlin
package com.komgareader.domain.repository
import kotlinx.coroutines.flow.Flow

data class DownloadedBook(val bookRemoteId: String, val seriesRemoteId: String, val title: String,
    val format: String, val localPath: String, val totalPages: Int)

interface DownloadRepository {
    val downloads: Flow<List<DownloadedBook>>
    suspend fun get(bookRemoteId: String): DownloadedBook?
    suspend fun put(book: DownloadedBook)
    suspend fun remove(bookRemoteId: String)
}
```
- [ ] `RoomDownloadRepository(dao)` mapped Entity↔DownloadedBook. Hilt-Provider in DataModule. `./gradlew :data:assembleDebug` → SUCCESSFUL. Commit: `feat(data): DownloadRepository (Room)`.

---

### Task 2: DownloadManager (lädt + speichert Datei)

**Files:** `data/.../download/DownloadManager.kt`.

- [ ] ```kotlin
@Singleton class DownloadManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val downloads: DownloadRepository,
) {
    /** Lädt Bytes (vom Aufrufer geliefert, da KomgaSource im :app-Layer gebaut wird) und speichert lokal. */
    suspend fun store(bookRemoteId: String, seriesRemoteId: String, title: String,
                      format: String, totalPages: Int, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "downloads").apply { mkdirs() }
        val ext = format.lowercase()
        val file = File(dir, "$bookRemoteId.$ext")
        file.writeBytes(bytes)
        downloads.put(DownloadedBook(bookRemoteId, seriesRemoteId, title, format, file.absolutePath, totalPages))
    }
    suspend fun delete(bookRemoteId: String) = withContext(Dispatchers.IO) {
        downloads.get(bookRemoteId)?.let { File(it.localPath).delete() }
        downloads.remove(bookRemoteId)
    }
    fun bytesOf(d: DownloadedBook): ByteArray = File(d.localPath).readBytes()
}
```
(Imports: `android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`, `java.io.File`, coroutines.) `./gradlew :data:assembleDebug` → SUCCESSFUL. Commit: `feat(data): DownloadManager (Datei speichern/löschen)`.

---

### Task 3: SeriesDetail — Download-Button + Status; Reader liest lokal

**Files:** `app/.../ui/series/SeriesDetailViewModel.kt`/`Screen.kt`, `app/.../ui/reader/ReaderViewModel.kt`.

- [ ] **SeriesDetailViewModel:** injiziere `DownloadManager` + `DownloadRepository` + `KomgaSourceProvider` + `ServerRepository`. Exponiere pro Buch den Download-Status (`downloads`-Flow → Set der bookRemoteIds). `fun download(book: Book)`: `viewModelScope.launch`: source = provider.from(config); `val bytes = source.downloadFile(book.remoteId)`; `manager.store(book.remoteId, seriesId, book.title, book.format.name, book.pageCount, bytes)`. `fun removeDownload(id)`.
- [ ] **SeriesDetailScreen:** pro Buchzeile ein Icon: nicht geladen → `Icons.Filled.CloudDownload` (Tap = download), lädt → `CircularProgressIndicator`, lokal → `Icons.Filled.CheckCircle` (Tap = löschen). 
- [ ] **ReaderViewModel:** beim Laden zuerst `downloadRepo.get(bookId)` prüfen. Wenn vorhanden → `val bytes = downloadManager.bytesOf(it)`; `document = MupdfDocumentFactory().open(bytes, ".\${it.format.lowercase()}")`; `ReaderContent.Epub(...)`-artiger **lokaler** Render-Pfad (benenne ggf. `ReaderContent.Local` ⇒ identisch zum Epub-Bitmap-Pfad, nutzt `renderEpubPage`/`renderLocalPage`). Kein Netz. Sonst bestehende Logik (EPUB-Download / Streaming).
  - Refactor-Hinweis: Der MuPDF-Bitmap-Render-Pfad (aus 2b) wird hier wiederverwendet — generalisiere `ReaderContent.Epub` zu „MuPDF-gerendert aus Document" (z.B. `ReaderContent.Rendered(pageCount, initialPage)`), genutzt von EPUB-Streaming UND lokalem Download. `EpubReaderScreen` bleibt der Renderer.
- [ ] `./gradlew :app:assembleDebug` → SUCCESSFUL + Smoke PASS. Commit: `feat(app): Download-Button + Offline-Lesen via MuPDF`.

---

### Task 4: Instrumented-E2E (Download + Offline-Render)

**Files:** `app/src/androidTest/.../DownloadInstrumentedTest.kt`.

- [ ] ```kotlin
@Test fun download_und_offline_render() = runTest {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val source = KomgaSourceProvider().from(ServerConfig("T","http://10.0.2.2:25600/api/v1/","<KOMGA_API_KEY>"))!!
    val bytes = source.downloadFile("0QKVPRDV42BFA")     // Berserk vol01 cbz
    val dir = File(ctx.filesDir, "downloads-test").apply { mkdirs() }
    val f = File(dir, "b.cbz"); f.writeBytes(bytes)
    // Offline lesen aus der lokalen Datei (kein Netz):
    val doc = MupdfDocumentFactory().open(f.readBytes(), ".cbz")
    assertTrue(doc.pageCount() >= 4)
    val page = doc.renderPage(0, 2f, 0)
    val dark = page.pixels.count { val r=(it shr 16)and 0xff; val g=(it shr 8)and 0xff; val b=it and 0xff; (r+g+b)/3<80 }
    assertTrue("nicht leer: $dark", dark > 100)
    doc.close(); f.delete()
}
```
- [ ] `docker start komga-test`; `./gradlew :app:connectedDebugAndroidTest` → alle grün. Commit: `test(app): Instrumented-E2E Download + Offline-MuPDF-Render`.

---

## Self-Review
- **Spec §7 Download/Offline:** Datei lokal speichern + offline via MuPDF lesen → Tasks 2,3,4; Badge-Status → Task 3.
- **Verschoben:** Hintergrund-Download-Queue/Fortschritt (jetzt einfacher suspend-Download), Teil-Download/Streaming-Range, automatische Lib-Spiegelung. Cbz-Offline rendert via MuPDF (statt entpackte Bilder) — einheitlich, ausreichend.
- **Abnahme:** Build grün, Smoke PASS, `connectedDebugAndroidTest` lädt echte Datei + rendert offline via MuPDF.
