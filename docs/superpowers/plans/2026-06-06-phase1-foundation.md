# Phase 1 · Plan 1/4 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Multimodul-Gradle-Gerüst + reines Domain-Modell + beide Interface-Nähte (`MediaSource`, `Document`, `EinkController`), voll Test-getrieben, ohne Android-Abhängigkeit.

**Architecture:** Clean Architecture. Das `:domain`-Modul ist ein reines Kotlin/JVM-Modul ohne Android-/Netz-Abhängigkeiten — die testbare, framework-freie Mitte. Es definiert die Modelle, die Use-Cases und die zwei tragenden Interface-Nähte, gegen die alle späteren Pläne (MuPDF-Render, KomgaSource, Reader/UI) implementieren.

**Tech Stack:** Kotlin/JVM · Gradle KTS + Version-Catalog · JUnit5 · kotlin.test · MockK · Turbine (Flow-Tests) · kotlinx.coroutines.

---

## Referenz-Spec
`docs/superpowers/specs/2026-06-06-komga-eink-reader-design.md` — §3 Module, §4 Domain-Modell, §5 Naht A, §6 Naht B, §7 Sync-Konfliktregel.

## File Structure (in diesem Plan erstellt)

```
settings.gradle.kts                  # Modul-Includes
build.gradle.kts                     # Root, Plugin-Versionen
gradle/libs.versions.toml            # Version-Catalog
domain/build.gradle.kts              # kotlin("jvm"), JUnit5
domain/src/main/kotlin/com/komgareader/domain/
  model/ContentType.kt               # COMIC | NOVEL | WEBTOON
  model/ViewerType.kt                # PAGED | WEBTOON | EPUB
  model/SourceKind.kt                # KOMGA | LOCAL | OPDS | PLUGIN
  model/BookFormat.kt                # CBZ | CBR | PDF | EPUB
  model/DownloadState.kt             # REMOTE | DOWNLOADING | LOCAL
  model/Shelf.kt                     # Regal: Quellen-Bündel + Typ-Tag
  model/Series.kt                    # mit contentTypeOverride
  model/Book.kt
  model/ReadProgress.kt
  source/MediaSource.kt              # Naht A: MediaSource/Browsable/Syncing + DTOs
  source/SourceId.kt                 # deterministische 64-bit-ID
  source/StubSource.kt               # Fallback für fehlende Quelle
  source/SourceManager.kt            # reaktive id→Source-Map
  render/Document.kt                 # Naht B: Render-Interface
  eink/EinkController.kt             # Naht B: E-Ink-Geräte-Interface
  usecase/ResolveViewerType.kt       # Series+Shelf → ViewerType
  usecase/ResolveProgressConflict.kt # Offline-first Konfliktregel
domain/src/test/kotlin/com/komgareader/domain/
  source/SourceIdTest.kt
  source/SourceManagerTest.kt
  usecase/ResolveViewerTypeTest.kt
  usecase/ResolveProgressConflictTest.kt
```

---

### Task 0: Gradle-Multimodul-Gerüst mit `:domain`

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `domain/build.gradle.kts`

- [ ] **Step 1: Version-Catalog anlegen**

Create `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.0.21"
coroutines = "1.9.0"
junit5 = "5.11.3"
mockk = "1.13.13"
turbine = "1.2.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit5" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 2: Root-Build + Settings anlegen**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "komga-reader"
include(":domain")
```

Create `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
```

- [ ] **Step 3: `:domain`-Modul-Build anlegen**

Create `domain/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(17) }
```

- [ ] **Step 4: Build verifizieren**

Run: `./gradlew :domain:build`
Expected: BUILD SUCCESSFUL (leeres Modul kompiliert, keine Tests).
Falls Gradle-Wrapper fehlt: `gradle wrapper --gradle-version 8.10` einmalig ausführen, dann erneut.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml domain/build.gradle.kts gradlew gradlew.bat gradle/wrapper
git commit -m "build: Gradle-Multimodul-Geruest mit :domain-Modul"
```

---

### Task 1: Domain-Enums (Wert-Typen ohne Logik)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ContentType.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ViewerType.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/SourceKind.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/BookFormat.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/DownloadState.kt`

- [ ] **Step 1: Enums anlegen**

Create `ContentType.kt`:

```kotlin
package com.komgareader.domain.model

/** Inhalts-Typ eines Regals/einer Serie. Bestimmt den Default-Viewer. */
enum class ContentType { COMIC, NOVEL, WEBTOON }
```

Create `ViewerType.kt`:

```kotlin
package com.komgareader.domain.model

/** Konkreter Lese-Modus, den der Reader lädt. */
enum class ViewerType { PAGED, WEBTOON, EPUB }
```

Create `SourceKind.kt`:

```kotlin
package com.komgareader.domain.model

/** Art der Backend-Quelle. */
enum class SourceKind { KOMGA, LOCAL, OPDS, PLUGIN }
```

Create `BookFormat.kt`:

```kotlin
package com.komgareader.domain.model

enum class BookFormat { CBZ, CBR, PDF, EPUB }
```

Create `DownloadState.kt`:

```kotlin
package com.komgareader.domain.model

enum class DownloadState { REMOTE, DOWNLOADING, LOCAL }
```

- [ ] **Step 2: Kompilieren**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/
git commit -m "feat(domain): Inhalts-/Viewer-/Quellen-Enums"
```

---

### Task 2: Datenmodelle Shelf, Series, Book, ReadProgress

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/Shelf.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/Series.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/Book.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ReadProgress.kt`

- [ ] **Step 1: Modelle anlegen**

Create `Shelf.kt`:

```kotlin
package com.komgareader.domain.model

/**
 * Nutzer-definiertes Regal. Bündelt eine oder mehrere Quellen und deklariert
 * über [contentType] den Default-Viewer für alle enthaltenen Serien.
 */
data class Shelf(
    val id: Long,
    val name: String,
    val contentType: ContentType,
    val sourceIds: List<Long>,
)
```

Create `Series.kt`:

```kotlin
package com.komgareader.domain.model

/**
 * Eine Serie aus einer Quelle. [contentTypeOverride] erlaubt, den vom Regal
 * vorgegebenen Typ pro Serie zu überschreiben.
 */
data class Series(
    val id: Long,
    val sourceId: Long,
    val remoteId: String,
    val title: String,
    val coverUrl: String? = null,
    val contentTypeOverride: ContentType? = null,
)
```

Create `Book.kt`:

```kotlin
package com.komgareader.domain.model

data class Book(
    val id: Long,
    val sourceId: Long,
    val seriesId: Long,
    val remoteId: String,
    val title: String,
    val format: BookFormat,
    val pageCount: Int,
    val downloadState: DownloadState = DownloadState.REMOTE,
)
```

Create `ReadProgress.kt`:

```kotlin
package com.komgareader.domain.model

/**
 * Lese-Fortschritt eines Buchs. [dirty] markiert lokal geänderten, noch nicht
 * zum Server gepushten Stand. [locator] hält EPUB-Position bzw. Comic-Seitenindex.
 */
data class ReadProgress(
    val bookId: Long,
    val page: Int,
    val totalPages: Int,
    val completed: Boolean = false,
    val locator: String? = null,
    val dirty: Boolean = false,
    val updatedAt: Long,
)
```

- [ ] **Step 2: Kompilieren**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/
git commit -m "feat(domain): Modelle Shelf, Series, Book, ReadProgress"
```

---

### Task 3: Deterministische Quellen-ID (`SourceId`)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/source/SourceId.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/source/SourceIdTest.kt`

- [ ] **Step 1: Failing-Test schreiben**

Create `SourceIdTest.kt`:

```kotlin
package com.komgareader.domain.source

import com.komgareader.domain.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SourceIdTest {

    @Test
    fun `gleiche Eingabe ergibt stabil dieselbe ID`() {
        val a = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://nas.local:25600")
        val b = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://nas.local:25600")
        assertEquals(a, b)
    }

    @Test
    fun `unterschiedliche Eingabe ergibt unterschiedliche ID`() {
        val a = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://nas.local:25600")
        val b = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://other.local:25600")
        assertNotEquals(a, b)
    }

    @Test
    fun `ID ist nie negativ (Sign-Bit geloescht)`() {
        val id = SourceId.of("x", SourceKind.PLUGIN, "y")
        assertTrue(id >= 0L)
    }

    @Test
    fun `lokale Quelle hat reservierte ID 0`() {
        assertEquals(0L, SourceId.LOCAL)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.source.SourceIdTest"`
Expected: FAIL (Compile-Fehler: `SourceId` unbekannt).

- [ ] **Step 3: Minimale Implementierung**

Create `SourceId.kt`:

```kotlin
package com.komgareader.domain.source

import com.komgareader.domain.model.SourceKind
import java.security.MessageDigest

/**
 * Erzeugt global eindeutige, deterministische 64-bit-Quellen-IDs ohne zentrale
 * Registry — analog zu Mihons Source-ID. So koexistieren mehrere Server/Quellen
 * stabil. ID 0 ist für die lokale Quelle reserviert.
 */
object SourceId {

    const val LOCAL: Long = 0L

    fun of(name: String, kind: SourceKind, config: String): Long {
        val key = "${name.lowercase()}/${kind.name}/$config"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        var id = 0L
        for (i in 0 until 8) {
            id = (id shl 8) or (digest[i].toLong() and 0xff)
        }
        return id and Long.MAX_VALUE // Sign-Bit löschen → immer >= 0
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg prüfen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.source.SourceIdTest"`
Expected: PASS (4 Tests grün).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/source/SourceId.kt domain/src/test/kotlin/com/komgareader/domain/source/SourceIdTest.kt
git commit -m "feat(domain): deterministische SourceId mit reservierter LOCAL-ID"
```

---

### Task 4: Viewer-Auflösung (`ResolveViewerType`)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt`

- [ ] **Step 1: Failing-Test schreiben**

Create `ResolveViewerTypeTest.kt`:

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ViewerType
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveViewerTypeTest {

    private val resolve = ResolveViewerType()
    private val shelf = Shelf(id = 1, name = "R", contentType = ContentType.COMIC, sourceIds = listOf(5))
    private fun series(override: ContentType? = null) =
        Series(id = 9, sourceId = 5, remoteId = "r", title = "T", contentTypeOverride = override)

    @Test
    fun `Comic-Regal ohne Override ergibt PAGED`() {
        assertEquals(ViewerType.PAGED, resolve(series(), shelf))
    }

    @Test
    fun `Webtoon-Regal ergibt WEBTOON`() {
        val webtoonShelf = shelf.copy(contentType = ContentType.WEBTOON)
        assertEquals(ViewerType.WEBTOON, resolve(series(), webtoonShelf))
    }

    @Test
    fun `Novel-Regal ergibt EPUB`() {
        val novelShelf = shelf.copy(contentType = ContentType.NOVEL)
        assertEquals(ViewerType.EPUB, resolve(series(), novelShelf))
    }

    @Test
    fun `Serien-Override schlaegt den Regal-Typ`() {
        // Comic-Regal, aber diese Serie ist als Webtoon markiert
        assertEquals(ViewerType.WEBTOON, resolve(series(ContentType.WEBTOON), shelf))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ResolveViewerTypeTest"`
Expected: FAIL (`ResolveViewerType` unbekannt).

- [ ] **Step 3: Minimale Implementierung**

Create `ResolveViewerType.kt`:

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ViewerType

/**
 * Bestimmt deterministisch den Lese-Modus: Serien-Override hat Vorrang vor dem
 * Regal-Typ. Kein Auto-Erkennen — der Typ ist immer explizit deklariert.
 */
class ResolveViewerType {

    operator fun invoke(series: Series, shelf: Shelf): ViewerType =
        when (series.contentTypeOverride ?: shelf.contentType) {
            ContentType.COMIC -> ViewerType.PAGED
            ContentType.WEBTOON -> ViewerType.WEBTOON
            ContentType.NOVEL -> ViewerType.EPUB
        }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg prüfen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ResolveViewerTypeTest"`
Expected: PASS (4 Tests grün).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt
git commit -m "feat(domain): ResolveViewerType (Override schlaegt Regal-Typ)"
```

---

### Task 5: Sync-Konfliktregel (`ResolveProgressConflict`)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveProgressConflict.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveProgressConflictTest.kt`

- [ ] **Step 1: Failing-Test schreiben**

Create `ResolveProgressConflictTest.kt`:

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReadProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ResolveProgressConflictTest {

    private val resolve = ResolveProgressConflict()
    private fun progress(page: Int, updatedAt: Long, dirty: Boolean = false) =
        ReadProgress(bookId = 1, page = page, totalPages = 100, dirty = dirty, updatedAt = updatedAt)

    @Test
    fun `juengster Stand gewinnt — lokal neuer`() {
        val local = progress(page = 50, updatedAt = 2000)
        val remote = progress(page = 30, updatedAt = 1000)
        assertSame(local, resolve(local, remote))
    }

    @Test
    fun `juengster Stand gewinnt — remote neuer`() {
        val local = progress(page = 50, updatedAt = 1000)
        val remote = progress(page = 70, updatedAt = 3000)
        assertSame(remote, resolve(local, remote))
    }

    @Test
    fun `fehlender Remote-Stand — lokal gewinnt`() {
        val local = progress(page = 50, updatedAt = 1000)
        assertSame(local, resolve(local, null))
    }

    @Test
    fun `gleicher Zeitstempel — lokaler Stand gewinnt`() {
        val local = progress(page = 50, updatedAt = 1000, dirty = true)
        val remote = progress(page = 40, updatedAt = 1000)
        assertSame(local, resolve(local, remote))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ResolveProgressConflictTest"`
Expected: FAIL (`ResolveProgressConflict` unbekannt).

- [ ] **Step 3: Minimale Implementierung**

Create `ResolveProgressConflict.kt`:

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReadProgress

/**
 * Offline-first-Konfliktregel beim Sync: der jüngste Stand (höchstes updatedAt)
 * gewinnt. Bei Gleichstand gewinnt der lokale Stand. Fehlt der Remote-Stand,
 * bleibt der lokale erhalten.
 */
class ResolveProgressConflict {

    operator fun invoke(local: ReadProgress, remote: ReadProgress?): ReadProgress {
        if (remote == null) return local
        return if (remote.updatedAt > local.updatedAt) remote else local
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg prüfen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ResolveProgressConflictTest"`
Expected: PASS (4 Tests grün).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveProgressConflict.kt domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveProgressConflictTest.kt
git commit -m "feat(domain): ResolveProgressConflict (juengster Stand gewinnt)"
```

---

### Task 6: Naht A — `MediaSource`-Interfaces + DTOs

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/source/MediaSource.kt`

- [ ] **Step 1: Interfaces + Transport-Typen anlegen**

Create `MediaSource.kt`:

```kotlin
package com.komgareader.domain.source

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind

/** Naht A: jede Backend-Quelle (Komga, lokal, Online-Plugin) implementiert dies. */
interface MediaSource {
    val id: Long
    val name: String
    val kind: SourceKind
}

/** Eine Seite Ergebnisse mit Cursor-Flag. */
data class PagedResult<T>(val items: List<T>, val hasNextPage: Boolean)

/** Filter für [BrowsableSource.browse]. Wächst in späteren Plänen. */
data class SourceFilter(val seriesId: String? = null)

/** Verweis auf eine einzelne Seite (Bild) eines Buchs. */
data class PageRef(val index: Int, val url: String)

/** Quelle, die durchsucht und gelesen werden kann. */
interface BrowsableSource : MediaSource {
    suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series>
    suspend fun search(query: String, page: Int): PagedResult<Series>
    suspend fun books(seriesRemoteId: String): List<Book>
    suspend fun pages(bookRemoteId: String): List<PageRef>
    /** Liefert die rohen Bytes einer Seite (Stream) oder des Buchs (Download). */
    suspend fun openPage(ref: PageRef): ByteArray
}

/** Quelle, die Lese-Fortschritt server-seitig synchronisieren kann (z.B. Komga). */
interface SyncingSource : MediaSource {
    suspend fun pushProgress(bookRemoteId: String, progress: ReadProgress)
    suspend fun pullProgress(bookRemoteId: String): ReadProgress?
}
```

- [ ] **Step 2: Kompilieren**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/source/MediaSource.kt
git commit -m "feat(domain): Naht A — MediaSource/Browsable/Syncing + DTOs"
```

---

### Task 7: `StubSource` + `SourceManager` (reaktive Registry)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/source/StubSource.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/source/SourceManager.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/source/SourceManagerTest.kt`

- [ ] **Step 1: Failing-Test schreiben**

Create `SourceManagerTest.kt`:

```kotlin
package com.komgareader.domain.source

import com.komgareader.domain.model.SourceKind
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSource(
    override val id: Long,
    override val name: String,
    override val kind: SourceKind = SourceKind.KOMGA,
) : MediaSource

class SourceManagerTest {

    @Test
    fun `get liefert registrierte Quelle`() {
        val manager = SourceManager()
        val source = FakeSource(id = 42, name = "Komga")
        manager.register(source)
        assertEquals(source, manager.get(42))
    }

    @Test
    fun `get liefert null fuer unbekannte Quelle`() {
        val manager = SourceManager()
        assertNull(manager.get(99))
    }

    @Test
    fun `getOrStub liefert Stub fuer fehlende Quelle statt null`() {
        val manager = SourceManager()
        val stub = manager.getOrStub(id = 7, name = "Verschwunden")
        assertTrue(stub is StubSource)
        assertEquals(7, stub.id)
        assertEquals("Verschwunden", stub.name)
    }

    @Test
    fun `sources-Flow emittiert nach Registrierung`() = runTest {
        val manager = SourceManager()
        manager.sources.test {
            assertEquals(emptyList(), awaitItem())
            manager.register(FakeSource(id = 1, name = "A"))
            assertEquals(listOf(1L), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.source.SourceManagerTest"`
Expected: FAIL (`StubSource`/`SourceManager` unbekannt).

- [ ] **Step 3: `StubSource` implementieren**

Create `StubSource.kt`:

```kotlin
package com.komgareader.domain.source

import com.komgareader.domain.model.SourceKind

/**
 * Platzhalter-Quelle für Bibliotheks-Einträge, deren echte Quelle (noch) nicht
 * verfügbar ist (entfernter Server, deinstalliertes Plugin). Hält Titel/ID, damit
 * die Bibliothek nie bricht. Browsing/Sync schlagen bewusst fehl.
 */
data class StubSource(
    override val id: Long,
    override val name: String,
) : MediaSource {
    override val kind: SourceKind = SourceKind.PLUGIN
}
```

- [ ] **Step 4: `SourceManager` implementieren**

Create `SourceManager.kt`:

```kotlin
package com.komgareader.domain.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Reaktive Registry aller aktiven Quellen (id → Source). Bibliothek, Reader und
 * Sync greifen ausschließlich hierüber zu und bleiben damit quellen-agnostisch.
 * [getOrStub] garantiert, dass ein fehlendes Backend die Bibliothek nicht bricht.
 */
class SourceManager {

    private val registry = MutableStateFlow<Map<Long, MediaSource>>(emptyMap())

    /** Beobachtbare Liste aller registrierten Quellen. */
    val sources: StateFlow<List<MediaSource>> =
        registry
            .map { it.values.toList() }
            .stateIn(
                scope = CoroutineScope(Dispatchers.Unconfined),
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    fun register(source: MediaSource) {
        registry.update { it + (source.id to source) }
    }

    fun unregister(id: Long) {
        registry.update { it - id }
    }

    fun get(id: Long): MediaSource? = registry.value[id]

    fun getOrStub(id: Long, name: String): MediaSource =
        registry.value[id] ?: StubSource(id, name)
}
```

- [ ] **Step 5: Test laufen lassen, Erfolg prüfen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.source.SourceManagerTest"`
Expected: PASS (4 Tests grün).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/source/StubSource.kt domain/src/main/kotlin/com/komgareader/domain/source/SourceManager.kt domain/src/test/kotlin/com/komgareader/domain/source/SourceManagerTest.kt
git commit -m "feat(domain): SourceManager (reaktive Registry) + StubSource-Fallback"
```

---

### Task 8: Naht B — `Document`-Render-Interface

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/render/Document.kt`

- [ ] **Step 1: Interface + Wert-Typen anlegen**

Create `Document.kt`:

```kotlin
package com.komgareader.domain.render

/** Pixelmaße einer Seite in nativer Auflösung. */
data class PageSize(val width: Int, val height: Int)

/**
 * Ein gerendertes Seitenbild: rohe ARGB_8888-Pixel + Maße. Bewusst Android-frei
 * (kein android.graphics.Bitmap), damit das Domain-Modul rein bleibt. Der
 * MuPDF-Wrapper in :render-core (Plan 2) füllt dies und konvertiert zu Bitmap.
 */
data class RenderedPage(val width: Int, val height: Int, val pixels: IntArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is RenderedPage &&
            width == other.width && height == other.height && pixels.contentEquals(other.pixels))

    override fun hashCode(): Int =
        (width * 31 + height) * 31 + pixels.contentHashCode()
}

/**
 * Naht B: gemeinsame Render-Abstraktion über alle Formate. MuPDF deckt
 * cbz/cbr/pdf und EPUB-Reflow ab; eine alternative Engine (z.B. crengine für
 * EPUB) kann später dahinter treten, ohne Reader/UI zu berühren.
 */
interface Document : AutoCloseable {
    fun pageCount(): Int
    fun pageSize(index: Int): PageSize
    /** Rendert Seite [index] skaliert um [zoom], rotiert um [rotation]° (0/90/180/270). */
    fun renderPage(index: Int, zoom: Float, rotation: Int): RenderedPage
}

/** Öffnet ein Dokument aus rohen Bytes. Implementierung in :render-core (Plan 2). */
interface DocumentFactory {
    fun open(bytes: ByteArray): Document
}
```

- [ ] **Step 2: Kompilieren**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/render/Document.kt
git commit -m "feat(domain): Naht B — Document-Render-Interface (Android-frei)"
```

---

### Task 9: Naht B — `EinkController`-Geräte-Interface

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt`

- [ ] **Step 1: Interface + Typen anlegen**

Create `EinkController.kt`:

```kotlin
package com.komgareader.domain.eink

import kotlinx.coroutines.flow.Flow

/** E-Ink-Refresh-Modus, nach steigender Qualität / sinkender Geschwindigkeit. */
enum class RefreshMode { A2, FAST, PARTIAL, FULL }

/** Rechteck-Region eines Refreshs (Pixel). */
data class Region(val x: Int, val y: Int, val width: Int, val height: Int)

/** Physische Geräte-Taste. */
enum class HardwareButton { PAGE_NEXT, PAGE_PREV, VOLUME_UP, VOLUME_DOWN }

data class ButtonEvent(val button: HardwareButton)

/** Geräte-Fähigkeiten zur Laufzeit (Boox vs. generisches Tablet). */
data class EinkCapabilities(
    val hasEink: Boolean,
    val canColor: Boolean,
    val canInvert: Boolean,
)

/**
 * Naht B (Geräteseite): kapselt E-Ink-Spezifika. Boox-Implementierung nutzt das
 * Onyx-SDK; auf Nicht-Boox greift eine No-Op-Implementierung (Standard-Invalidate,
 * Buttons als normale KeyEvents), damit Entwicklung/Tests überall laufen.
 */
interface EinkController {
    val capabilities: EinkCapabilities
    val buttonEvents: Flow<ButtonEvent>
    fun refresh(region: Region, mode: RefreshMode)
    fun setContrast(level: Int)
    fun setInverted(inverted: Boolean)
}
```

- [ ] **Step 2: Kompilieren**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt
git commit -m "feat(domain): Naht B — EinkController-Geraete-Interface"
```

---

### Task 10: Gesamt-Testlauf + Plan-Abschluss

- [ ] **Step 1: Voller Modul-Test**

Run: `./gradlew :domain:test`
Expected: BUILD SUCCESSFUL, alle Tests grün (SourceId 4 · ResolveViewerType 4 · ResolveProgressConflict 4 · SourceManager 4 = 16 Tests).

- [ ] **Step 2: Voller Build**

Run: `./gradlew :domain:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Abschluss-Commit (falls offene Änderungen)**

```bash
git add -A
git commit -m "chore(domain): Foundation-Plan abgeschlossen — Modell + beide Naht-Interfaces"
```

---

## Self-Review-Notiz (Autor)
- **Spec-Abdeckung:** §4 Domain-Modell → Tasks 1–2; §5 Naht A (MediaSource, deterministische ID, SourceManager+Stub) → Tasks 3, 6, 7; §6 Naht B (Document, EinkController) → Tasks 8, 9; §6 Viewer-Auflösung → Task 4; §7 Sync-Konfliktregel → Task 5. i18n/Theme/UI/MuPDF/KomgaSource sind **bewusst** in Plan 2–4 (siehe Roadmap), nicht in diesem Plan.
- **Bewusst verschoben:** `BrowsableSource.openPage` liefert hier `ByteArray` (einfachste testbare Form); Streaming/Range-Optimierung kommt mit KomgaSource (Plan 3). Reader-`Viewer`-Interface lebt im `:app`-Modul (Plan 4), nicht im Domain.
- **Typen-Konsistenz:** `ContentType`/`ViewerType`/`SourceKind`/`BookFormat`/`DownloadState` einheitlich genutzt; `MediaSource.id: Long` deckt sich mit `SourceId`/`SourceManager`/`StubSource`.
