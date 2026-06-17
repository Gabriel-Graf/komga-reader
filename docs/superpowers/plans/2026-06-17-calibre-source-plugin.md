# Calibre Source Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Komga-Reader source plugin connecting to a Calibre **Content Server** via its `/ajax/` JSON API, grouping the library into Series→Volumes by Calibre's `series` metadata, read-only.

**Architecture:** A new Gradle module `komga-calibre-source` inside the **`KomgaReaderPlugins` monorepo** (first code plugin there; siblings are data-only). It mirrors the existing `komga-kavita-source` plugin: a `SourcePlugin` entry builds a `BrowsableSource` over Retrofit/OkHttp + kotlinx.serialization. Pure helpers (remote-id codec, mapper, pagination) are JVM-unit-tested; the source is contract-tested with MockWebServer and live-verified against a Dockerized Calibre Content Server. Zero edits to the host reader app.

**Tech Stack:** Kotlin, Android (`com.android.application`), Retrofit 2 + OkHttp 4 + retrofit-kotlinx-serialization-converter, kotlinx.serialization, JUnit4 + MockWebServer. Contract type comes from `com.komgareader:plugin-sdk:0.1.0` (`compileOnly`).

**Repo root for all paths below:** `/home/gabriel/Documents/Projekte/KomgaReaderPlugins`
**Reference plugin (read-only, do not modify):** `/home/gabriel/Documents/Projekte/komga-reader/plugin/komga-kavita-source`
**Spec:** `/home/gabriel/Documents/Projekte/komga-reader/docs/superpowers/specs/2026-06-17-calibre-source-plugin-design.md`

## Global Constraints

- `plugin-sdk:0.1.0` is **`compileOnly`** in `main` (also `testImplementation` so JVM tests can see the contract types). NEVER `implementation` — duplicate contract classes in the APK cause `ClassCastException` in the host classloader.
- `packageName` / `applicationId`: `com.komgareader.plugin.calibre`. Entry class: `com.komgareader.plugin.calibre.CalibreSourcePlugin` (public no-arg constructor).
- ABI: manifest `com.komgareader.plugin.ABI_VERSION = 1`.
- compileSdk 34, minSdk 28, targetSdk 34, JVM target 17, versionCode 1, versionName "0.1.0".
- remoteIds MUST be opaque (no `/`) — Base64URL-encoded (the app threads them as single nav-path segments).
- Unit tests run on the JVM (`unitTests.isReturnDefaultValues = true`) → use `java.util.Base64`, NEVER `android.util.Base64` (returns stub 0/null on JVM).
- Code comments + KDoc + README + commit messages in **English**.
- Backend is the Calibre **Content Server** (`/ajax/` API), NOT calibre-web.
- Read-only: implement `BrowsableSource` only, NOT `SyncingSource`. README must state the sync gap.
- Calibre book metadata keys (verified from `calibre/src/calibre/srv/ajax.py`): `title`, `authors`, `series`, `series_index`, `formats`, `format_metadata`, `main_format`, `other_formats`, `cover`, `comments`. Endpoints: `/ajax/library-info`, `/ajax/categories/{lib}`, `/ajax/category/{encoded}/{lib}`, `/ajax/search/{lib}`, `/ajax/books/{lib}?ids=`, `/ajax/book/{id}/{lib}`. Raw bytes: `/get/cover/{id}/{lib}`, `/get/{FMT}/{id}/{lib}`.

---

### Task 1: Module scaffold + monorepo wiring

**Files:**
- Create: `komga-calibre-source/build.gradle.kts`
- Create: `komga-calibre-source/src/main/AndroidManifest.xml`
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreSourcePlugin.kt`
- Modify: `settings.gradle.kts` (add module to `include(...)`, add `mavenLocal()`)

**Interfaces:**
- Produces: `CalibreSourcePlugin : SourcePlugin` with `metadata`, `configSchema()`, and a stub `create()` (real wiring in Task 7).

- [ ] **Step 1: Add `mavenLocal()` + module include to `settings.gradle.kts`**

In `dependencyResolutionManagement.repositories { ... }`, add `mavenLocal()` as the **first** entry (above `google()`). Append `:komga-calibre-source` to the `include(...)` list:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
rootProject.name = "KomgaReaderPlugins"
include(":komga-lang-es", ":komga-lang-fr", ":komga-lang-it", ":komga-reader-preset-eink", ":komga-panel-model-yolo", ":komga-font-ebgaramond", ":komga-font-lora", ":komga-font-merriweather", ":komga-font-sourceserif", ":komga-font-atkinson", ":komga-calibre-source")
```

- [ ] **Step 2: Create `komga-calibre-source/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.komgareader.plugin.calibre"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.komgareader.plugin.calibre"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    // Plugin contract — host supplies it at runtime, so compileOnly (never implementation).
    compileOnly("com.komgareader:plugin-sdk:0.1.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Contract types for JVM unit tests (host does not supply them off-device).
    testImplementation("com.komgareader:plugin-sdk:0.1.0")
}
```

- [ ] **Step 3: Create `komga-calibre-source/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:label="Calibre Source Plugin"
        android:allowBackup="false">

        <!-- Host discovers this APK via PackageManager using these meta-data entries. -->
        <meta-data
            android:name="com.komgareader.plugin.SOURCE"
            android:value="com.komgareader.plugin.calibre.CalibreSourcePlugin" />

        <meta-data
            android:name="com.komgareader.plugin.ABI_VERSION"
            android:value="1" />

    </application>
</manifest>
```

- [ ] **Step 4: Create stub `CalibreSourcePlugin.kt`**

```kotlin
package com.komgareader.plugin.calibre

import com.komgareader.domain.source.BrowsableSource
import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType
import com.komgareader.plugin.PluginMetadata
import com.komgareader.plugin.SourcePlugin

/**
 * Entry point of the Calibre source plugin. The host instantiates this via reflection
 * (`getDeclaredConstructor().newInstance()`), so a public no-arg constructor is required.
 *
 * config keys: "url" (Content Server base URL), "username"/"password" (optional Basic-Auth),
 * "library" (optional; blank → server's default_library).
 */
class CalibreSourcePlugin : SourcePlugin {

    override val metadata: PluginMetadata = PluginMetadata(displayName = "Calibre")

    override fun configSchema(): ConfigSchema = ConfigSchema(
        fields = listOf(
            ConfigField(key = "url", label = "Server-URL", type = FieldType.URL, required = true, default = ""),
            ConfigField(key = "username", label = "Benutzername", type = FieldType.TEXT, required = false, default = ""),
            ConfigField(key = "password", label = "Passwort", type = FieldType.SECRET, required = false, default = ""),
            ConfigField(key = "library", label = "Bibliothek", type = FieldType.TEXT, required = false, default = ""),
        ),
    )

    override fun create(config: Map<String, String>): BrowsableSource {
        TODO("wired in Task 7")
    }
}
```

- [ ] **Step 5: Verify it builds**

Run: `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && ./gradlew :komga-calibre-source:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `plugin-sdk:0.1.0` is not found, run the host's publish: `cd /home/gabriel/Documents/Projekte/komga-reader && ./gradlew :plugin-sdk:publishToMavenLocal`, then retry.)

- [ ] **Step 6: Commit**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins
git add settings.gradle.kts komga-calibre-source/build.gradle.kts komga-calibre-source/src/main/AndroidManifest.xml komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreSourcePlugin.kt
git commit -m "feat(calibre): scaffold plugin module + monorepo wiring"
```

---

### Task 2: CalibreRemoteId (pure codec)

**Files:**
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreRemoteId.kt`
- Test: `komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreRemoteIdTest.kt`

**Interfaces:**
- Produces: `object CalibreRemoteId { fun forSeries(name: String): String; fun forBook(bookId: String): String; fun decode(remoteId: String): Parsed }` and `sealed interface Parsed { data class Series(val name: String); data class Book(val id: String) }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.plugin.calibre

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibreRemoteIdTest {

    @Test fun `series round-trips a name with slash and quotes`() {
        val name = "Berserk / \"Deluxe\""
        val rid = CalibreRemoteId.forSeries(name)
        assertEquals(false, rid.contains('/'))
        assertEquals(CalibreRemoteId.Parsed.Series(name), CalibreRemoteId.decode(rid))
    }

    @Test fun `book round-trips an id`() {
        val rid = CalibreRemoteId.forBook("123")
        assertEquals(false, rid.contains('/'))
        assertEquals(CalibreRemoteId.Parsed.Book("123"), CalibreRemoteId.decode(rid))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreRemoteIdTest*"`
Expected: FAIL (unresolved reference `CalibreRemoteId`).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.komgareader.plugin.calibre

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Opaque, Base64URL-encoded remote-ids. The app threads remoteIds as single nav-path
 * segments, so they must not contain '/'. A tag byte distinguishes series from standalone book.
 */
object CalibreRemoteId {

    sealed interface Parsed {
        data class Series(val name: String) : Parsed
        data class Book(val id: String) : Parsed
    }

    fun forSeries(name: String): String = encode("S", name)
    fun forBook(bookId: String): String = encode("B", bookId)

    fun decode(remoteId: String): Parsed {
        val raw = String(Base64.getUrlDecoder().decode(remoteId), StandardCharsets.UTF_8)
        val tag = raw.substring(0, 1)
        val value = raw.substring(2) // skip "X "
        return when (tag) {
            "S" -> Parsed.Series(value)
            else -> Parsed.Book(value)
        }
    }

    private fun encode(tag: String, value: String): String {
        val raw = "$tag $value".toByteArray(StandardCharsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreRemoteIdTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreRemoteId.kt komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreRemoteIdTest.kt
git commit -m "feat(calibre): opaque Base64URL remote-id codec"
```

---

### Task 3: CalibreDtos + CalibreMapper (pure)

**Files:**
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/api/CalibreDtos.kt`
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreMapper.kt`
- Test: `komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreMapperTest.kt`

**Interfaces:**
- Consumes: `CalibreRemoteId` (Task 2).
- Produces:
  - DTOs: `LibraryInfoDto(library_map, default_library)`, `CategoryDto(name, url)`, `CategoryItemsDto(total_num, items)`, `CategoryItemDto(name, count)`, `SearchDto(total_num, book_ids)`, `CalibreBookDto(title, authors, series, series_index, formats, main_format, comments)`.
  - `object CalibreMapper { fun pickFormat(formats: List<String>?): BookFormat?; fun toBook(dto, bookId, sourceId, seriesTitle): Book?; fun standaloneSeries(dto, bookId, sourceId): Series; fun seriesTile(name, sourceId): Series; fun groupSearch(books, sourceId): List<Series> }`.

- [ ] **Step 1: Create the DTOs**

```kotlin
package com.komgareader.plugin.calibre.api

import kotlinx.serialization.Serializable

@Serializable
data class LibraryInfoDto(
    val library_map: Map<String, String> = emptyMap(),
    val default_library: String = "",
)

@Serializable
data class CategoryDto(
    val name: String = "",
    val url: String = "",
)

@Serializable
data class CategoryItemsDto(
    val total_num: Int = 0,
    val items: List<CategoryItemDto> = emptyList(),
)

@Serializable
data class CategoryItemDto(
    val name: String = "",
    val count: Int = 0,
)

@Serializable
data class SearchDto(
    val total_num: Int = 0,
    val book_ids: List<Int> = emptyList(),
)

@Serializable
data class CalibreBookDto(
    val title: String = "",
    val authors: List<String> = emptyList(),
    val series: String? = null,
    val series_index: Double? = null,
    val formats: List<String> = emptyList(),
    val main_format: Map<String, String> = emptyMap(),
    val comments: String? = null,
)
```

- [ ] **Step 2: Write the failing mapper test**

```kotlin
package com.komgareader.plugin.calibre

import com.komgareader.domain.model.BookFormat
import com.komgareader.plugin.calibre.api.CalibreBookDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibreMapperTest {

    @Test fun `pickFormat prefers EPUB then PDF then CBZ then CBR`() {
        assertEquals(BookFormat.EPUB, CalibreMapper.pickFormat(listOf("MOBI", "PDF", "EPUB")))
        assertEquals(BookFormat.PDF, CalibreMapper.pickFormat(listOf("MOBI", "PDF")))
        assertEquals(BookFormat.CBZ, CalibreMapper.pickFormat(listOf("CBZ", "CBR")))
        assertNull(CalibreMapper.pickFormat(listOf("MOBI", "AZW3")))
        assertNull(CalibreMapper.pickFormat(emptyList()))
    }

    @Test fun `toBook maps title, format, number and skips unreadable`() {
        val dto = CalibreBookDto(title = "Vol 1", series = "X", series_index = 1.0, formats = listOf("EPUB"))
        val book = CalibreMapper.toBook(dto, bookId = "7", sourceId = 99L, seriesTitle = "X")!!
        assertEquals("Vol 1", book.title)
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("7", book.remoteId)
        assertEquals("1", book.number)
        assertEquals(0, book.pageCount)
        assertNull(CalibreMapper.toBook(dto.copy(formats = listOf("MOBI")), "7", 99L, "X"))
    }

    @Test fun `groupSearch collapses series and keeps standalone separate`() {
        val books = mapOf(
            "1" to CalibreBookDto(title = "A1", series = "Saga", series_index = 1.0, formats = listOf("EPUB")),
            "2" to CalibreBookDto(title = "A2", series = "Saga", series_index = 2.0, formats = listOf("EPUB")),
            "3" to CalibreBookDto(title = "Solo", series = null, formats = listOf("PDF")),
        )
        val series = CalibreMapper.groupSearch(books, sourceId = 99L)
        assertEquals(2, series.size)
        assertTrue(series.any { it.title == "Saga" && it.remoteId == CalibreRemoteId.forSeries("Saga") })
        assertTrue(series.any { it.title == "Solo" && it.remoteId == CalibreRemoteId.forBook("3") })
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreMapperTest*"`
Expected: FAIL (unresolved reference `CalibreMapper`).

- [ ] **Step 4: Write the mapper**

```kotlin
package com.komgareader.plugin.calibre

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.Series
import com.komgareader.plugin.calibre.api.CalibreBookDto

/** Pure DTO → domain mapping. */
object CalibreMapper {

    /** Readable formats in descending preference. */
    private val FORMAT_PRIORITY = listOf(
        "EPUB" to BookFormat.EPUB,
        "PDF" to BookFormat.PDF,
        "CBZ" to BookFormat.CBZ,
        "CBR" to BookFormat.CBR,
    )

    fun pickFormat(formats: List<String>?): BookFormat? {
        val upper = formats?.map { it.uppercase() }?.toSet() ?: return null
        return FORMAT_PRIORITY.firstOrNull { it.first in upper }?.second
    }

    /** A standalone book (no Calibre series) → its own one-volume Series. */
    fun standaloneSeries(dto: CalibreBookDto, bookId: String, sourceId: Long): Series = Series(
        id = 0L,
        sourceId = sourceId,
        remoteId = CalibreRemoteId.forBook(bookId),
        title = dto.title.ifBlank { bookId },
        summary = dto.comments?.ifBlank { null },
    )

    /** A Calibre series → a Series tile (volumes resolved later via [books]). */
    fun seriesTile(name: String, sourceId: Long): Series = Series(
        id = 0L,
        sourceId = sourceId,
        remoteId = CalibreRemoteId.forSeries(name),
        title = name,
    )

    /** Maps a book DTO to a domain Book; returns null when no readable format exists. */
    fun toBook(dto: CalibreBookDto, bookId: String, sourceId: Long, seriesTitle: String): Book? {
        val format = pickFormat(dto.formats) ?: return null
        return Book(
            id = 0L,
            sourceId = sourceId,
            seriesId = 0L,
            remoteId = bookId,
            title = dto.title.ifBlank { bookId },
            format = format,
            pageCount = 0,
            seriesTitle = seriesTitle,
            summary = dto.comments?.ifBlank { null },
            number = dto.series_index?.let { formatIndex(it) },
        )
    }

    /** Groups a fetched book map into Series tiles (series collapsed, standalone separate). */
    fun groupSearch(books: Map<String, CalibreBookDto>, sourceId: Long): List<Series> {
        val out = mutableListOf<Series>()
        val seenSeries = mutableSetOf<String>()
        // Preserve insertion order for determinism.
        for ((bookId, dto) in books) {
            val series = dto.series?.ifBlank { null }
            if (series != null) {
                if (seenSeries.add(series)) out.add(seriesTile(series, sourceId))
            } else {
                out.add(standaloneSeries(dto, bookId, sourceId))
            }
        }
        return out
    }

    /** "1.0" → "1", "1.5" → "1.5". */
    private fun formatIndex(index: Double): String =
        if (index % 1.0 == 0.0) index.toLong().toString() else index.toString()
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreMapperTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/api/CalibreDtos.kt komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreMapper.kt komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreMapperTest.kt
git commit -m "feat(calibre): DTOs + pure DTO->domain mapper"
```

---

### Task 4: BrowsePaging (pure)

**Files:**
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/BrowsePaging.kt`
- Test: `komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/BrowsePagingTest.kt`

**Interfaces:**
- Produces: `object BrowsePaging { fun slice(page: Int, pageSize: Int, seriesTotal: Int): Slice; fun hasNext(page: Int, pageSize: Int, seriesTotal: Int, standaloneTotal: Int): Boolean }` and `data class Slice(val phase: Phase, val offset: Int)`, `enum class Phase { SERIES, STANDALONE }`.

**Note on design:** browse pages over series first (Calibre's series category), then over standalone books (`search?query=series:false`). `page` is 1-based. The source supplies `seriesTotal` (category `total_num`) and `standaloneTotal` (search `total_num`); this helper does all offset/boundary arithmetic.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.plugin.calibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsePagingTest {

    @Test fun `pages within series range use SERIES phase`() {
        // seriesTotal=5, pageSize=2 -> series pages 1..3
        assertEquals(BrowsePaging.Slice(BrowsePaging.Phase.SERIES, 0), BrowsePaging.slice(1, 2, 5))
        assertEquals(BrowsePaging.Slice(BrowsePaging.Phase.SERIES, 4), BrowsePaging.slice(3, 2, 5))
    }

    @Test fun `pages past series range use STANDALONE phase with reset offset`() {
        // series pages 1..3 (seriesTotal=5,size=2); page 4 -> standalone offset 0; page 5 -> offset 2
        assertEquals(BrowsePaging.Slice(BrowsePaging.Phase.STANDALONE, 0), BrowsePaging.slice(4, 2, 5))
        assertEquals(BrowsePaging.Slice(BrowsePaging.Phase.STANDALONE, 2), BrowsePaging.slice(5, 2, 5))
    }

    @Test fun `hasNext spans the series-to-standalone boundary`() {
        // last series page (3) still has standalone -> true
        assertTrue(BrowsePaging.hasNext(page = 3, pageSize = 2, seriesTotal = 5, standaloneTotal = 3))
        // last standalone page (5): offset 2 + page 1 item == 3 -> no more
        assertFalse(BrowsePaging.hasNext(page = 5, pageSize = 2, seriesTotal = 5, standaloneTotal = 3))
        // no standalone at all, last series page -> false
        assertFalse(BrowsePaging.hasNext(page = 3, pageSize = 2, seriesTotal = 5, standaloneTotal = 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*BrowsePagingTest*"`
Expected: FAIL (unresolved reference `BrowsePaging`).

- [ ] **Step 3: Write the implementation**

```kotlin
package com.komgareader.plugin.calibre

/**
 * Browse paginates Calibre series first (the built-in "series" category), then standalone
 * books (search query "series:false"). All offset/boundary math lives here, pure & testable.
 * [page] is 1-based.
 */
object BrowsePaging {

    enum class Phase { SERIES, STANDALONE }
    data class Slice(val phase: Phase, val offset: Int)

    fun seriesPages(seriesTotal: Int, pageSize: Int): Int =
        if (seriesTotal <= 0) 0 else (seriesTotal + pageSize - 1) / pageSize

    fun slice(page: Int, pageSize: Int, seriesTotal: Int): Slice {
        val sPages = seriesPages(seriesTotal, pageSize)
        return if (page <= sPages) {
            Slice(Phase.SERIES, (page - 1) * pageSize)
        } else {
            Slice(Phase.STANDALONE, (page - 1 - sPages) * pageSize)
        }
    }

    fun hasNext(page: Int, pageSize: Int, seriesTotal: Int, standaloneTotal: Int): Boolean {
        val sPages = seriesPages(seriesTotal, pageSize)
        val stdPages = if (standaloneTotal <= 0) 0 else (standaloneTotal + pageSize - 1) / pageSize
        return page < sPages + stdPages
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*BrowsePagingTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/BrowsePaging.kt komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/BrowsePagingTest.kt
git commit -m "feat(calibre): pure series->standalone browse pagination"
```

---

### Task 5: CalibreApi + auth interceptor + client builder

**Files:**
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/api/CalibreApi.kt`
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/client/CalibreAuthInterceptor.kt`
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/client/CalibreClient.kt`
- Test: `komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreAuthInterceptorTest.kt`

**Interfaces:**
- Consumes: DTOs (Task 3).
- Produces:
  - `interface CalibreApi` with `libraryInfo()`, `categories(library)`, `category(encoded, library, num, offset)`, `search(library, query, num, offset)`, `books(library, ids)`, `book(bookId, library)`, `raw(path)`.
  - `fun buildCalibreClient(baseUrl: String, username: String?, password: String?, debug: Boolean = false): CalibreApi`.

- [ ] **Step 1: Create `CalibreApi.kt`**

```kotlin
package com.komgareader.plugin.calibre.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit interface for the Calibre Content Server /ajax/ API.
 * `library` is the resolved library id (e.g. "Calibre_Library"). `raw` fetches /get/ byte routes.
 */
interface CalibreApi {

    @GET("ajax/library-info")
    suspend fun libraryInfo(): LibraryInfoDto

    @GET("ajax/categories/{library}")
    suspend fun categories(@Path("library") library: String): List<CategoryDto>

    @GET("ajax/category/{encoded}/{library}")
    suspend fun category(
        @Path("encoded", encoded = true) encoded: String,
        @Path("library") library: String,
        @Query("num") num: Int,
        @Query("offset") offset: Int,
        @Query("sort") sort: String = "name",
    ): CategoryItemsDto

    @GET("ajax/search/{library}")
    suspend fun search(
        @Path("library") library: String,
        @Query("query") query: String,
        @Query("num") num: Int,
        @Query("offset") offset: Int,
    ): SearchDto

    @GET("ajax/books/{library}")
    suspend fun books(
        @Path("library") library: String,
        @Query("ids") ids: String,
    ): Map<String, CalibreBookDto?>

    @GET("ajax/book/{bookId}/{library}")
    suspend fun book(
        @Path("bookId") bookId: String,
        @Path("library") library: String,
    ): CalibreBookDto

    /** Raw bytes for /get/ routes (cover, format download). [path] is relative to baseUrl. */
    @Streaming
    @GET
    suspend fun raw(@Url path: String): ResponseBody
}
```

- [ ] **Step 2: Write the failing interceptor test**

```kotlin
package com.komgareader.plugin.calibre

import com.komgareader.plugin.calibre.client.CalibreAuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibreAuthInterceptorTest {

    @Test fun `adds Basic header when username present`() {
        val server = MockWebServer().apply { start(); enqueue(MockResponse().setResponseCode(200)) }
        val client = OkHttpClient.Builder()
            .addInterceptor(CalibreAuthInterceptor(username = "alice", password = "pw"))
            .build()
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        val recorded = server.takeRequest()
        // base64("alice:pw") == "YWxpY2U6cHc="
        assertEquals("Basic YWxpY2U6cHc=", recorded.getHeader("Authorization"))
        server.shutdown()
    }

    @Test fun `no header when username blank`() {
        val server = MockWebServer().apply { start(); enqueue(MockResponse().setResponseCode(200)) }
        val client = OkHttpClient.Builder()
            .addInterceptor(CalibreAuthInterceptor(username = "", password = ""))
            .build()
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
        server.shutdown()
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreAuthInterceptorTest*"`
Expected: FAIL (unresolved reference `CalibreAuthInterceptor`).

- [ ] **Step 4: Write the interceptor**

```kotlin
package com.komgareader.plugin.calibre.client

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds HTTP Basic auth when a username is configured (Calibre Content Server run with
 * --enable-auth). With a blank username the request passes through unchanged.
 */
internal class CalibreAuthInterceptor(
    private val username: String,
    private val password: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authed = if (username.isNotBlank()) {
            request.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
        } else {
            request
        }
        return chain.proceed(authed)
    }
}
```

- [ ] **Step 5: Write the client builder `CalibreClient.kt`**

```kotlin
package com.komgareader.plugin.calibre.client

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.komgareader.plugin.calibre.api.CalibreApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Builds the Calibre Retrofit client.
 * @param baseUrl must end with "/".
 * @param username/@param password optional Basic-Auth (blank username = no auth header).
 */
fun buildCalibreClient(
    baseUrl: String,
    username: String?,
    password: String?,
    debug: Boolean = false,
): CalibreApi {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val okHttpClient = OkHttpClient.Builder()
        .apply {
            if (debug) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .addInterceptor(CalibreAuthInterceptor(username = username.orEmpty(), password = password.orEmpty()))
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CalibreApi::class.java)
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreAuthInterceptorTest*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/api/CalibreApi.kt komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/client/
git add komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreAuthInterceptorTest.kt
git commit -m "feat(calibre): Retrofit API + optional Basic-Auth client"
```

---

### Task 6: CalibreSource (BrowsableSource) + contract test

**Files:**
- Create: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreSource.kt`
- Test: `komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreSourceContractTest.kt`

**Interfaces:**
- Consumes: `CalibreApi`, `buildCalibreClient` (Task 5), `CalibreMapper`, `CalibreRemoteId`, `BrowsePaging`.
- Produces: `class CalibreSource(api: CalibreApi, baseUrl: String, library: String, override val name: String) : BrowsableSource`.

**Behaviour notes:**
- `id = SourceId.of(name, SourceKind.PLUGIN, baseUrl)`, `kind = SourceKind.PLUGIN`.
- `PAGE_SIZE = 50`.
- The series category url from `/ajax/categories` looks like `/ajax/category/<encoded>` (occasionally with a trailing `/<lib>`). Extract the `<encoded>` segment after `/ajax/category/`, stripping any trailing library segment. Cache it (`@Volatile`).
- Cache `seriesTotal`/`standaloneTotal` per browse call (recomputed each call; just locals).
- Series cover resolves the lowest-`series_index` volume of the series, then its `/get/cover`.

- [ ] **Step 1: Write the failing contract test**

```kotlin
package com.komgareader.plugin.calibre

import com.komgareader.domain.source.SourceFilter
import com.komgareader.plugin.calibre.client.buildCalibreClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalibreSourceContractTest {

    private lateinit var server: MockWebServer
    private lateinit var source: CalibreSource

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val path = req.path ?: ""
                return when {
                    path.startsWith("/ajax/categories/") -> json(
                        """[{"name":"Series","url":"/ajax/category/c2VyaWVz"},{"name":"Authors","url":"/ajax/category/YXV0aA=="}]"""
                    )
                    path.startsWith("/ajax/category/") -> json(
                        """{"total_num":1,"items":[{"name":"Saga","count":2}]}"""
                    )
                    // standalone count probe + page (query=series:false)
                    path.startsWith("/ajax/search/") && path.contains("series%3Afalse") -> json(
                        """{"total_num":1,"book_ids":[9]}"""
                    )
                    // books(series) resolution: query=series:"Saga"
                    path.startsWith("/ajax/search/") -> json(
                        """{"total_num":2,"book_ids":[1,2]}"""
                    )
                    path.startsWith("/ajax/books/") -> json(
                        """{"1":{"title":"Saga 1","series":"Saga","series_index":1.0,"formats":["EPUB"]},
                            "2":{"title":"Saga 2","series":"Saga","series_index":2.0,"formats":["EPUB"]},
                            "9":{"title":"Solo","formats":["PDF"]}}"""
                    )
                    path.startsWith("/ajax/book/") -> json(
                        """{"title":"Solo","formats":["PDF"]}"""
                    )
                    path.startsWith("/get/") -> MockResponse().setResponseCode(200).setBody("BYTES")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        source = CalibreSource(
            api = buildCalibreClient(server.url("/").toString(), username = null, password = null),
            baseUrl = server.url("/").toString().trimEnd('/'),
            library = "Calibre_Library",
            name = "Test-Calibre",
        )
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(body)

    @Test fun `browse returns series tiles then standalone`() = runBlocking {
        val page1 = source.browse(1, SourceFilter())
        assertTrue(page1.items.any { it.title == "Saga" })
        // seriesTotal=1, pageSize=50 -> 1 series page; standaloneTotal=1 -> page 2 has the standalone
        assertTrue(page1.hasNextPage)
        val page2 = source.browse(2, SourceFilter())
        assertTrue(page2.items.any { it.title == "Solo" })
    }

    @Test fun `books resolves a series sorted by index`() = runBlocking {
        val rid = CalibreRemoteId.forSeries("Saga")
        val books = source.books(rid)
        assertEquals(listOf("Saga 1", "Saga 2"), books.map { it.title })
    }

    @Test fun `pages is empty (whole-file read)`() = runBlocking {
        assertTrue(source.pages("1").isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreSourceContractTest*"`
Expected: FAIL (unresolved reference `CalibreSource`).

- [ ] **Step 3: Write `CalibreSource.kt`**

```kotlin
package com.komgareader.plugin.calibre

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceId
import com.komgareader.plugin.calibre.api.CalibreApi
import com.komgareader.plugin.calibre.api.CalibreBookDto

/**
 * [BrowsableSource] over the Calibre Content Server /ajax/ API. Read-only (no progress sync).
 * Groups books into Series→volumes by Calibre's `series` metadata (series-name join key).
 */
class CalibreSource(
    private val api: CalibreApi,
    private val baseUrl: String,
    private val library: String,
    override val name: String,
) : BrowsableSource {

    override val id: Long = SourceId.of(name, SourceKind.PLUGIN, baseUrl)
    override val kind: SourceKind = SourceKind.PLUGIN

    @Volatile private var seriesCategoryEncoded: String? = null

    private companion object {
        const val PAGE_SIZE = 50
        const val STANDALONE_QUERY = "series:false"
    }

    // ----------------------------------------------------------------- browse
    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> {
        val encoded = resolveSeriesCategory()
        val seriesItems = api.category(encoded, library, num = 0, offset = 0)
        val seriesTotal = seriesItems.total_num
        val standaloneTotal = api.search(library, STANDALONE_QUERY, num = 0, offset = 0).total_num

        val slice = BrowsePaging.slice(page, PAGE_SIZE, seriesTotal)
        val items: List<Series> = when (slice.phase) {
            BrowsePaging.Phase.SERIES -> {
                api.category(encoded, library, num = PAGE_SIZE, offset = slice.offset)
                    .items.map { CalibreMapper.seriesTile(it.name, id) }
            }
            BrowsePaging.Phase.STANDALONE -> {
                val ids = api.search(library, STANDALONE_QUERY, num = PAGE_SIZE, offset = slice.offset).book_ids
                fetchBooks(ids).map { (bid, dto) -> CalibreMapper.standaloneSeries(dto, bid, id) }
            }
        }
        return PagedResult(items, hasNextPage = BrowsePaging.hasNext(page, PAGE_SIZE, seriesTotal, standaloneTotal))
    }

    // ----------------------------------------------------------------- search
    override suspend fun search(query: String, page: Int): PagedResult<Series> {
        val offset = (page - 1).coerceAtLeast(0) * PAGE_SIZE
        val result = api.search(library, query, num = PAGE_SIZE, offset = offset)
        val books = fetchBooks(result.book_ids)
        val series = CalibreMapper.groupSearch(books, id)
        val hasNext = offset + result.book_ids.size < result.total_num
        return PagedResult(series, hasNextPage = hasNext)
    }

    // ----------------------------------------------------------------- books
    override suspend fun books(seriesRemoteId: String): List<Book> {
        return when (val parsed = CalibreRemoteId.decode(seriesRemoteId)) {
            is CalibreRemoteId.Parsed.Book -> {
                val dto = runCatching { api.book(parsed.id, library) }.getOrNull() ?: return emptyList()
                listOfNotNull(CalibreMapper.toBook(dto, parsed.id, id, dto.title))
            }
            is CalibreRemoteId.Parsed.Series -> {
                val ids = api.search(library, seriesQuery(parsed.name), num = PAGE_SIZE, offset = 0).book_ids
                fetchBooks(ids)
                    .toList()
                    .sortedBy { it.second.series_index ?: 0.0 }
                    .mapNotNull { (bid, dto) -> CalibreMapper.toBook(dto, bid, id, parsed.name) }
            }
        }
    }

    // ----------------------------------------------------------------- detail
    override suspend fun seriesDetail(seriesRemoteId: String): Series? {
        return when (val parsed = CalibreRemoteId.decode(seriesRemoteId)) {
            is CalibreRemoteId.Parsed.Series -> CalibreMapper.seriesTile(parsed.name, id)
            is CalibreRemoteId.Parsed.Book -> {
                val dto = runCatching { api.book(parsed.id, library) }.getOrNull() ?: return null
                CalibreMapper.standaloneSeries(dto, parsed.id, id)
            }
        }
    }

    // ----------------------------------------------------------------- reading
    /** Calibre serves whole files, no page streaming → empty list → reader reads whole-file. */
    override suspend fun pages(bookRemoteId: String): List<PageRef> = emptyList()

    override suspend fun openPage(ref: PageRef): ByteArray =
        throw UnsupportedOperationException("Calibre has no page streaming; use downloadFile")

    override suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): ByteArray {
        val dto = api.book(bookRemoteId, library)
        val fmt = CalibreMapper.pickFormat(dto.formats)?.name
            ?: throw IllegalStateException("No readable format for book $bookRemoteId")
        val body = api.raw("get/$fmt/$bookRemoteId/$library")
        val total = body.contentLength()
        if (total <= 0) return body.bytes()
        val out = java.io.ByteArrayOutputStream(total.toInt().coerceAtLeast(8192))
        val buffer = ByteArray(8192)
        var read = 0L
        body.byteStream().use { stream ->
            var n: Int
            while (stream.read(buffer).also { n = it } != -1) {
                out.write(buffer, 0, n); read += n; onProgress(read, total)
            }
        }
        return out.toByteArray()
    }

    override suspend fun seriesIdOf(bookRemoteId: String): String {
        val dto = api.book(bookRemoteId, library)
        val series = dto.series?.ifBlank { null }
        return if (series != null) CalibreRemoteId.forSeries(series) else CalibreRemoteId.forBook(bookRemoteId)
    }

    // ----------------------------------------------------------------- cover
    override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray {
        val bookId = if (isSeriesCover) firstVolumeId(remoteId) else remoteId
            ?: return ByteArray(0)
        return api.raw("get/cover/$bookId/$library").bytes()
    }

    // ----------------------------------------------------------------- helpers
    private suspend fun resolveSeriesCategory(): String {
        seriesCategoryEncoded?.let { return it }
        val cats = api.categories(library)
        val seriesCat = cats.firstOrNull { it.name.equals("Series", ignoreCase = true) }
            ?: throw IllegalStateException("Calibre: no Series category")
        // url like "/ajax/category/<encoded>" (optionally "/<lib>")
        val encoded = seriesCat.url.substringAfter("/ajax/category/").substringBefore("/")
        seriesCategoryEncoded = encoded
        return encoded
    }

    /** Fetches book metadata for ids, dropping nulls, preserving id order. */
    private suspend fun fetchBooks(ids: List<Int>): Map<String, CalibreBookDto> {
        if (ids.isEmpty()) return emptyMap()
        val raw = api.books(library, ids.joinToString(","))
        val out = LinkedHashMap<String, CalibreBookDto>()
        for (idInt in ids) {
            val key = idInt.toString()
            raw[key]?.let { out[key] = it }
        }
        return out
    }

    /** Lowest-series_index volume id for a series remoteId; null if not a series / empty. */
    private suspend fun firstVolumeId(seriesRemoteId: String): String? {
        val parsed = CalibreRemoteId.decode(seriesRemoteId)
        if (parsed is CalibreRemoteId.Parsed.Book) return parsed.id
        val name = (parsed as CalibreRemoteId.Parsed.Series).name
        val ids = api.search(library, seriesQuery(name), num = PAGE_SIZE, offset = 0).book_ids
        return fetchBooks(ids).toList().minByOrNull { it.second.series_index ?: 0.0 }?.first
    }

    private fun seriesQuery(name: String): String = "series:\"${name.replace("\"", "\\\"")}\""
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreSourceContractTest*"`
Expected: PASS.

- [ ] **Step 5: Run the full unit suite**

Run: `./gradlew :komga-calibre-source:testDebugUnitTest`
Expected: PASS (all four test classes).

- [ ] **Step 6: Commit**

```bash
git add komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreSource.kt komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreSourceContractTest.kt
git commit -m "feat(calibre): BrowsableSource over /ajax/ + contract test"
```

---

### Task 7: Wire CalibreSourcePlugin.create + build APK

**Files:**
- Modify: `komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreSourcePlugin.kt`

**Interfaces:**
- Consumes: `buildCalibreClient` (Task 5), `CalibreSource` (Task 6).

- [ ] **Step 1: Replace the stub `create()`**

Replace the `TODO(...)` body and add the imports `buildCalibreClient` + `CalibreSource`:

```kotlin
    override fun create(config: Map<String, String>): BrowsableSource {
        val url = config["url"]?.trim()?.trimEnd('/')?.ifBlank { null }
            ?: throw IllegalArgumentException("Calibre plugin: 'url' missing")
        val username = config["username"].orEmpty().trim()
        val password = config["password"].orEmpty()
        val api = buildCalibreClient(baseUrl = "$url/", username = username, password = password)

        val library = config["library"]?.trim()?.ifBlank { null }
            ?: runBlocking { runCatching { api.libraryInfo().default_library }.getOrNull() }
            ?: throw IllegalArgumentException("Calibre plugin: cannot resolve library")

        return CalibreSource(api = api, baseUrl = url, library = library, name = "Calibre @ ${hostOf(url)}")
    }

    private fun hostOf(url: String): String = try {
        java.net.URL(url).host
    } catch (_: Exception) { url }
```

Add imports at the top: `import com.komgareader.plugin.calibre.client.buildCalibreClient`, `import kotlinx.coroutines.runBlocking`.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :komga-calibre-source:assembleDebug`
Expected: BUILD SUCCESSFUL; APK at `komga-calibre-source/build/outputs/apk/debug/komga-calibre-source-debug.apk`.

- [ ] **Step 3: Commit**

```bash
git add komga-calibre-source/src/main/java/com/komgareader/plugin/calibre/CalibreSourcePlugin.kt
git commit -m "feat(calibre): wire plugin entry (resolve library, build source)"
```

---

### Task 8: README

**Files:**
- Create: `komga-calibre-source/README.md`

- [ ] **Step 1: Write the README**

```markdown
# Calibre Source Plugin

Connects the Komga-Reader to a **Calibre Content Server** via its `/ajax/` JSON API.
Books are grouped into Series → volumes by Calibre's `series` metadata.

> **Alternative without this plugin:** Calibre also speaks OPDS. You can add it in the
> reader as Server → OPDS pointing at `http://<host>:<port>/opds`. This plugin exists
> because Calibre's `/ajax/` API gives proper series grouping and richer metadata than
> its OPDS feed.

## Server setup (Calibre Content Server)

Run the Calibre **Content Server** (not calibre-web):

- Desktop calibre: *Connect/share → Start Content Server*, or
- Headless: `calibre-server /path/to/library --port 8080`
- Optional auth: add `--enable-auth` and create a user (`--manage-users`).

The default port is 8080. Confirm `http://<host>:8080/ajax/library-info` returns JSON.

## Connect from the reader

1. Settings → **Server hinzufügen** → **Plugin** → install **Calibre** from the repo,
   confirm the trust (TOFU) dialog.
2. Fill the config form:
   - **Server-URL** — `http://<host>:<port>` (e.g. `http://192.168.1.10:8080`)
   - **Benutzername** / **Passwort** — only if you started the server with `--enable-auth`
   - **Bibliothek** — leave blank to use the server's default library; set it to a key
     from `/ajax/library-info`'s `library_map` for a specific one.

## Quirks & limitations

- **Content Server only.** calibre-web is a different project and does not serve `/ajax/`.
- **Whole-file reading.** Calibre has no page streaming; books are downloaded whole, then
  rendered by the reader (EPUB/PDF/CBZ/CBR). Readable formats are picked in the order
  EPUB > PDF > CBZ > CBR; books with none of these are hidden.
- **Series cover** = the cover of the series' first volume (Calibre has no per-series cover).
- **Read-only — progress is NOT synced to Calibre yet.** Reading position stays on the
  device. Calibre's last-read API is EPUB-CFI based (not page-based), so a future version
  will need a CFI↔page bridge. This is the planned next step.

## Verified against

_(updated by the verification loop — see the plan's E2E task)_
- Docker image: `TBD`
- Demo works: `TBD`
```

- [ ] **Step 2: Commit**

```bash
git add komga-calibre-source/README.md
git commit -m "docs(calibre): plugin README (setup, connect, quirks, sync gap)"
```

---

### Task 9: E2E against a Dockerized Calibre + repo.json + distributable APK

**Files:**
- Create: `komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreLiveTest.kt`
- Modify: `komga-calibre-source/README.md` (fill "Verified against")
- Modify: `repo.json` (add Calibre entry)
- Create: `plugins/komga-calibre-source-0.1.0.apk` (built + signed)

**Interfaces:**
- Consumes: `CalibreSource`, `buildCalibreClient`.

- [ ] **Step 1: Start a Calibre Content Server with real demo works**

Run a Dockerized content server with a tiny demo library (a multi-volume series + standalone books, mixed formats). Use the linuxserver image, which runs desktop calibre with the content server on port 8081:

```bash
mkdir -p /tmp/calibre-demo/config /tmp/calibre-demo/library
docker run -d --name calibre-demo -p 8081:8081 -p 8080:8080 \
  -v /tmp/calibre-demo/config:/config -v /tmp/calibre-demo/library:/books \
  lscr.io/linuxserver/calibre:latest
```

Wait for it to boot, then add demo books via `calibredb` inside the container (download a few public-domain EPUBs from Project Gutenberg first, e.g. into `/tmp/calibre-demo/`):

```bash
# Example: pull 3 public-domain EPUBs (Gutenberg) and import, tagging a 2-book series.
docker exec calibre-demo bash -lc '
  cd /tmp && \
  curl -L -o a.epub https://www.gutenberg.org/ebooks/1342.epub.images && \
  curl -L -o b.epub https://www.gutenberg.org/ebooks/1343.epub.images && \
  curl -L -o c.epub https://www.gutenberg.org/ebooks/11.epub.images && \
  calibredb add a.epub b.epub c.epub --library-path /books'
# Set series metadata on the first two (ids shown by the add output, e.g. 1 and 2):
docker exec calibre-demo bash -lc 'calibredb set_metadata 1 --field series:"Demo Saga" --field series_index:1 --library-path /books; calibredb set_metadata 2 --field series:"Demo Saga" --field series_index:2 --library-path /books'
```

Confirm the API: `curl -s http://localhost:8081/ajax/library-info | head -c 200` returns JSON with `default_library`.

> If the linuxserver content server runs on a different port or the default library key
> differs, read it from `/ajax/library-info` and adjust `CALIBRE_LIVE_URL` / library below.

- [ ] **Step 2: Write the env-gated live test**

```kotlin
package com.komgareader.plugin.calibre

import com.komgareader.domain.source.SourceFilter
import com.komgareader.plugin.calibre.client.buildCalibreClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live test against a real Calibre Content Server. Gated by env CALIBRE_LIVE=1 so the
 * normal unit run skips it. Set CALIBRE_LIVE_URL (default http://localhost:8081),
 * CALIBRE_LIVE_LIBRARY (default Calibre_Library), and optional CALIBRE_LIVE_USER/PASS.
 */
class CalibreLiveTest {

    private fun env(k: String, d: String) = System.getenv(k)?.ifBlank { null } ?: d

    @Test fun `browse, books and download work against a real server`() = runBlocking {
        assumeTrue("set CALIBRE_LIVE=1 to run", System.getenv("CALIBRE_LIVE") == "1")
        val url = env("CALIBRE_LIVE_URL", "http://localhost:8081")
        val source = CalibreSource(
            api = buildCalibreClient("$url/", System.getenv("CALIBRE_LIVE_USER"), System.getenv("CALIBRE_LIVE_PASS"), debug = true),
            baseUrl = url,
            library = env("CALIBRE_LIVE_LIBRARY", "Calibre_Library"),
            name = "Live-Calibre",
        )
        val first = source.browse(1, SourceFilter())
        assertTrue("expected at least one shelf entry", first.items.isNotEmpty())
        // Find a series tile, list its volumes, download the first volume's bytes.
        val tile = first.items.first()
        val books = source.books(tile.remoteId)
        assertTrue("expected volumes", books.isNotEmpty())
        val bytes = source.downloadFile(books.first().remoteId) { _, _ -> }
        assertTrue("expected file bytes", bytes.isNotEmpty())
        // Cover bytes
        assertTrue(source.coverBytes(tile.remoteId, isSeriesCover = true).isNotEmpty())
    }
}
```

- [ ] **Step 3: Run the live test**

Run: `CALIBRE_LIVE=1 ./gradlew :komga-calibre-source:testDebugUnitTest --tests "*CalibreLiveTest*"`
Expected: PASS. If it fails, debug against the real responses (`debug = true` logs requests; inspect with `curl http://localhost:8081/ajax/...`). Fix `CalibreSource`/mapper as needed and re-run the full suite (`./gradlew :komga-calibre-source:testDebugUnitTest`).

- [ ] **Step 4: Fill the README "Verified against" section**

Replace the `TBD`s with the actual image digest/version (`docker inspect --format '{{.Config.Image}}' calibre-demo` + `docker exec calibre-demo calibre --version`) and the demo works used (titles + the series mapping). Keep it factual — this is the audit trail for the verification loop.

- [ ] **Step 5: Build + sign the distributable APK**

The repo's sibling APKs are signed with a shared debug keystore (fingerprint `F4:16:A7:F7:44:DE:08:44:8F:E9:99:1C:AC:DB:2A:19:7E:14:82:DA:55:AE:2C:18:5F:EC:C6:24:C6:C0:68:DA`). Build the debug APK (debug builds are signed with that keystore by the monorepo config) and copy it:

```bash
./gradlew :komga-calibre-source:assembleDebug
cp komga-calibre-source/build/outputs/apk/debug/komga-calibre-source-debug.apk plugins/komga-calibre-source-0.1.0.apk
```

Verify the fingerprint matches the siblings:

```bash
keytool -printcert -jarfile plugins/komga-calibre-source-0.1.0.apk | grep -i SHA256
```

Expected: SHA256 equals the fingerprint above. If it differs, the monorepo uses a specific keystore — locate it (check sibling module build configs / `~/.android/debug.keystore`) and sign with the same one before copying.

- [ ] **Step 6: Add the repo.json entry**

Add to the `plugins` array in `repo.json` (use the verified fingerprint from Step 5):

```json
{ "packageName": "com.komgareader.plugin.calibre", "name": "Calibre", "description": "Calibre Content Server als Quelle (E-Books/Comics)", "type": "source", "abiVersion": 1, "versionCode": 1, "versionName": "0.1.0", "apkUrl": "plugins/komga-calibre-source-0.1.0.apk", "fingerprint": "F4:16:A7:F7:44:DE:08:44:8F:E9:99:1C:AC:DB:2A:19:7E:14:82:DA:55:AE:2C:18:5F:EC:C6:24:C6:C0:68:DA" }
```

- [ ] **Step 7: Commit**

```bash
git add komga-calibre-source/src/test/java/com/komgareader/plugin/calibre/CalibreLiveTest.kt komga-calibre-source/README.md repo.json plugins/komga-calibre-source-0.1.0.apk
git commit -m "test(calibre): live E2E vs Dockerized Content Server + distribute APK"
```

- [ ] **Step 8: Tear down the demo container**

Run: `docker rm -f calibre-demo`

---

## Self-Review

**Spec coverage:** identity/manifest (T1), ConfigSchema (T1), remote-id codec (T2), DTOs+mapper incl. format pick & grouping (T3), browse pagination (T4), API+auth client (T5), all BrowsableSource methods incl. whole-file read & series cover (T6), plugin wiring & library resolution (T7), README with connect guide + quirks + sync gap (T8), Docker E2E with real works + verified-against doc + repo.json + distributable APK (T9). No `SyncingSource` (intentional, documented). Monorepo wiring incl. `mavenLocal()` (T1). All spec sections covered.

**Placeholder scan:** The only `TBD`s are in the README "Verified against" section, intentionally filled at T9 Step 4 (real-world data not knowable until the live run). No code placeholders.

**Type consistency:** `CalibreRemoteId.{forSeries,forBook,decode}` + `Parsed.{Series,Book}` consistent across T2/T3/T6. `CalibreMapper.{pickFormat,toBook,standaloneSeries,seriesTile,groupSearch}` consistent T3/T6. `BrowsePaging.{slice,hasNext,Phase,Slice}` consistent T4/T6. `CalibreApi` method signatures consistent T5/T6. `buildCalibreClient(baseUrl, username, password, debug)` consistent T5/T6/T7. `CalibreSource(api, baseUrl, library, name)` consistent T6/T7/T9.
