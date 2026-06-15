# External Book File Handler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register the app as a handler for `.epub/.cbz/.cbr/.pdf`, open an externally-tapped file ephemerally in the existing reader, and offer to import it into the local (= download) folder; behaviour is rememberable and editable in Settings → Downloads.

**Architecture:** A `VIEW` intent on `MainActivity` yields a `content://` URI. We detect the format, insert a **transient** `DownloadedBook` under a reserved `SourceId.EXTERNAL` (the reader already reads `content://` rows via the download table), and navigate to the existing reader route — no reader rewrite. Import copies the bytes into the local(=download) SAF folder so LocalSource turns it into a normal local work. Transient rows are purged on app start.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Android Storage Access Framework (`DocumentFile`), navigation-compose.

**Spec:** `docs/superpowers/specs/2026-06-15-external-book-file-handler-design.md`

**Verification note:** CBZ/PDF ephemeral open + import + the DAO purge run on the x86_64 emulator. EPUB uses crengine (arm64-only `.so`) → EPUB open is **device-gated on a real Boox**, as is confirming our app actually appears as an "open with" handler in the Boox file manager (screenshot).

---

## File Structure

**Create:**
- `domain/.../model/ExternalOpenBehavior.kt` — `enum { ASK, IMPORT, READ_ONLY }`
- `domain/.../usecase/DetectBookFormat.kt` — pure `detectBookFormat(mime, fileName): BookFormat?`
- `domain/.../usecase/DetectBookFormatTest.kt` — unit tests (test dir)
- `app/.../data/ExternalBookOpener.kt` — `@Singleton`: transient open, import, purge
- test files (see tasks)

**Modify:**
- `source-api/.../source/SourceId.kt` — add `EXTERNAL = 1L`
- `domain/.../repository/SettingsRepository.kt` + `data/.../repository/RoomSettingsRepository.kt` — `externalOpenBehavior`
- `data/.../db/DownloadDao.kt` — `deleteBySourceId`
- `domain/.../repository/DownloadRepository.kt` + `data/.../repository/RoomDownloadRepository.kt` — `removeBySourceId`
- `app/.../data/SyncCoordinator.kt` — purge transient rows on app start
- `app/src/main/AndroidManifest.xml` — `VIEW` intent-filter
- `app/.../MainActivity.kt` — read VIEW intent, prompt/route, import picker
- `app/.../ui/settings/SettingsViewModel.kt` — `externalOpenBehavior` state + setter
- `app/.../ui/settings/SettingsContent.kt` — download picker sets both folders; behaviour row
- `app/.../i18n/Strings.kt` — new keys (4 impls)

---

## Task 1: Reserved SourceId.EXTERNAL

**Files:**
- Modify: `source-api/src/main/kotlin/com/komgareader/domain/source/SourceId.kt`

- [ ] **Step 1: Add the constant**

In `object SourceId`, after `const val LOCAL: Long = 0L`:
```kotlin
    /**
     * Transient source for an externally-opened file (VIEW intent). Distinct from
     * [LOCAL] (0) so `LocalDownloadSync` — which only reconciles sourceId == LOCAL —
     * never touches these rows. `of()` results are always >= 0 via `and Long.MAX_VALUE`,
     * so the small constant 1 cannot collide with a generated id.
     */
    const val EXTERNAL: Long = 1L
```

- [ ] **Step 2: Build**

Run: `./gradlew :source-api:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add source-api/src/main/kotlin/com/komgareader/domain/source/SourceId.kt
git commit -m "feat(source-api): reserved SourceId.EXTERNAL for transient external opens"
```

---

## Task 2: Pure format detection + behaviour enum (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ExternalOpenBehavior.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/DetectBookFormat.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/DetectBookFormatTest.kt`

- [ ] **Step 1: Behaviour enum**

```kotlin
package com.komgareader.domain.model

/** What to do when an external book file is opened via a VIEW intent. */
enum class ExternalOpenBehavior { ASK, IMPORT, READ_ONLY }
```

- [ ] **Step 2: Failing test** (this module uses `kotlin.test` + JUnit5, NOT JUnit4)

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectBookFormatTest {
    @Test fun mime_epub() = assertEquals(BookFormat.EPUB, detectBookFormat("application/epub+zip", null))
    @Test fun mime_pdf() = assertEquals(BookFormat.PDF, detectBookFormat("application/pdf", "x"))
    @Test fun mime_cbz_zip() = assertEquals(BookFormat.CBZ, detectBookFormat("application/zip", null))
    @Test fun mime_cbz_comicbook() = assertEquals(BookFormat.CBZ, detectBookFormat("application/vnd.comicbook+zip", null))
    @Test fun mime_cbr() = assertEquals(BookFormat.CBR, detectBookFormat("application/x-cbr", null))
    @Test fun mime_cbr_rar() = assertEquals(BookFormat.CBR, detectBookFormat("application/x-rar-compressed", null))
    @Test fun octet_falls_back_to_extension() =
        assertEquals(BookFormat.CBZ, detectBookFormat("application/octet-stream", "Vol. 1.cbz"))
    @Test fun null_mime_uses_extension() = assertEquals(BookFormat.EPUB, detectBookFormat(null, "book.EPUB"))
    @Test fun extension_pdf() = assertEquals(BookFormat.PDF, detectBookFormat(null, "doc.pdf"))
    @Test fun extension_cbr() = assertEquals(BookFormat.CBR, detectBookFormat(null, "a.cbr"))
    @Test fun unknown_returns_null() = assertNull(detectBookFormat("text/plain", "notes.txt"))
    @Test fun unknown_no_hints_null() = assertNull(detectBookFormat(null, null))
}
```

- [ ] **Step 3: Run it, verify it fails**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.DetectBookFormatTest"`
Expected: FAIL (unresolved `detectBookFormat`).

- [ ] **Step 4: Implement**

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.BookFormat

/**
 * Map an Android VIEW intent's MIME type and/or file name to a [BookFormat], or null
 * if it is not a supported book. MIME is authoritative when specific; the common
 * generic MIME `application/octet-stream` (and a null MIME) fall back to the file
 * name extension. CBZ/CBR are frequently delivered as zip/rar/octet-stream, so both
 * MIME and extension are accepted.
 */
fun detectBookFormat(mime: String?, fileName: String?): BookFormat? {
    fromMime(mime)?.let { return it }
    return fromExtension(fileName)
}

private fun fromMime(mime: String?): BookFormat? = when (mime?.lowercase()?.substringBefore(';')?.trim()) {
    "application/epub+zip" -> BookFormat.EPUB
    "application/pdf" -> BookFormat.PDF
    "application/zip", "application/x-cbz", "application/vnd.comicbook+zip" -> BookFormat.CBZ
    "application/x-cbr", "application/x-rar", "application/x-rar-compressed",
    "application/vnd.comicbook-rar", "application/vnd.rar" -> BookFormat.CBR
    else -> null
}

private fun fromExtension(fileName: String?): BookFormat? = when (fileName?.substringAfterLast('.', "")?.lowercase()) {
    "epub" -> BookFormat.EPUB
    "pdf" -> BookFormat.PDF
    "cbz" -> BookFormat.CBZ
    "cbr" -> BookFormat.CBR
    else -> null
}
```

- [ ] **Step 5: Run it, verify it passes**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.DetectBookFormatTest"`
Expected: PASS (11 tests).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ExternalOpenBehavior.kt \
        domain/src/main/kotlin/com/komgareader/domain/usecase/DetectBookFormat.kt \
        domain/src/test/kotlin/com/komgareader/domain/usecase/DetectBookFormatTest.kt
git commit -m "feat(domain): detectBookFormat + ExternalOpenBehavior"
```

---

## Task 3: externalOpenBehavior setting

**Files:**
- Modify: `domain/.../repository/SettingsRepository.kt`, `data/.../repository/RoomSettingsRepository.kt`

- [ ] **Step 1: Interface** — add next to `bookmarkMarkerStyle`:
```kotlin
    /** "ASK" | "IMPORT" | "READ_ONLY" — what to do when an external book file is opened. */
    val externalOpenBehavior: Flow<String>
    suspend fun setExternalOpenBehavior(value: String)
```

- [ ] **Step 2: Room impl** — mirror `bookmarkMarkerStyle`:
```kotlin
    override val externalOpenBehavior: Flow<String> =
        dao.observe(KEY_EXTERNAL_OPEN_BEHAVIOR).map { it ?: ExternalOpenBehavior.ASK.name }

    override suspend fun setExternalOpenBehavior(value: String) =
        dao.put(SettingEntity(KEY_EXTERNAL_OPEN_BEHAVIOR, value))
```
Add the key constant by `KEY_BOOKMARK_MARKER_STYLE`:
```kotlin
    const val KEY_EXTERNAL_OPEN_BEHAVIOR = "external_open_behavior"
```
Add import `com.komgareader.domain.model.ExternalOpenBehavior`.

- [ ] **Step 3: Build**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt \
        data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt
git commit -m "feat(settings): externalOpenBehavior (ASK/IMPORT/READ_ONLY) setting"
```

---

## Task 4: deleteBySourceId on the download table (TDD)

**Files:**
- Modify: `data/.../db/DownloadDao.kt`, `domain/.../repository/DownloadRepository.kt`, `data/.../repository/RoomDownloadRepository.kt`
- Test: `data/src/androidTest/kotlin/com/komgareader/data/DownloadDaoSourceIdTest.kt`

- [ ] **Step 1: Add DAO query** (`DownloadDao.kt`)
```kotlin
    @Query("DELETE FROM downloads WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: Long)
```

- [ ] **Step 2: Repository interface** (`DownloadRepository.kt`) — add to the interface:
```kotlin
    suspend fun removeBySourceId(sourceId: Long)
```

- [ ] **Step 3: Room impl** (`RoomDownloadRepository.kt`):
```kotlin
    override suspend fun removeBySourceId(sourceId: Long) = dao.deleteBySourceId(sourceId)
```

- [ ] **Step 4: Failing androidTest** (JUnit4, in-memory Room — mirror `NovelBookmarkDaoTest`)

```kotlin
package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.DownloadDao
import com.komgareader.data.db.DownloadEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDaoSourceIdTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadDao

    private fun entity(id: String, source: Long) = DownloadEntity(
        bookRemoteId = id, sourceId = source, seriesRemoteId = "s", title = id,
        format = "cbz", localPath = "content://x/$id", totalPages = 0,
        seriesTitle = "", seriesCoverUrl = null,
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
        dao = db.downloadDao()
    }
    @After fun teardown() = db.close()

    @Test fun deleteBySourceId_removes_only_that_source() = runBlocking {
        dao.put(entity("a", 1L))
        dao.put(entity("b", 1L))
        dao.put(entity("c", 0L))
        dao.deleteBySourceId(1L)
        val left = dao.observeAll().first()
        assertEquals(listOf("c"), left.map { it.bookRemoteId })
    }
}
```
(Confirm the DAO accessor name on `AppDatabase` is `downloadDao()`; if different, match it. Confirm `DownloadEntity`'s exact field set against `Entities.kt` and adjust the constructor.)

- [ ] **Step 5: Run on emulator**

Run: `./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.data.DownloadDaoSourceIdTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add data/ domain/src/main/kotlin/com/komgareader/domain/repository/DownloadRepository.kt
git commit -m "feat(data): DownloadRepository.removeBySourceId + DAO deleteBySourceId"
```

---

## Task 5: ExternalBookOpener (transient open, import, purge)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/ExternalBookOpener.kt`

- [ ] **Step 1: Implement the opener**

```kotlin
package com.komgareader.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.DownloadedBook
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.usecase.detectBookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The reader route + the file name for an external open. */
data class ExternalOpenTarget(val route: String, val fileName: String)

/**
 * Wires an externally-opened file (VIEW intent content:// URI) into the existing
 * reader by inserting a TRANSIENT download row under [SourceId.EXTERNAL] (the reader
 * reads content:// rows via the download table). Also imports a copy into the
 * local(=download) folder, and purges the transient rows.
 */
@Singleton
class ExternalBookOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
) {
    /**
     * Detect the format, register a transient download row, and return the reader
     * route. Returns null if the URI is not a supported book.
     */
    suspend fun prepareEphemeral(uri: Uri): ExternalOpenTarget? {
        val name = displayName(uri)
        val mime = context.contentResolver.getType(uri)
        val format = detectBookFormat(mime, name) ?: return null
        val bookId = Base64.encodeToString(uri.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        downloads.put(
            DownloadedBook(
                bookRemoteId = bookId,
                sourceId = SourceId.EXTERNAL,
                seriesRemoteId = bookId,
                title = name ?: "Buch",
                format = format.name.lowercase(),
                localPath = uri.toString(),
                totalPages = 0,
            ),
        )
        // Viewer mode: format drives it; EPUB → Novel reader (route format), else PAGED.
        val route = "reader/$bookId/${SourceId.EXTERNAL}/0/${format.name}/false/PAGED"
        return ExternalOpenTarget(route, name ?: "Buch")
    }

    /** Copy the URI bytes into the [treeUri] SAF folder. Returns true on success. */
    suspend fun importToFolder(uri: Uri, treeUri: Uri, fileName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching false
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val target = tree.createFile(mime, uniqueName(tree, fileName)) ?: return@runCatching false
            context.contentResolver.openInputStream(uri)?.use { input ->
                context.contentResolver.openOutputStream(target.uri)?.use { out -> input.copyTo(out) }
                    ?: return@runCatching false
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** The configured download(=local) folder tree URI, or null if none is set. */
    suspend fun configuredFolder(): Uri? =
        settings.downloadDir.first()?.let(Uri::parse)

    /** Drop all transient external rows (called on app start and reader exit). */
    suspend fun purgeTransient() = downloads.removeBySourceId(SourceId.EXTERNAL)

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun uniqueName(tree: DocumentFile, name: String): String {
        if (tree.findFile(name) == null) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
            if (tree.findFile(candidate) == null) return candidate
            i++
        }
    }
}
```
(Confirm `DownloadedBook` lives in `com.komgareader.domain.repository`; adjust the import if it is elsewhere. The `documentfile` dependency: confirm `androidx.documentfile:documentfile` is on the `:app` classpath — `source-local` already uses `DocumentFile`, so it should be available transitively; if not, add `implementation(libs.androidx.documentfile)` to `app/build.gradle.kts`.)

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/ExternalBookOpener.kt
git commit -m "feat(app): ExternalBookOpener — transient open + import + purge"
```

---

## Task 6: Purge transient rows on app start

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/SyncCoordinator.kt`

- [ ] **Step 1: Inject + purge**

In the Hilt `@Inject constructor`, add `externalOpener: ExternalBookOpener,` and pass a
`purgeExternal = { externalOpener.purgeTransient() }` lambda into the primary constructor
(mirror how `syncLocalDownloads = { localDownloads.sync() }` is wired). Add a
`private val purgeExternal: suspend () -> Unit` primary-constructor param. Then in
`onAppStart()` append:
```kotlin
        runCatching { purgeExternal() }
```
(Read the file first — match the exact constructor delegation shape shown for
`syncLocalDownloads`.)

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/SyncCoordinator.kt
git commit -m "feat(app): purge transient external downloads on app start"
```

---

## Task 7: Manifest intent-filter + MainActivity intent handling + prompt

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`

- [ ] **Step 1: Manifest intent-filter**

Inside the `MainActivity` `<activity>` (after the LAUNCHER filter), add:
```xml
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" android:mimeType="application/epub+zip" />
                <data android:scheme="content" android:mimeType="application/pdf" />
                <data android:scheme="content" android:mimeType="application/zip" />
                <data android:scheme="content" android:mimeType="application/vnd.comicbook+zip" />
                <data android:scheme="content" android:mimeType="application/x-cbz" />
                <data android:scheme="content" android:mimeType="application/x-cbr" />
                <data android:scheme="content" android:mimeType="application/vnd.comicbook-rar" />
                <data android:scheme="content" android:mimeType="application/vnd.rar" />
                <data android:scheme="content" android:mimeType="application/octet-stream" />
            </intent-filter>
```
(`application/octet-stream` casts a wide net so generically-typed CBZ/CBR are offered; the
runtime `detectBookFormat` rejects non-books, so a wrong octet-stream file just shows the
"not supported" message.)

- [ ] **Step 2: MainActivity — capture the launch/new intent URI**

Inject the opener: add `@Inject lateinit var externalOpener: ExternalBookOpener` to the
Activity. Hold the pending URI as Compose state. Read the file first to match its style;
add near the top of the class:
```kotlin
    private val pendingExternalUri = mutableStateOf<Uri?>(null)

    private fun captureViewIntent(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { pendingExternalUri.value = it }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        captureViewIntent(intent)
    }
```
In `onCreate`, before `setContent`, call `captureViewIntent(intent)`.
(Imports: `androidx.compose.runtime.mutableStateOf`; `android.net.Uri`.)

- [ ] **Step 3: MainActivity — resolve behaviour, prompt, navigate**

Inside `setContent`, after the `NavHost(...)` block (so `nav` exists), add the external-open
driver. It collects the behaviour, and on a pending URI either navigates ephemerally,
imports, or shows the prompt:
```kotlin
        val behavior by settingsViewModel.externalOpenBehavior.collectAsState()
        var promptTarget by remember { mutableStateOf<ExternalOpenTarget?>(null) }
        var promptUri by remember { mutableStateOf<Uri?>(null) }
        val scope = rememberCoroutineScope()
        val strings = LocalStrings.current

        // Folder picker used when importing with no folder configured yet.
        val importPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { tree: Uri? ->
            val uri = promptUri
            if (tree != null && uri != null) {
                contentResolver.takePersistableUriPermission(
                    tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                settingsViewModel.setBothFolders(tree.lastPathSegment?.substringAfterLast('/') ?: "Ordner", tree.toString())
                scope.launch {
                    externalOpener.importToFolder(uri, tree, promptTarget?.fileName ?: "buch")
                    syncCoordinator.onManualReload()
                }
            }
        }

        LaunchedEffect(pendingExternalUri.value) {
            val uri = pendingExternalUri.value ?: return@LaunchedEffect
            pendingExternalUri.value = null
            val target = externalOpener.prepareEphemeral(uri)
            if (target == null) { /* unsupported */ return@LaunchedEffect }
            nav.navigate(target.route)
            when (ExternalOpenBehavior.valueOf(behavior)) {
                ExternalOpenBehavior.READ_ONLY -> {}
                ExternalOpenBehavior.IMPORT -> {
                    val folder = externalOpener.configuredFolder()
                    if (folder != null) {
                        externalOpener.importToFolder(uri, folder, target.fileName)
                        syncCoordinator.onManualReload()
                    } else { promptUri = uri; promptTarget = target } // fall back to the prompt's picker
                }
                ExternalOpenBehavior.ASK -> { promptUri = uri; promptTarget = target }
            }
        }
```
(Imports: `androidx.activity.compose.rememberLauncherForActivityResult`,
`androidx.activity.result.contract.ActivityResultContracts`,
`androidx.compose.runtime.*`, `com.komgareader.domain.model.ExternalOpenBehavior`,
`com.komgareader.app.data.ExternalOpenTarget`, `kotlinx.coroutines.launch`.)

- [ ] **Step 4: MainActivity — the prompt modal**

After the driver, render the prompt when `promptTarget` is set (ASK, or IMPORT with no folder):
```kotlin
        promptTarget?.let { target ->
            var remember by remember { mutableStateOf(false) }
            EinkModal(
                title = strings.externalOpenTitle,
                onDismiss = { promptTarget = null; promptUri = null },
                onConfirm = {
                    if (remember) settingsViewModel.setExternalOpenBehavior(ExternalOpenBehavior.IMPORT.name)
                    val uri = promptUri
                    val folder = /* resolved below */ null as Uri?
                    promptTarget = null
                    if (uri != null) scope.launch {
                        val f = externalOpener.configuredFolder()
                        if (f != null) { externalOpener.importToFolder(uri, f, target.fileName); syncCoordinator.onManualReload() }
                        else importPicker.launch(null)
                    }
                },
                confirmLabel = strings.externalOpenImport,
                dismissLabel = strings.externalOpenReadOnly,
                onDismissAction = { if (remember) settingsViewModel.setExternalOpenBehavior(ExternalOpenBehavior.READ_ONLY.name) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = remember, onCheckedChange = { remember = it })
                    Text(strings.externalOpenRemember)
                }
            }
        }
```
> Read `EinkModal`'s ACTUAL signature first (`app/.../ui/components/EinkModal.kt`). It may
> not have a separate `onDismissAction`; if dismiss is a single `onDismiss`, fold the
> "remember READ_ONLY" write into `onDismiss`. Match the real parameter names
> (`confirmLabel`/`dismissLabel`/`onConfirm`/`onDismiss`). Remove the dead `folder` line —
> it's a leftover; resolve the folder inside the `scope.launch` as shown. Keep the body a
> simple labelled checkbox row.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/com/komgareader/app/MainActivity.kt
git commit -m "feat(app): handle VIEW intent — open external book + import prompt"
```

---

## Task 8: Settings — folder default coupling + behaviour row + i18n

**Files:**
- Modify: `app/.../i18n/Strings.kt`, `app/.../ui/settings/SettingsViewModel.kt`, `app/.../ui/settings/SettingsContent.kt`

- [ ] **Step 1: i18n keys** (interface + StringsDe + StringsEn + MapBackedStrings — all four)

Interface:
```kotlin
    val externalOpenTitle: String
    val externalOpenImport: String
    val externalOpenReadOnly: String
    val externalOpenRemember: String
    val externalOpenSetting: String
    val externalOpenAsk: String
    val externalOpenUnsupported: String
```
StringsDe:
```kotlin
    override val externalOpenTitle = "Datei in Bibliothek übernehmen?"
    override val externalOpenImport = "Importieren"
    override val externalOpenReadOnly = "Nur lesen"
    override val externalOpenRemember = "Auswahl merken"
    override val externalOpenSetting = "Externe Dateien öffnen"
    override val externalOpenAsk = "Fragen"
    override val externalOpenUnsupported = "Dateiformat nicht unterstützt"
```
StringsEn:
```kotlin
    override val externalOpenTitle = "Add file to your library?"
    override val externalOpenImport = "Import"
    override val externalOpenReadOnly = "Read only"
    override val externalOpenRemember = "Remember choice"
    override val externalOpenSetting = "Open external files"
    override val externalOpenAsk = "Ask"
    override val externalOpenUnsupported = "File format not supported"
```
MapBackedStrings: add each as `overrides["..."] ?: fallback.<key>` in its existing style.
(`externalOpenImport`/`externalOpenReadOnly` reuse for the SegmentedChoiceRow's IMPORT/READ_ONLY labels.)

- [ ] **Step 2: SettingsViewModel** — state + setter (mirror `bookmarkMarkerStyle`):
```kotlin
    val externalOpenBehavior =
        settings.externalOpenBehavior.stateIn(viewModelScope, SharingStarted.Eagerly, ExternalOpenBehavior.ASK.name)
```
```kotlin
    fun setExternalOpenBehavior(value: String) =
        viewModelScope.launch { settings.setExternalOpenBehavior(value) }.let {}
```
(Import `com.komgareader.domain.model.ExternalOpenBehavior`.)

- [ ] **Step 3: SettingsContent — couple the download picker to both folders**

Change the existing `downloadPicker` callback (currently `viewModel.setDownloadDir(uri.toString())`) to set both folders, making local default to the download folder:
```kotlin
    val downloadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            takeTreePermission(uri)
            viewModel.setBothFolders(folderName(uri), uri.toString())
        }
    }
```
Remove the now-redundant `SettingsGroup(s.sameFolderForBoth, ...)` block (the "use same
folder" button) and its `bothPicker`. Keep the separate **local-folder** group as an advanced
override (so a user can still point the local folder elsewhere). The `s.sameFolderForBoth`/
`s.useSameFolderForBoth`/`s.sameFolderForBothHelp` i18n keys may now be unused — leave them
defined (removing i18n keys risks breaking other call sites; verify with grep before deleting).

- [ ] **Step 4: SettingsContent — behaviour row** in the Downloads group:
```kotlin
    SettingsGroup(s.externalOpenSetting, query) {
        SegmentedChoiceRow(
            label = s.externalOpenSetting,
            options = listOf(
                SegmentOption(ExternalOpenBehavior.ASK.name, s.externalOpenAsk),
                SegmentOption(ExternalOpenBehavior.IMPORT.name, s.externalOpenImport),
                SegmentOption(ExternalOpenBehavior.READ_ONLY.name, s.externalOpenReadOnly),
            ),
            selectedKey = externalOpenBehavior,
            onSelect = { viewModel.setExternalOpenBehavior(it) },
            query = query,
        )
    }
```
Collect the value in the composable: `val externalOpenBehavior by viewModel.externalOpenBehavior.collectAsState()`.
(Import `com.komgareader.domain.model.ExternalOpenBehavior`.)

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (all 4 Strings impls satisfy the interface).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt \
        app/src/main/kotlin/com/komgareader/app/ui/settings/
git commit -m "feat(settings): external-open behaviour row + download folder defaults to local"
```

---

## Task 9: Full build, verification, doc sync

**Files:**
- Modify: `CLAUDE.md` (if a top-level statement changed), `.claude/rules/architecture-seams.md` / `source-extensibility.md`, the English docs, the spec status.

- [ ] **Step 1: Full unit build**

Run: `./gradlew :domain:test :data:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all unit tests green.

- [ ] **Step 2: Emulator checks**

Run: `./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.data.DownloadDaoSourceIdTest`
Expected: PASS. Then `./gradlew :app:installDebug`, and via adb send a VIEW intent for a CBZ
to confirm the ephemeral open + prompt path:
`adb shell am start -a android.intent.action.VIEW -d <content-uri> -t application/zip -n com.komgareader.app/.MainActivity` (use a content:// URI from a SAF provider; a CBZ in the emulator's Downloads exposed via DocumentsUI works). Verify the reader opens and the import prompt appears.

- [ ] **Step 3: Real Boox**

`installDebug` on an arm64 Boox; from the Boox file manager, long-press a book → "open with" → confirm our app is listed (screenshot); open a CBZ + an EPUB; tap Import → confirm the file lands in the folder and shows as a local work after the next scan; flip the Settings → Downloads behaviour and re-open to confirm ASK/IMPORT/READ_ONLY.

- [ ] **Step 4: Doc sync** — invoke the `komga-doc-sync` skill. At minimum: note the external-file handler + `SourceId.EXTERNAL` + the transient-download mechanism in `.claude/rules/architecture-seams.md` (Naht A / source-agnostic note) and `source-extensibility.md` if relevant; document the folder-default coupling; flip the spec `Status:` to implemented (device-pending for EPUB/handler-listing); update README/ARCHITECTURE/PROJECT-STATUS (new "open external files" capability).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: sync external book file handler (intent handler, SourceId.EXTERNAL, folder default)"
```

---

## Self-Review

- **Spec coverage:** intent-filter + handler (T7) ✓; format detection (T2) ✓; ephemeral via transient download row + reserved EXTERNAL (T1/T5) ✓; purge on app start (T6) ✓; import to local(=download) folder + no-folder picker fallback (T5/T7) ✓; folder default coupling (T8) ✓; prompt with "remember" + IMPORT/READ_ONLY/ASK (T7/T3) ✓; Settings → Downloads behaviour row (T8) ✓; viewer type format-driven (T5 route, viewerMode=PAGED, EPUB→Novel via format) ✓; i18n de+en+MapBacked (T8) ✓; E-Ink modal (T7) ✓; tests (T2 pure, T4 DAO, T9 emulator/Boox) ✓; device gate for EPUB/handler-listing (T9) ✓.
- **Type consistency:** `SourceId.EXTERNAL`(T1) used in T5/T4-test; `detectBookFormat`(T2) used in T5; `ExternalOpenBehavior`(T2) used in T3/T7/T8; `ExternalOpenTarget`(T5) used in T7; `removeBySourceId`/`deleteBySourceId`(T4) used in T5 purge; `externalOpenBehavior`/`setExternalOpenBehavior`(T3) used in T7/T8; `setBothFolders`(existing) used in T7/T8.
- **Placeholders:** none — every code step is concrete. Two steps explicitly flag "read the real signature first" (EinkModal in T7-Step4, SyncCoordinator constructor in T6) with concrete fallback instructions; those are integration-fit checks, not blanks.
- **Known risk surfaced:** T7-Step4's modal sketch has a noted dead `folder` line to remove and depends on EinkModal's real signature — the implementer adapts. Octet-stream intent-filter is intentionally broad (T7-Step1) and gated by `detectBookFormat` at runtime.
