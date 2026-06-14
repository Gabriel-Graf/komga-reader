# LocalSource Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `LocalSource` (Seam A `BrowsableSource`) that turns a user-picked device folder into a browsable, readable source, fully mixed with Komga/OPDS, reading "like downloads".

**Architecture:** New Android-library module `:source-local` (depends only on `:domain` + `:source-api` + `androidx.documentfile`, **never `:render-core`**). CBZ pages stream via `openPage` (raw zip-entry extract, `java.util.zip`); PDF/CBR/EPUB read whole-file via `downloadFile` and are rendered by the existing render layer. One `SourceKind.LOCAL` branch in `SourceRegistration`, one general `ReaderViewModel` fallback, one Settings "Local folder" add-source segment.

**Tech Stack:** Kotlin, coroutines, Android SAF (`DocumentFile`/`ContentResolver`), `java.util.zip`, `kotlin.test` (Android-lib unit-test runner / JUnit4 platform, like `data` — no JUnit5), Hilt, Jetpack Compose. Run unit tests with `./gradlew :source-local:testDebugUnitTest`.

**Spec:** `docs/superpowers/specs/2026-06-14-local-source-design.md` (read it first).

> CBR decision (Task 0): **IN** — the app already renders downloaded Komga CBR via MuPDF whole-file (`ReaderContent.kt:30` "CBZ/CBR/PDF offline"; `KomgaFormat` maps `application/x-rar-compressed`→`CBR`). LocalSource handles CBR exactly like PDF: `pages()` empty → reader whole-file MuPDF render (no junrar, no zip-extract — RAR isn't zip). Confirm with a real `.cbr` sample in the E2E (Task 12); if MuPDF can't open it on-device, drop `cbr` from `BOOK_EXTENSIONS` then.

---

## File Structure

- `settings.gradle.kts` — add `include(":source-local")`
- `source-local/build.gradle.kts` — Android library, deps `:domain` + `:source-api` + `androidx.documentfile` + coroutines
- `source-local/src/main/AndroidManifest.xml` — empty manifest (package only)
- `source-local/src/main/kotlin/com/komgareader/source/local/`
  - `LocalNaming.kt` — pure helpers: natural sort, extension→`BookFormat`, image-entry filter
  - `LocalMetadataParser.kt` — pure ComicInfo.xml parser (+ filename fallback already applied by mapper)
  - `CbzArchive.kt` — pure: list ordered image entries + extract entry bytes by index (from a `File`)
  - `LocalLibraryMapper.kt` — pure: `List<ScannedEntry>` → `LocalIndex(series, books)`
  - `LocalModels.kt` — `ScannedEntry`, `LocalIndex`, `LocalSeries`, `LocalBook`
  - `LocalFileCache.kt` — Android: SAF uri → cached `File` (LRU, size-capped)
  - `LocalFolderScanner.kt` — Android: `DocumentFile` tree walk → `List<ScannedEntry>`
  - `LocalSource.kt` — `BrowsableSource` impl assembling the above
  - `LocalSourceFactory.kt` — `create(context, name, rootTreeUri): LocalSource`
- `source-local/src/test/kotlin/com/komgareader/source/local/` — pure unit tests
- Modify: `app/.../data/SourceRegistration.kt` (LOCAL branch + Context)
- Modify: `app/.../di/AppModule.kt` (only if `SourceRegistration` needs Context provided — verify)
- Modify: `app/.../ui/reader/ReaderViewModel.kt:173-182` (whole-file fallback)
- Modify: `app/.../ui/settings/SettingsContent.kt` + `SettingsViewModel.kt` (Local-folder add-source)
- Modify: app i18n (de + en) — new keys
- Modify docs: `.claude/rules/architecture-seams.md`, `.claude/rules/source-extensibility.md`, `CLAUDE.md`

---

## Task 0: Verify MuPDF CBR support (decision gate, no code)

**Goal:** Decide whether `.cbr` is in V1. Spec routes CBR through whole-file MuPDF render (no junrar).

- [ ] **Step 1: Check the MuPDF build for RAR/CBR.**

Run: `grep -rin "cbr\|rar\|libarchive\|unrar" render-core/ 2>/dev/null; ./gradlew :render-core:dependencies --configuration releaseRuntimeClasspath 2>/dev/null | grep -i mupdf`
Inspect the MuPDF artifact version in `gradle/libs.versions.toml` (`com.artifex.mupdf`). MuPDF's `mutool` supports `.cbz` always; `.cbr` only if built with libarchive/unrar.

- [ ] **Step 2: Decide and record.**

If unclear from grep, default to **CBR excluded in V1** (safest). Record the decision in a one-line note at the top of this plan file (edit it):
`> CBR decision (Task 0): <in|out> — reason.`
All later tasks treat the recognized-extensions set accordingly. CBZ/PDF/EPUB are always in.

- [ ] **Step 3: Commit the note.**

```bash
git add docs/superpowers/plans/2026-06-14-source-local.md
git commit -m "docs(plan): record CBR-in-V1 decision for LocalSource"
```

---

## Task 1: Scaffold the `:source-local` module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `source-local/build.gradle.kts`
- Create: `source-local/src/main/AndroidManifest.xml`
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/LocalModels.kt`

- [ ] **Step 1: Add the module to settings.**

In `settings.gradle.kts`, add after `include(":source-opds")`:
```kotlin
include(":source-local")
```

- [ ] **Step 2: Create `source-local/build.gradle.kts`.**

Mirror an existing Android-library module (e.g. `data/build.gradle.kts`) for the `com.android.library` plugin id, `namespace`, `compileSdk`/`minSdk`, and Kotlin/jvmTarget. Read `data/build.gradle.kts` first to copy the exact plugin aliases and SDK values, then:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.komgareader.source.local"
    compileSdk = 35 // match data/build.gradle.kts
    defaultConfig { minSdk = 26 } // match data/build.gradle.kts
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}
dependencies {
    implementation(project(":domain"))
    implementation(project(":source-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.documentfile) // add to libs.versions.toml if missing: androidx.documentfile:documentfile:1.0.1

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
}
tasks.withType<Test> { useJUnitPlatform() }
```
If `libs.androidx.documentfile` is not in `gradle/libs.versions.toml`, add it (version `1.0.1`).

- [ ] **Step 3: Create the empty manifest.**

`source-local/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 4: Create `LocalModels.kt` (internal index types).**

```kotlin
package com.komgareader.source.local

import com.komgareader.domain.model.BookFormat

/** One file/folder discovered by the scanner, path relative to the picked root. */
data class ScannedEntry(
    val relativePath: String, // e.g. "Berserk/v01.cbz" or "oneshot.pdf"
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
)

/** Pure, in-memory library index built from scanned entries. */
data class LocalIndex(val series: List<LocalSeries>) {
    fun series(remoteId: String): LocalSeries? = series.firstOrNull { it.remoteId == remoteId }
    fun book(bookRemoteId: String): LocalBook? =
        series.firstNotNullOfOrNull { s -> s.books.firstOrNull { it.remoteId == bookRemoteId } }
}

data class LocalSeries(
    val remoteId: String, // relative folder path, or the loose file's relative path
    val title: String,
    val books: List<LocalBook>,
)

data class LocalBook(
    val remoteId: String, // relative file path
    val title: String,
    val format: BookFormat,
    val number: String? = null,
    val summary: String? = null,
)
```

- [ ] **Step 5: Verify the module compiles.**

Run: `./gradlew :source-local:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add settings.gradle.kts source-local/ gradle/libs.versions.toml
git commit -m "feat(source-local): scaffold module + index models"
```

---

## Task 2: Pure naming/format helpers (`LocalNaming`)

**Files:**
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/LocalNaming.kt`
- Test: `source-local/src/test/kotlin/com/komgareader/source/local/LocalNamingTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.komgareader.source.local

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalNamingTest {
    @Test fun `natural sort orders v2 before v10`() {
        val input = listOf("v10.cbz", "v2.cbz", "v1.cbz")
        assertEquals(listOf("v1.cbz", "v2.cbz", "v10.cbz"), input.sortedWith(naturalOrder))
    }

    @Test fun `format from extension is case-insensitive`() {
        assertEquals(BookFormat.CBZ, formatOf("Berserk/v01.CBZ"))
        assertEquals(BookFormat.PDF, formatOf("a.pdf"))
        assertEquals(BookFormat.EPUB, formatOf("b.EPUB"))
        assertNull(formatOf("notes.txt"))
        assertNull(formatOf("folder"))
    }

    @Test fun `title strips extension and path`() {
        assertEquals("v01", titleOf("Berserk/v01.cbz"))
        assertEquals("oneshot", titleOf("oneshot.pdf"))
    }

    @Test fun `image entry filter keeps only images`() {
        assertEquals(true, isImageEntry("001.jpg"))
        assertEquals(true, isImageEntry("a/002.PNG"))
        assertEquals(false, isImageEntry("ComicInfo.xml"))
        assertEquals(false, isImageEntry("dir/"))
    }
}
```

- [ ] **Step 2: Run, verify it fails.**

Run: `./gradlew :source-local:testDebugUnitTest --tests "*LocalNamingTest*"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement `LocalNaming.kt`.**

```kotlin
package com.komgareader.source.local

import com.komgareader.domain.model.BookFormat

/** Recognized book extensions. CBR included iff Task 0 decided "in". */
private val BOOK_EXTENSIONS: Map<String, BookFormat> = mapOf(
    "cbz" to BookFormat.CBZ,
    "cbr" to BookFormat.CBR, // CBR IN (Task 0) — whole-file MuPDF, like PDF
    "pdf" to BookFormat.PDF,
    "epub" to BookFormat.EPUB,
)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private fun extOf(path: String): String =
    path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

fun formatOf(path: String): BookFormat? =
    if (path.endsWith("/")) null else BOOK_EXTENSIONS[extOf(path)]

fun titleOf(path: String): String =
    path.substringAfterLast('/').substringBeforeLast('.')

fun isImageEntry(entryName: String): Boolean =
    !entryName.endsWith("/") && extOf(entryName) in IMAGE_EXTENSIONS

/**
 * Natural order: compares numeric runs by value so `v2` < `v10`. Case-insensitive
 * on non-numeric runs. Pure, total order, stable.
 */
val naturalOrder: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

private fun compareNatural(a: String, b: String): Int {
    var i = 0; var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]; val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var ei = i; while (ei < a.length && a[ei].isDigit()) ei++
            var ej = j; while (ej < b.length && b[ej].isDigit()) ej++
            val na = a.substring(i, ei).trimStart('0').ifEmpty { "0" }
            val nb = b.substring(j, ej).trimStart('0').ifEmpty { "0" }
            val cmp = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
            if (cmp != 0) return cmp
            i = ei; j = ej
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return cmp
            i++; j++
        }
    }
    return (a.length - i) - (b.length - j)
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `./gradlew :source-local:testDebugUnitTest --tests "*LocalNamingTest*"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add source-local/src/main/kotlin/com/komgareader/source/local/LocalNaming.kt source-local/src/test/kotlin/com/komgareader/source/local/LocalNamingTest.kt
git commit -m "feat(source-local): natural sort + format/image helpers"
```

---

## Task 3: ComicInfo.xml parser (`LocalMetadataParser`)

**Files:**
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/LocalMetadataParser.kt`
- Test: `source-local/src/test/kotlin/com/komgareader/source/local/LocalMetadataParserTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.komgareader.source.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalMetadataParserTest {
    private val parser = LocalMetadataParser()

    @Test fun `parses series title number summary genres`() {
        val xml = """
            <?xml version="1.0"?>
            <ComicInfo>
              <Series>Berserk</Series>
              <Number>3</Number>
              <Summary>Guts fights.</Summary>
              <Genre>Action, Dark Fantasy</Genre>
            </ComicInfo>
        """.trimIndent()
        val m = parser.parse(xml)!!
        assertEquals("Berserk", m.series)
        assertEquals("3", m.number)
        assertEquals("Guts fights.", m.summary)
        assertEquals(listOf("Action", "Dark Fantasy"), m.genres)
    }

    @Test fun `missing fields are null or empty`() {
        val m = parser.parse("<ComicInfo><Number>1</Number></ComicInfo>")!!
        assertNull(m.series)
        assertEquals("1", m.number)
        assertNull(m.summary)
        assertEquals(emptyList(), m.genres)
    }

    @Test fun `non-comicinfo or garbage returns null`() {
        assertNull(parser.parse("not xml at all"))
        assertNull(parser.parse("<other/>"))
    }
}
```

- [ ] **Step 2: Run, verify it fails.**

Run: `./gradlew :source-local:testDebugUnitTest --tests "*LocalMetadataParserTest*"`
Expected: FAIL.

- [ ] **Step 3: Implement `LocalMetadataParser.kt`.**

Use the JDK XML parser (`javax.xml.parsers`) — available on Android, no dependency. Parse defensively (return null on any error or when root is not `ComicInfo`).
```kotlin
package com.komgareader.source.local

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Subset of ComicInfo.xml relevant to the library. All fields optional. */
data class ComicInfoMeta(
    val series: String? = null,
    val number: String? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
)

class LocalMetadataParser {
    fun parse(xml: String): ComicInfoMeta? = runCatching {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
        val root = doc.documentElement ?: return null
        if (!root.nodeName.equals("ComicInfo", ignoreCase = true)) return null
        ComicInfoMeta(
            series = text(root, "Series"),
            number = text(root, "Number"),
            summary = text(root, "Summary"),
            genres = text(root, "Genre")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
            status = text(root, "Status"),
        )
    }.getOrNull()

    private fun text(root: Element, tag: String): String? =
        root.getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifBlank { null }
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `./gradlew :source-local:testDebugUnitTest --tests "*LocalMetadataParserTest*"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add source-local/src/main/kotlin/com/komgareader/source/local/LocalMetadataParser.kt source-local/src/test/kotlin/com/komgareader/source/local/LocalMetadataParserTest.kt
git commit -m "feat(source-local): ComicInfo.xml parser"
```

---

## Task 4: CBZ archive reader (`CbzArchive`)

**Files:**
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/CbzArchive.kt`
- Test: `source-local/src/test/kotlin/com/komgareader/source/local/CbzArchiveTest.kt`

- [ ] **Step 1: Write the failing test (builds a real zip in a temp file).**

```kotlin
package com.komgareader.source.local

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CbzArchiveTest {
    private val dir: File = createTempDirectory("cbz-test").toFile()

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private fun makeCbz(): File {
        val f = File(dir, "vol.cbz")
        ZipOutputStream(f.outputStream()).use { zip ->
            // intentionally out of order + a non-image entry
            listOf("010.jpg" to "ten", "ComicInfo.xml" to "<ComicInfo/>", "002.jpg" to "two", "001.jpg" to "one")
                .forEach { (name, body) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
                }
        }
        return f
    }

    @Test fun `image entries are listed in natural order, non-images excluded`() {
        val cbz = CbzArchive(makeCbz())
        assertEquals(3, cbz.pageCount())
        assertContentEquals("one".toByteArray(), cbz.pageBytes(0))
        assertContentEquals("two".toByteArray(), cbz.pageBytes(1))
        assertContentEquals("ten".toByteArray(), cbz.pageBytes(2))
    }

    @Test fun `comicinfo bytes returned when present`() {
        val cbz = CbzArchive(makeCbz())
        assertEquals("<ComicInfo/>", cbz.comicInfoXml())
    }
}
```
Note: this module uses `kotlin.test` on the Android-library unit-test runner (JUnit4 platform — same as `data`). Do NOT use JUnit5 `@TempDir`/`@org.junit.jupiter.*`; use `kotlin.io.path.createTempDirectory` + `@AfterTest` cleanup as shown. Run unit tests with `./gradlew :source-local:testDebugUnitTest`.

- [ ] **Step 2: Run, verify it fails.**

Run: `./gradlew :source-local:testDebugUnitTest --tests "*CbzArchiveTest*"`
Expected: FAIL.

- [ ] **Step 3: Implement `CbzArchive.kt`.**

```kotlin
package com.komgareader.source.local

import java.io.File
import java.util.zip.ZipFile

/**
 * Random-access reader over a CBZ (zip) file. Image entries are exposed in
 * natural-sorted order; extraction returns the stored bytes verbatim (no decode).
 * Construct over a real [File] (use [LocalFileCache] to materialize SAF content).
 */
class CbzArchive(private val file: File) {

    private val imageNames: List<String> by lazy {
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { isImageEntry(it) }
                .sortedWith(naturalOrder)
                .toList()
        }
    }

    fun pageCount(): Int = imageNames.size

    fun pageBytes(index: Int): ByteArray {
        val name = imageNames[index]
        return ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(name)).readBytes() }
    }

    /** First image entry bytes (cover), or empty if none. */
    fun coverBytes(): ByteArray = if (imageNames.isEmpty()) ByteArray(0) else pageBytes(0)

    /** ComicInfo.xml content if present (case-insensitive), else null. */
    fun comicInfoXml(): String? = ZipFile(file).use { zip ->
        val entry = zip.entries().asSequence()
            .firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true) }
            ?: return null
        zip.getInputStream(entry).readBytes().decodeToString()
    }
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `./gradlew :source-local:testDebugUnitTest --tests "*CbzArchiveTest*"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add source-local/src/main/kotlin/com/komgareader/source/local/CbzArchive.kt source-local/src/test/kotlin/com/komgareader/source/local/CbzArchiveTest.kt
git commit -m "feat(source-local): CBZ random-access page/cover/comicinfo reader"
```

---

## Task 5: Folder→index mapping (`LocalLibraryMapper`)

**Files:**
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/LocalLibraryMapper.kt`
- Test: `source-local/src/test/kotlin/com/komgareader/source/local/LocalLibraryMapperTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.komgareader.source.local

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalLibraryMapperTest {
    private val mapper = LocalLibraryMapper()

    @Test fun `subfolder becomes a series with its files as natural-sorted books`() {
        val entries = listOf(
            ScannedEntry("Berserk", isDirectory = true),
            ScannedEntry("Berserk/v10.cbz", isDirectory = false),
            ScannedEntry("Berserk/v2.cbz", isDirectory = false),
            ScannedEntry("Berserk/notes.txt", isDirectory = false), // ignored
        )
        val index = mapper.map(entries)
        val s = index.series.single()
        assertEquals("Berserk", s.title)
        assertEquals("Berserk", s.remoteId)
        assertEquals(listOf("Berserk/v2.cbz", "Berserk/v10.cbz"), s.books.map { it.remoteId })
        assertEquals(BookFormat.CBZ, s.books.first().format)
    }

    @Test fun `loose root file becomes a single-volume series`() {
        val index = mapper.map(listOf(ScannedEntry("oneshot.pdf", isDirectory = false)))
        val s = index.series.single()
        assertEquals("oneshot", s.title)
        assertEquals("oneshot.pdf", s.remoteId)
        assertEquals(listOf("oneshot.pdf"), s.books.map { it.remoteId })
        assertEquals(BookFormat.PDF, s.books.single().format)
    }

    @Test fun `empty folders and unsupported files yield no series`() {
        val index = mapper.map(
            listOf(
                ScannedEntry("Empty", isDirectory = true),
                ScannedEntry("readme.txt", isDirectory = false),
            ),
        )
        assertEquals(emptyList(), index.series)
    }

    @Test fun `series are natural-sorted by title`() {
        val index = mapper.map(
            listOf(
                ScannedEntry("Series 10", isDirectory = true),
                ScannedEntry("Series 10/a.cbz", isDirectory = false),
                ScannedEntry("Series 2", isDirectory = true),
                ScannedEntry("Series 2/a.cbz", isDirectory = false),
            ),
        )
        assertEquals(listOf("Series 2", "Series 10"), index.series.map { it.title })
    }
}
```

- [ ] **Step 2: Run, verify it fails.**

Run: `./gradlew :source-local:testDebugUnitTest --tests "*LocalLibraryMapperTest*"`
Expected: FAIL.

- [ ] **Step 3: Implement `LocalLibraryMapper.kt`.**

Pure: groups recognized files by their top-level path segment. A file directly in root → its own single-volume series. ComicInfo enrichment happens later in `LocalSource` (needs archive bytes); here titles come from names.
```kotlin
package com.komgareader.source.local

class LocalLibraryMapper {
    fun map(entries: List<ScannedEntry>): LocalIndex {
        val files = entries.filter { !it.isDirectory && formatOf(it.relativePath) != null }
        // Group by top-level segment: files under a subfolder share the folder; loose files stand alone.
        val grouped: Map<String?, List<ScannedEntry>> = files.groupBy { e ->
            val seg = e.relativePath.substringBefore('/')
            if (seg == e.relativePath) null else seg // null = loose root file
        }

        val series = mutableListOf<LocalSeries>()

        // Subfolder series
        grouped.filterKeys { it != null }.forEach { (folder, folderFiles) ->
            val books = folderFiles
                .sortedWith(compareBy(naturalOrder) { it.relativePath })
                .map { it.toBook() }
            if (books.isNotEmpty()) {
                series += LocalSeries(remoteId = folder!!, title = folder, books = books)
            }
        }

        // Loose root files → single-volume series each
        grouped[null].orEmpty().forEach { f ->
            series += LocalSeries(
                remoteId = f.relativePath,
                title = titleOf(f.relativePath),
                books = listOf(f.toBook()),
            )
        }

        return LocalIndex(series.sortedWith(compareBy(naturalOrder) { it.title }))
    }

    private fun ScannedEntry.toBook() = LocalBook(
        remoteId = relativePath,
        title = titleOf(relativePath),
        format = formatOf(relativePath)!!,
    )
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `./gradlew :source-local:testDebugUnitTest --tests "*LocalLibraryMapperTest*"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add source-local/src/main/kotlin/com/komgareader/source/local/LocalLibraryMapper.kt source-local/src/test/kotlin/com/komgareader/source/local/LocalLibraryMapperTest.kt
git commit -m "feat(source-local): pure folder→series/book mapper"
```

---

## Task 6: SAF file cache (`LocalFileCache`) + folder scanner (`LocalFolderScanner`)

**Files:**
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/LocalFileCache.kt`
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/LocalFolderScanner.kt`

These are Android glue (no pure unit test — exercised by the E2E in Task 12). Keep them thin.

- [ ] **Step 1: Implement `LocalFileCache.kt`.**

Materializes a SAF document (by its content `Uri`) into an app-cache `File` for random access, with a simple size-capped LRU. Read `data/.../download/DownloadManager.kt` first to copy the project's `ContentResolver.openInputStream` idiom.
```kotlin
package com.komgareader.source.local

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies a SAF document into the app cache once (so we get random access from a
 * real File), then serves the cached copy. Size-capped LRU by last-access time.
 */
class LocalFileCache(
    private val context: Context,
    private val maxBytes: Long = 512L * 1024 * 1024, // 512 MB cap
) {
    private val dir: File = File(context.cacheDir, "local-source").apply { mkdirs() }

    /** Returns a real File holding [documentUri]'s bytes, copying on first use. */
    fun materialize(documentUri: Uri, cacheKey: String): File {
        val target = File(dir, safe(cacheKey))
        if (!target.exists() || target.length() == 0L) {
            context.contentResolver.openInputStream(documentUri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open local document: $documentUri")
        }
        target.setLastModified(System.currentTimeMillis())
        evictIfNeeded()
        return target
    }

    private fun evictIfNeeded() {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        var i = 0
        while (total > maxBytes && i < files.size) {
            total -= files[i].length(); files[i].delete(); i++
        }
    }

    private fun safe(key: String): String =
        key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(180)
}
```

- [ ] **Step 2: Implement `LocalFolderScanner.kt`.**

Walks the picked tree one logical level for series + their files (subfolders one deep + loose root files), producing `ScannedEntry`s with paths relative to the root, plus a resolver from a `relativePath` to its `DocumentFile`/`Uri` for reading.
```kotlin
package com.komgareader.source.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * SAF tree walk over the picked root: subfolders (one level) become series folders,
 * files inside them are books; loose files in the root become single-volume series.
 * Also resolves a relativePath back to its content Uri for reading.
 */
class LocalFolderScanner(private val context: Context, private val rootTreeUri: Uri) {

    private val root: DocumentFile? get() = DocumentFile.fromTreeUri(context, rootTreeUri)

    fun scan(): List<ScannedEntry> {
        val r = root ?: return emptyList()
        val out = mutableListOf<ScannedEntry>()
        r.listFiles().forEach { child ->
            if (child.isDirectory) {
                val folderName = child.name ?: return@forEach
                out += ScannedEntry(folderName, isDirectory = true)
                child.listFiles().forEach { f ->
                    if (!f.isDirectory) {
                        val name = f.name ?: return@forEach
                        out += ScannedEntry("$folderName/$name", isDirectory = false, sizeBytes = f.length())
                    }
                }
            } else {
                val name = child.name ?: return@forEach
                out += ScannedEntry(name, isDirectory = false, sizeBytes = child.length())
            }
        }
        return out
    }

    /** Resolve a relative path (as in ScannedEntry/remoteId) to a readable content Uri. */
    fun uriOf(relativePath: String): Uri? {
        var node: DocumentFile = root ?: return null
        for (seg in relativePath.split('/')) {
            node = node.findFile(seg) ?: return null
        }
        return node.uri
    }
}
```

- [ ] **Step 3: Verify compile.**

Run: `./gradlew :source-local:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add source-local/src/main/kotlin/com/komgareader/source/local/LocalFileCache.kt source-local/src/main/kotlin/com/komgareader/source/local/LocalFolderScanner.kt
git commit -m "feat(source-local): SAF file cache + folder scanner (Android glue)"
```

---

## Task 7: `LocalSource` + `LocalSourceFactory`

**Files:**
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/LocalSource.kt`
- Create: `source-local/src/main/kotlin/com/komgareader/source/local/LocalSourceFactory.kt`

- [ ] **Step 1: Implement `LocalSource.kt`.**

Assembles scanner + mapper + cache + CBZ + parser into a `BrowsableSource`. The index is built lazily on first use and cached; a public `refresh()` rebuilds it (called by the on-start/manual scan). Reads `source-api/.../source/MediaSource.kt` to match signatures exactly (already pinned in the spec).
```kotlin
package com.komgareader.source.local

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalSource internal constructor(
    override val id: Long,
    override val name: String,
    private val scanner: LocalFolderScanner,
    private val cache: LocalFileCache,
    private val parser: LocalMetadataParser = LocalMetadataParser(),
) : BrowsableSource {

    override val kind: SourceKind = SourceKind.LOCAL

    private val indexLock = Mutex()
    @Volatile private var cached: LocalIndex? = null

    private suspend fun index(): LocalIndex = indexLock.withLock {
        cached ?: withContext(Dispatchers.IO) { LocalLibraryMapper().map(scanner.scan()) }
            .also { cached = it }
    }

    /** Rebuild the index (on-start / manual reload). */
    suspend fun refresh() = indexLock.withLock { cached = null }

    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> =
        PagedResult(index().series.map { it.toSeries() }, hasNextPage = false)

    override suspend fun search(query: String, page: Int): PagedResult<Series> =
        PagedResult(
            index().series.filter { it.title.contains(query, ignoreCase = true) }.map { it.toSeries() },
            hasNextPage = false,
        )

    override suspend fun books(seriesRemoteId: String): List<Book> =
        index().series(seriesRemoteId)?.books?.map { it.toBook(seriesRemoteId) }.orEmpty()

    override suspend fun seriesDetail(seriesRemoteId: String): Series? =
        index().series(seriesRemoteId)?.toSeries()

    override suspend fun pages(bookRemoteId: String): List<PageRef> {
        val book = index().book(bookRemoteId) ?: return emptyList()
        if (book.format != BookFormat.CBZ) return emptyList() // PDF/CBR/EPUB → whole-file render
        val cbz = cbzOf(bookRemoteId) ?: return emptyList()
        return (0 until cbz.pageCount()).map { i ->
            PageRef(index = i, bookRemoteId = bookRemoteId, pageNumber = i + 1, url = "")
        }
    }

    override suspend fun openPage(ref: PageRef): ByteArray {
        val cbz = cbzOf(ref.bookRemoteId)
            ?: throw UnsupportedOperationException("LocalSource streams CBZ pages only")
        return withContext(Dispatchers.IO) { cbz.pageBytes(ref.index) }
    }

    override suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        val file = materialize(bookRemoteId) ?: error("Local file not found: $bookRemoteId")
        file.readBytes().also { onProgress(it.size.toLong(), it.size.toLong()) }
    }

    override suspend fun seriesIdOf(bookRemoteId: String): String =
        index().series.firstOrNull { s -> s.books.any { it.remoteId == bookRemoteId } }?.remoteId
            ?: bookRemoteId

    override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray {
        // Series cover = its first book's cover; book cover = the book's first page.
        val bookId = if (isSeriesCover) index().series(remoteId)?.books?.firstOrNull()?.remoteId else remoteId
        bookId ?: return ByteArray(0)
        val cbz = cbzOf(bookId) ?: return ByteArray(0) // PDF/CBR/EPUB → placeholder (no renderer)
        return withContext(Dispatchers.IO) { cbz.coverBytes() }
    }

    // --- helpers ---

    private suspend fun materialize(bookRemoteId: String): java.io.File? {
        val uri = scanner.uriOf(bookRemoteId) ?: return null
        return withContext(Dispatchers.IO) { cache.materialize(uri, bookRemoteId) }
    }

    private suspend fun cbzOf(bookRemoteId: String): CbzArchive? {
        val book = index().book(bookRemoteId) ?: return null
        if (book.format != BookFormat.CBZ) return null
        val file = materialize(bookRemoteId) ?: return null
        return CbzArchive(file)
    }

    private fun LocalSeries.toSeries(): Series {
        // Enrich from the first CBZ's ComicInfo if present (best-effort, non-fatal).
        return Series(
            id = 0L,
            sourceId = this@LocalSource.id,
            remoteId = remoteId,
            title = title,
        )
    }

    private fun LocalBook.toBook(seriesRemoteId: String): Book = Book(
        id = 0L,
        sourceId = this@LocalSource.id,
        seriesId = 0L,
        remoteId = remoteId,
        title = title,
        format = format,
        pageCount = 0, // real count from the opened Document (reader) — CBZ count is via pages()
        seriesTitle = index().series(seriesRemoteId)?.title.orEmpty(),
        number = number,
        summary = summary,
    )
}
```
Note: ComicInfo enrichment of `Series.summary/genres`/`Book.number` requires reading the archive; keep it best-effort and **off the scan path** (only when a detail/book is requested). For V1 it is acceptable to leave `summary/genres` null on the listing and enrich in `books()`/`seriesDetail()` by reading the first CBZ's `comicInfoXml()` through `parser.parse(...)`. If enrichment complicates this task, ship the name-only mapping (Task 5) and add ComicInfo enrichment as a follow-up — the spec allows missing metadata (Seam A "fill what you can"). Prefer to wire enrichment here if cheap.

- [ ] **Step 2: Implement `LocalSourceFactory.kt`.**

```kotlin
package com.komgareader.source.local

import android.content.Context
import android.net.Uri
import com.komgareader.domain.source.SourceId

object LocalSourceFactory {
    /**
     * Builds a [LocalSource] over a SAF tree uri. Cheap — no scan happens here
     * (the index is built lazily on first browse), so it is safe to call from
     * SourceRegistration.build / sourceIdOf.
     */
    fun create(context: Context, name: String, rootTreeUri: String): LocalSource {
        val uri = Uri.parse(rootTreeUri)
        return LocalSource(
            id = SourceId.LOCAL, // reserved 0 — single local source in V1
            name = name,
            scanner = LocalFolderScanner(context.applicationContext, uri),
            cache = LocalFileCache(context.applicationContext),
        )
    }
}
```

- [ ] **Step 3: Verify compile.**

Run: `./gradlew :source-local:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add source-local/src/main/kotlin/com/komgareader/source/local/LocalSource.kt source-local/src/main/kotlin/com/komgareader/source/local/LocalSourceFactory.kt
git commit -m "feat(source-local): LocalSource BrowsableSource + factory"
```

---

## Task 8: Wire `SourceKind.LOCAL` into `SourceRegistration`

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/SourceRegistration.kt`
- Modify: `app/build.gradle.kts` (add `implementation(project(":source-local"))`)
- Modify (if needed): `app/.../di/AppModule.kt` — only if `@ApplicationContext` isn't already injectable into `SourceRegistration`.

- [ ] **Step 1: Add the module dependency.**

In `app/build.gradle.kts`, alongside `implementation(project(":source-opds"))`:
```kotlin
implementation(project(":source-local"))
```

- [ ] **Step 2: Inject Context + add the LOCAL branch.**

`SourceRegistration` is `@Singleton @Inject`. Add an `@ApplicationContext` Context param and the branch. (Hilt provides `@ApplicationContext` without an AppModule change; verify the import.)
```kotlin
import android.content.Context
import com.komgareader.source.local.LocalSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
// ...
@Singleton
class SourceRegistration @Inject constructor(
    private val sources: SourceManager,
    private val komgaProvider: KomgaSourceProvider,
    private val pluginHost: PluginHost,
    @ApplicationContext private val context: Context,
) {
    // ...
    private fun build(config: ServerConfig): BrowsableSource? = when (config.kind) {
        SourceKind.LOCAL -> LocalSourceFactory.create(context, config.name, config.baseUrl)
        SourceKind.OPDS -> OpdsSourceFactory.create(/* unchanged */ ... )
        SourceKind.PLUGIN -> { /* unchanged */ ... }
        else -> komgaProvider.from(config)
    }
```

- [ ] **Step 3: Verify the app compiles.**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If Hilt complains about `@ApplicationContext`, confirm `dagger.hilt.android.qualifiers.ApplicationContext` import and that the app uses Hilt (it does — other classes inject it; grep `@ApplicationContext` in `app/`).

- [ ] **Step 4: Commit.**

```bash
git add app/build.gradle.kts app/src/main/kotlin/com/komgareader/app/data/SourceRegistration.kt
git commit -m "feat(app): register LocalSource for SourceKind.LOCAL"
```

---

## Task 9: General reader fallback for non-streaming sources

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt` (the paged branch around lines 173–182)

**Why:** When `pages()` is empty and there is no local download (OPDS today; LocalSource PDF/CBR), read the whole file and render it via MuPDF instead of building an empty `Streamed` list. Source-agnostic.

- [ ] **Step 1: Replace the non-webtoon paged branch.**

Current (lines ~173–182):
```kotlin
val pages = source.pages(bookId)
val startPage = runCatching { (source as? SyncingSource)?.pullProgress(bookId) }
    .getOrNull()
    ?.let { progress -> (progress.page - 1).coerceIn(0, pages.size - 1) }
    ?: 0
_currentPage.value = startPage
_content.value = ReaderContent.Streamed(
    pages = pages.map { SourceImage(source.id, bookId, it.pageNumber) },
    initialPage = startPage,
)
```
Replace with:
```kotlin
val pages = source.pages(bookId)
if (pages.isEmpty()) {
    // Source cannot stream pages (OPDS, LocalSource PDF/CBR) → render whole file via MuPDF.
    val bytes = withContext(Dispatchers.IO) { source.downloadFile(bookId) }
    val ext = ".${format.name.lowercase()}"
    val doc = withContext(Dispatchers.IO) { documentFactory.open(bytes, ext) }
    document = doc
    val pageCount = withContext(Dispatchers.IO) { doc.pageCount() }
    val startPage = runCatching {
        (source as? SyncingSource)?.pullProgress(bookId)?.let { (it.page - 1).coerceIn(0, pageCount - 1) }
    }.getOrNull() ?: 0
    _currentPage.value = startPage
    _content.value = ReaderContent.Rendered(pageCount = pageCount, initialPage = startPage)
} else {
    val startPage = runCatching { (source as? SyncingSource)?.pullProgress(bookId) }
        .getOrNull()
        ?.let { progress -> (progress.page - 1).coerceIn(0, pages.size - 1) }
        ?: 0
    _currentPage.value = startPage
    _content.value = ReaderContent.Streamed(
        pages = pages.map { SourceImage(source.id, bookId, it.pageNumber) },
        initialPage = startPage,
    )
}
```
Confirm `format` is in scope (it is — used at line 137) and `documentFactory`/`document`/`localBookBytes` fields exist (they do — lines 148–149).

- [ ] **Step 2: Build + run the existing reader tests.**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "*Reader*"`
Expected: BUILD SUCCESSFUL; existing reader/webtoon planner tests still PASS.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt
git commit -m "feat(reader): render whole-file when source has no streamable pages"
```

---

## Task 10: Settings "Local folder" add-source segment

**Files:**
- Read first: `app/.../ui/settings/SettingsContent.kt`, `SettingsSections.kt`, `SettingsViewModel.kt` (to match the existing OPDS/Komga add-source flow and the segment control).
- Modify: `SettingsContent.kt` (add a "Local folder" choice + SAF launcher), `SettingsViewModel.kt` (a `saveLocalFolder(name, treeUri)`), app i18n de+en.

- [ ] **Step 1: Add i18n keys (de + en).**

Find the i18n source (grep for an existing settings key like `settingsAddServer`). Add, in both languages:
- `settingsAddLocalFolder` = "Lokaler Ordner" / "Local folder"
- `localFolderPickPrompt` = "Ordner mit Comics/Büchern wählen" / "Pick a folder of comics/books"
Keep compile-time parity (both maps).

- [ ] **Step 2: Add a SAF folder launcher in the add-source UI.**

In the add-source composable, register:
```kotlin
val pickFolder = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
) { uri: Uri? ->
    if (uri != null) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Local"
        viewModel.saveLocalFolder(name = name, treeUri = uri.toString())
    }
}
```
Wire a "Local folder" button/segment (next to Komga/OPDS) that calls `pickFolder.launch(null)`. Follow the existing segment pattern in `SettingsContent.kt`.

- [ ] **Step 3: Add `saveLocalFolder` to `SettingsViewModel`.**

Mirror the existing `saveServer`:
```kotlin
fun saveLocalFolder(name: String, treeUri: String) = viewModelScope.launch {
    servers.save(ServerConfig(name = name, baseUrl = treeUri, kind = SourceKind.LOCAL))
    coordinator.onServerChanged()
}
```

- [ ] **Step 4: Build.**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/ app/src/main/<i18n paths>
git commit -m "feat(settings): add a local folder as a source (SAF, persisted permission)"
```

---

## Task 11: Release the persisted URI permission on removal

**Files:**
- Modify: `app/.../ui/settings/SettingsViewModel.kt` (`removeServer`)

- [ ] **Step 1: Release the permission for LOCAL configs.**

In `removeServer`, after resolving the config and before/after `servers.remove(id)`:
```kotlin
val cfg = servers.configs.first().firstOrNull { it.id == id }
if (cfg?.kind == SourceKind.LOCAL) {
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(cfg.baseUrl), Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
servers.remove(id)
cfg?.let { registration.sourceIdOf(it) }?.let { collections.removeSource(it) }
```
Inject `@ApplicationContext Context` into `SettingsViewModel` if not already present (grep first).

- [ ] **Step 2: Build.**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt
git commit -m "feat(settings): release SAF permission when removing a local folder"
```

---

## Task 12: E2E on the emulator (`eink_test`)

**Goal:** Prove the whole chain on a device, mixed with a live source.

- [ ] **Step 1: Push a test folder to the emulator.**

Build a fixture: a folder `LocalTest/` with `Berserk/` (two small CBZ, each a zip of a few jpgs, one carrying a `ComicInfo.xml`), a loose `oneshot.pdf`, and an `book.epub`. Push it to shared storage:
```bash
adb -s emulator-5554 push /tmp/LocalTest /sdcard/Download/LocalTest
```

- [ ] **Step 2: Build, install, launch.**

Run: `./gradlew :app:installDebug && adb shell am start -n com.komgareader.app/.MainActivity`

- [ ] **Step 3: Add the local folder via Settings → add source → Local folder.**

Pick `/sdcard/Download/LocalTest` in the SAF dialog. Verify (screenshot):
- Library shows the Berserk series (2 volumes) + oneshot + the EPUB, mixed with the live Komga/OPDS source.
- CBZ series shows a real cover; PDF/EPUB show the placeholder.

- [ ] **Step 4: Read each format.**

- Open a CBZ → PAGED reader, swipe pages (raw zip-extract via `openPage`).
- Open the PDF → PAGED reader renders (whole-file MuPDF fallback).
- Open the EPUB → NOVEL reader opens (whole-file crengine).
Capture screenshots of each.

- [ ] **Step 5: Persistence + restart.**

Read a few pages, kill and relaunch the app. Verify the local folder is still present (persisted URI permission) and reading position is restored. Verify removing the local folder in Settings makes it disappear and (re-add) works again.

- [ ] **Step 6: Record evidence + commit any fixups.**

Save screenshots under `docs/superpowers/evidence/` (or attach in the PR). Commit any fixes found during E2E with clear messages.

---

## Task 13: Documentation + memory (same-branch, `docs-match-code`)

**Files:**
- Modify: `.claude/rules/architecture-seams.md` (Seam A — LocalSource now real, single-folder, renderer-free, reader whole-file fallback)
- Modify: `.claude/rules/source-extensibility.md` (recipe B — point to `source-local` as a second worked example after OPDS, note the Android-library + SAF specifics)
- Modify: `CLAUDE.md` (module table — add `source-local` row; note the `SourceKind.LOCAL` wiring)
- Modify: memory `MEMORY.md` + a new `source-local.md` note (what was non-obvious: renderer-free source, CBZ-streams/PDF-renders split, the general reader fallback, single-folder id 0)

- [ ] **Step 1: Use the komga-doc-sync skill.**

Invoke the `komga-doc-sync` skill and follow it to update the rules/CLAUDE.md precisely (Soll vs. Ist, no phantom types).

- [ ] **Step 2: Write the memory note.**

Create `…/memory/source-local.md` (type: project) capturing the non-obvious decisions and link `[[architecture-source-agnostic-debt]]`, `[[project-komga-eink-reader]]`. Add a one-line pointer in `MEMORY.md`.

- [ ] **Step 3: Commit.**

```bash
git add .claude/rules/ CLAUDE.md
git commit -m "docs: LocalSource is now real (Seam A) — rules + module table"
```

---

## Self-Review notes

- **Spec coverage:** module (T1), folder mapping (T5), metadata/ComicInfo (T3), reading model CBZ-stream/whole-file (T4,T7,T9), SAF pick + persistence (T6,T10,T11), ViewerType (unchanged — relies on existing `ResolveViewerType`; EPUB→NOVEL, else PAGED, user override via TypeMenu), progress (unchanged — local store), tests (T2–T5 pure, T12 E2E), CBR gate (T0), docs (T13). All covered.
- **Type consistency:** `ScannedEntry`/`LocalIndex`/`LocalSeries`/`LocalBook` defined in T1, used T5/T7. `formatOf`/`titleOf`/`isImageEntry`/`naturalOrder` defined T2, used T4/T5/T7. `CbzArchive.pageCount/pageBytes/coverBytes/comicInfoXml` defined T4, used T7. `LocalSource.refresh()` exposed for the scan trigger.
- **Open verification during execution:** exact Android-library SDK/plugin values (copy from `data/build.gradle.kts`); the add-source segment shape in `SettingsContent.kt`; whether `SettingsViewModel`/`SourceRegistration` already inject `@ApplicationContext`. Each task says "read first".
- **Scan trigger:** `LocalSource.refresh()` should be called from the existing on-start/manual scan path. Verify whether `SyncCoordinator.onAppStart`/`onManualReload` can reach registered sources to call `refresh()`; if not trivial, the lazy index already rebuilds on process restart, and a manual reload re-registers — acceptable for V1. Note this in T13 docs if `refresh()` is not yet wired to a button.
