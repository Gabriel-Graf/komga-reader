# Screensaver Cover Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move screensaver-cover generation off the reader-open path into a decoupled, crash-safe WorkManager worker that sets the server cover immediately as a fallback, then upgrades to a high-resolution cover (full first page, or whole-file extraction for EPUB/PDF/CBR) when the server cover is much smaller than the screen.

**Architecture:** `ReaderViewModel` stops doing screensaver work inline; it enqueues a one-time `ScreenSaverCoverWorker` (HiltWorker, WorkManager) keyed so only the most recently opened book wins. The worker delegates to a `@Singleton ScreenSaverCoverResolver` that (1) resolves the server cover per reader type and applies it as the baseline (the crash fallback), then (2) if that cover is low-resolution for the screen, fetches a high-res cover and applies it. Pure decision logic (per-type cover selection, low-res threshold) lives in a side-effect-free `ScreenSaverCoverPolicy` so it is JVM-unit-testable; the Android/render/seam calls live in the resolver and are device-verified.

**Tech Stack:** Kotlin, Jetpack WorkManager (`androidx.work`), Hilt + `androidx.hilt:hilt-work` (KSP), Coroutines, existing Seam A (`SourceManager`/`BrowsableSource`), Seam B (`DocumentFactory`/MuPDF `renderFirstPageCover`), `extractEpubCoverImage` (`:source-local`), `ScreenSaverManager`.

## Global Constraints

- Code-facing artifacts (comments, KDoc, commit messages) are **English**; user-visible strings go through i18n (not needed here — no new UI).
- E-Ink invariants are host-enforced; the screensaver only does work when `EinkController.capabilities.hasEink` (already gated in `ScreenSaverManager.applyCached`).
- Source-agnostic: the worker resolves a `BrowsableSource` via `SourceManager.get(sourceId)`; **no** concrete source type (`KomgaSource`, …) anywhere.
- Default screensaver mode is `BOOK_COVER` (already shipped); resolver no-ops unless mode is `BOOK_COVER`.
- KSP only (no kapt). Hilt version `2.52` (from `libs.versions.toml`).
- WorkManager runs the worker in the app's main process; the crash fallback is the **baseline server cover applied first**, not process isolation.
- Per-type cover choice (already the current behaviour, preserve it): `ViewerMode.WEBTOON` → whole-series cover; everything else (`PAGED`/`COMIC`, and EPUB/novel which opens as `PAGED`) → the current work's own cover.

---

## File Structure

- `gradle/libs.versions.toml` — add `work`, `androidxHiltWork` versions + library aliases.
- `app/build.gradle.kts` — add WorkManager + androidx-hilt-work deps (ksp for the androidx hilt compiler).
- `app/src/main/AndroidManifest.xml` — disable the default `WorkManagerInitializer` so Hilt provides the `WorkerFactory` (on-demand initialization).
- `app/src/main/kotlin/com/komgareader/app/KomgaReaderApp.kt` — implement `Configuration.Provider` exposing `HiltWorkerFactory`.
- `app/src/main/kotlin/com/komgareader/app/data/ScreenSaverCoverPolicy.kt` (**new, pure**) — per-type cover selection + low-res threshold. JVM-unit-tested.
- `app/src/test/kotlin/com/komgareader/app/data/ScreenSaverCoverPolicyTest.kt` (**new**) — tests for the pure policy.
- `app/src/main/kotlin/com/komgareader/app/data/ScreenSaverCoverResolver.kt` (**new**) — orchestration: server cover → baseline apply → high-res upgrade. Device-verified.
- `app/src/main/kotlin/com/komgareader/app/work/ScreenSaverCoverWorker.kt` (**new**) — thin `@HiltWorker` calling the resolver.
- `app/src/main/kotlin/com/komgareader/app/work/ScreenSaverScheduler.kt` (**new**) — `@Singleton` that enqueues the worker (unique, REPLACE).
- `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt` — replace inline `updateScreenSaverCover()` + its helpers with one `scheduler.schedule(...)` call; drop now-unused deps (`screenSaver`, `localCoverRenderer`, `SourceCover` import) from this VM.
- `app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderViewModelTest.kt` — update the constructor fixture (remove `screenSaver`/`localCoverRenderer`, add `scheduler` fake/mock).

---

## Task 1: Add WorkManager + HiltWorker dependencies and DI wiring

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:114-116` (Hilt deps block)
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/komgareader/app/KomgaReaderApp.kt`

**Interfaces:**
- Produces: a working WorkManager whose `WorkerFactory` is `HiltWorkerFactory`, so `@HiltWorker` classes (Task 4) get constructor injection.

- [ ] **Step 1: Add versions + library aliases to the catalog**

In `gradle/libs.versions.toml` under `[versions]` add:

```toml
work = "2.9.1"
androidxHiltWork = "1.2.0"
```

Under `[libraries]` add:

```toml
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidxHiltWork" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "androidxHiltWork" }
```

- [ ] **Step 2: Add the dependencies to the app module**

In `app/build.gradle.kts`, in the `dependencies { }` block next to the existing Hilt lines (`implementation(libs.hilt.android)` / `ksp(libs.hilt.compiler)`), add:

```kotlin
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
```

- [ ] **Step 3: Disable the default WorkManager initializer**

Hilt must supply the `WorkerFactory`, which requires on-demand initialization. In `app/src/main/AndroidManifest.xml`, inside `<application>`, add:

```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authority="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

(`xmlns:tools` is already declared on the `<manifest>` root.)

- [ ] **Step 4: Make the Application provide the Hilt WorkerFactory**

Replace the body of `app/src/main/kotlin/com/komgareader/app/KomgaReaderApp.kt` with:

```kotlin
package com.komgareader.app

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KomgaReaderApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val manufacturer = Build.MANUFACTURER
        val isOnyx = manufacturer.equals("ONYX", ignoreCase = true)
        Log.i("KomgaReaderApp", "Gerät-Hersteller=$manufacturer | Onyx-E-Ink-Modus=${if (isOnyx) "AKTIV" else "INAKTIV (No-Op)"}")
    }
}
```

- [ ] **Step 5: Verify the app still compiles and assembles**

Run: `./gradlew :app:compileDebugKotlin :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL (no output from `-q` on success).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/kotlin/com/komgareader/app/KomgaReaderApp.kt
git commit -m "build: add WorkManager + Hilt worker factory wiring"
```

---

## Task 2: Pure screensaver-cover policy + tests

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/ScreenSaverCoverPolicy.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/data/ScreenSaverCoverPolicyTest.kt`

**Interfaces:**
- Produces:
  - `enum class ScreenSaverCoverKind { SERIES, WORK }`
  - `fun coverKindFor(viewerMode: ViewerMode): ScreenSaverCoverKind` — `WEBTOON` → `SERIES`, else `WORK`.
  - `fun needsHighResUpgrade(coverMinEdgePx: Int, screenMinEdgePx: Int): Boolean` — true when the cover's shorter edge is below half the screen's shorter edge (and the screen edge is positive). A non-positive `coverMinEdgePx` (decode failed / no cover) also returns true so the upgrade is attempted.

`ViewerMode` is the existing `com.komgareader.app.ui.reader.ViewerMode` enum.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.app.data

import com.komgareader.app.ui.reader.ViewerMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenSaverCoverPolicyTest {

    @Test fun `webtoon uses the series cover`() {
        assertEquals(ScreenSaverCoverKind.SERIES, coverKindFor(ViewerMode.WEBTOON))
    }

    @Test fun `paged and comic use the work cover`() {
        assertEquals(ScreenSaverCoverKind.WORK, coverKindFor(ViewerMode.PAGED))
        assertEquals(ScreenSaverCoverKind.WORK, coverKindFor(ViewerMode.COMIC))
    }

    @Test fun `cover far below screen needs upgrade`() {
        // 200px cover, 1264px screen short edge -> 200 < 632 -> upgrade.
        assertTrue(needsHighResUpgrade(coverMinEdgePx = 200, screenMinEdgePx = 1264))
    }

    @Test fun `cover near screen resolution does not need upgrade`() {
        // 720px cover, 1264px screen -> 720 >= 632 -> keep.
        assertFalse(needsHighResUpgrade(coverMinEdgePx = 720, screenMinEdgePx = 1264))
    }

    @Test fun `missing or undecodable cover triggers upgrade`() {
        assertTrue(needsHighResUpgrade(coverMinEdgePx = 0, screenMinEdgePx = 1264))
    }

    @Test fun `unknown screen size never forces an upgrade loop`() {
        assertFalse(needsHighResUpgrade(coverMinEdgePx = 200, screenMinEdgePx = 0))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ScreenSaverCoverPolicyTest"`
Expected: FAIL — unresolved references `ScreenSaverCoverKind`, `coverKindFor`, `needsHighResUpgrade`.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.komgareader.app.data

import com.komgareader.app.ui.reader.ViewerMode

/** Which cover the screensaver should use for a given reader type. */
enum class ScreenSaverCoverKind { SERIES, WORK }

/**
 * A Webtoon is a continuous multi-chapter strip, so a single chapter cover is arbitrary → use the
 * whole-series cover. Every other reader type (paged/comic, and EPUB/novel which opens as PAGED)
 * shows the current work's own cover.
 */
fun coverKindFor(viewerMode: ViewerMode): ScreenSaverCoverKind =
    if (viewerMode == ViewerMode.WEBTOON) ScreenSaverCoverKind.SERIES else ScreenSaverCoverKind.WORK

/**
 * Whether the server cover is too low-resolution for the standby screen and a high-res upgrade
 * (full first page / whole-file extraction) is worth fetching. True when the cover's shorter edge is
 * below half the screen's shorter edge — a 200px cover upscaled to a ~1264px-wide standby looks
 * blurry, while a ~720px cover holds up. A non-positive [coverMinEdgePx] (no/undecodable cover) also
 * returns true so the upgrade is attempted; a non-positive [screenMinEdgePx] (unknown) returns false
 * so we never loop on a bad metric.
 */
fun needsHighResUpgrade(coverMinEdgePx: Int, screenMinEdgePx: Int): Boolean {
    if (screenMinEdgePx <= 0) return false
    if (coverMinEdgePx <= 0) return true
    return coverMinEdgePx < screenMinEdgePx / 2
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ScreenSaverCoverPolicyTest"`
Expected: PASS (BUILD SUCCESSFUL).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/ScreenSaverCoverPolicy.kt app/src/test/kotlin/com/komgareader/app/data/ScreenSaverCoverPolicyTest.kt
git commit -m "feat(screensaver): pure cover-selection + low-res-upgrade policy"
```

---

## Task 3: ScreenSaverCoverResolver (server baseline + high-res upgrade)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/ScreenSaverCoverResolver.kt`

**Interfaces:**
- Consumes: `coverKindFor`, `needsHighResUpgrade`, `ScreenSaverCoverKind` (Task 2); `SourceManager.get(sourceId): MediaSource?`; `BrowsableSource.coverBytes`, `.seriesIdOf`, `.pages`, `.openPage`, `.downloadFile`; `ScreenSaverManager.applyBytes(ByteArray): Boolean`; `LocalCoverRenderer.render(SourceCover): ByteArray?`; `renderFirstPageCover(factory, bytes, formatHint): ByteArray?` (from `com.komgareader.data.cover`); `extractEpubCoverImage(bytes): ByteArray?` (from `com.komgareader.source.local`); `DocumentFactory`; `SettingsRepository.screenSaverMode`.
- Produces: `suspend fun refresh(sourceId: Long, bookRemoteId: String, viewerMode: ViewerMode, format: BookFormat)` — resolves + applies the screensaver cover; safe to call off the reader path. No-op unless screensaver mode is `BOOK_COVER`.

- [ ] **Step 1: Create the resolver**

```kotlin
package com.komgareader.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.app.ui.reader.ViewerMode
import com.komgareader.data.cover.renderFirstPageCover
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ScreenSaverMode
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import com.komgareader.source.local.extractEpubCoverImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves and applies the device standby cover, off the reader-open path (driven by
 * [com.komgareader.app.work.ScreenSaverCoverWorker]). Two stages, the first being the crash fallback:
 *
 * 1. **Baseline:** the source's own cover — series poster for Webtoon, else the work's book cover —
 *    applied immediately. If anything below crashes, this image is already the standby.
 * 2. **Upgrade (only when the baseline is much smaller than the screen):** a high-resolution cover.
 *    Streamable image works expose the full first page via [BrowsableSource.openPage] (cheap, native
 *    resolution = the cover). Whole-file works without streamable pages (EPUB/PDF/CBR) are downloaded
 *    once and the cover is extracted: the embedded image for EPUB, MuPDF page-0 render otherwise.
 *
 * Webtoon keeps the series poster (a chapter's first page is the strip top, not a poster) and is not
 * upgraded via first page. All steps are wrapped so a failure never propagates past the worker.
 */
@Singleton
class ScreenSaverCoverResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sources: SourceManager,
    private val documentFactory: DocumentFactory,
    private val localCoverRenderer: LocalCoverRenderer,
    private val screenSaver: ScreenSaverManager,
    private val settings: SettingsRepository,
) {
    suspend fun refresh(sourceId: Long, bookRemoteId: String, viewerMode: ViewerMode, format: BookFormat) {
        val mode = runCatching { ScreenSaverMode.valueOf(settings.screenSaverMode.first()) }
            .getOrDefault(ScreenSaverMode.OFF)
        if (mode != ScreenSaverMode.BOOK_COVER) return
        val source = sources.get(sourceId) as? BrowsableSource ?: return

        val kind = coverKindFor(viewerMode)
        val baseline = baselineCover(source, bookRemoteId, kind)
        if (baseline != null && baseline.isNotEmpty()) {
            screenSaver.applyBytes(baseline) // crash fallback: standby is set before any upgrade work
        }

        if (kind == ScreenSaverCoverKind.SERIES) return // series poster is the cover; no first-page upgrade
        if (!needsHighResUpgrade(minEdgeOf(baseline), screenMinEdge())) return

        val hi = highResWorkCover(source, bookRemoteId, format)
        if (hi != null && hi.isNotEmpty()) {
            Log.i(TAG, "screensaver upgraded to high-res cover (${hi.size} bytes)")
            screenSaver.applyBytes(hi)
        }
    }

    /** Stage-1 cover: series poster (Webtoon) or the work's book cover, with the local render fallback. */
    private suspend fun baselineCover(source: BrowsableSource, bookRemoteId: String, kind: ScreenSaverCoverKind): ByteArray? =
        when (kind) {
            ScreenSaverCoverKind.SERIES -> {
                val seriesId = runCatching { source.seriesIdOf(bookRemoteId) }.getOrNull()
                val poster = seriesId?.let { runCatching { source.coverBytes(it, isSeriesCover = true) }.getOrNull() }
                poster?.takeIf { it.isNotEmpty() }
                    ?: seriesId?.let { localCoverRenderer.render(SourceCover(source.id, it, isSeries = true)) }
            }
            ScreenSaverCoverKind.WORK -> {
                val cover = runCatching { source.coverBytes(bookRemoteId, isSeriesCover = false) }.getOrNull()
                cover?.takeIf { it.isNotEmpty() }
                    ?: localCoverRenderer.render(SourceCover(source.id, bookRemoteId, isSeries = false))
            }
        }

    /** Stage-2 high-res cover for a work: full first page if streamable, else whole-file extraction. */
    private suspend fun highResWorkCover(source: BrowsableSource, bookRemoteId: String, format: BookFormat): ByteArray? {
        runCatching { source.pages(bookRemoteId).firstOrNull() }.getOrNull()?.let { ref ->
            runCatching { source.openPage(ref) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val whole = runCatching { source.downloadFile(bookRemoteId) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: return null
        return if (format == BookFormat.EPUB) {
            extractEpubCoverImage(whole)
        } else {
            renderFirstPageCover(documentFactory, whole, ".${format.name.lowercase()}")
        }
    }

    /** Shorter edge (px) of the encoded image via a bounds-only decode; 0 if it can't be decoded. */
    private fun minEdgeOf(bytes: ByteArray?): Int {
        if (bytes == null || bytes.isEmpty()) return 0
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return 0
        return minOf(opts.outWidth, opts.outHeight)
    }

    private fun screenMinEdge(): Int {
        val dm = context.resources.displayMetrics
        return minOf(dm.widthPixels, dm.heightPixels)
    }

    private companion object {
        const val TAG = "ScreenSaverCover"
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

If `extractEpubCoverImage` is not resolvable, confirm its package with:
`grep -rn "fun extractEpubCoverImage" source-local/src/main` → it is `com.komgareader.source.local.extractEpubCoverImage` (top-level). The app module already depends on `:source-local`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/ScreenSaverCoverResolver.kt
git commit -m "feat(screensaver): resolver with server baseline + high-res upgrade"
```

---

## Task 4: ScreenSaverCoverWorker (HiltWorker)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/work/ScreenSaverCoverWorker.kt`

**Interfaces:**
- Consumes: `ScreenSaverCoverResolver.refresh(...)` (Task 3).
- Produces: `ScreenSaverCoverWorker` with input-data keys `KEY_SOURCE_ID` (Long), `KEY_BOOK_ID` (String), `KEY_VIEWER_MODE` (String), `KEY_FORMAT` (String) — consumed by the scheduler in Task 5.

- [ ] **Step 1: Create the worker**

```kotlin
package com.komgareader.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.komgareader.app.data.ScreenSaverCoverResolver
import com.komgareader.app.ui.reader.ViewerMode
import com.komgareader.domain.model.BookFormat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Generates the device standby cover off the reader-open path. Enqueued when a book opens (see
 * [ScreenSaverScheduler]); delegates to [ScreenSaverCoverResolver], which sets a baseline server
 * cover first (the crash fallback) and then upgrades to a high-res cover when warranted. Always
 * returns success — the screensaver is best-effort and must never surface a failure or block reading.
 */
@HiltWorker
class ScreenSaverCoverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val resolver: ScreenSaverCoverResolver,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.success()
        if (sourceId < 0L) return Result.success()
        val viewerMode = runCatching { ViewerMode.valueOf(inputData.getString(KEY_VIEWER_MODE) ?: "PAGED") }
            .getOrDefault(ViewerMode.PAGED)
        val format = runCatching { BookFormat.valueOf(inputData.getString(KEY_FORMAT) ?: "CBZ") }
            .getOrDefault(BookFormat.CBZ)
        runCatching { resolver.refresh(sourceId, bookId, viewerMode, format) }
        return Result.success()
    }

    companion object {
        const val KEY_SOURCE_ID = "sourceId"
        const val KEY_BOOK_ID = "bookId"
        const val KEY_VIEWER_MODE = "viewerMode"
        const val KEY_FORMAT = "format"
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/work/ScreenSaverCoverWorker.kt
git commit -m "feat(screensaver): HiltWorker wrapping the cover resolver"
```

---

## Task 5: Scheduler + wire ReaderViewModel to enqueue

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/work/ScreenSaverScheduler.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes: `ScreenSaverCoverWorker.KEY_*` (Task 4).
- Produces: `ScreenSaverScheduler.schedule(sourceId: Long, bookRemoteId: String, viewerMode: ViewerMode, format: BookFormat)` — enqueues one unique worker (REPLACE), so only the most recently opened book's cover is generated.

- [ ] **Step 1: Create the scheduler**

```kotlin
package com.komgareader.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.komgareader.app.ui.reader.ViewerMode
import com.komgareader.domain.model.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the [ScreenSaverCoverWorker] when a book opens. Uniquely named with REPLACE so opening a
 * new book supersedes any still-running cover job — only the latest book's cover matters for the
 * standby. Runs entirely off the reader-open path.
 */
@Singleton
class ScreenSaverScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(sourceId: Long, bookRemoteId: String, viewerMode: ViewerMode, format: BookFormat) {
        val request = OneTimeWorkRequestBuilder<ScreenSaverCoverWorker>()
            .setInputData(
                workDataOf(
                    ScreenSaverCoverWorker.KEY_SOURCE_ID to sourceId,
                    ScreenSaverCoverWorker.KEY_BOOK_ID to bookRemoteId,
                    ScreenSaverCoverWorker.KEY_VIEWER_MODE to viewerMode.name,
                    ScreenSaverCoverWorker.KEY_FORMAT to format.name,
                ),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val UNIQUE_NAME = "screensaver-cover"
    }
}
```

- [ ] **Step 2: Replace the inline screensaver code in ReaderViewModel — remove the old helpers**

In `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt`:

Delete the `updateScreenSaverCover()` function and its helpers `bookCoverBytes(...)`, `firstPageBytes(...)`, `seriesCoverBytes(...)` entirely (the whole block from the `/** When the screensaver ... */` KDoc down to the end of `seriesCoverBytes`).

Remove these now-unused imports:

```kotlin
import com.komgareader.app.data.LocalCoverRenderer
import com.komgareader.app.data.ScreenSaverManager
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.domain.model.ScreenSaverMode
```

(Keep `import android.util.Log` only if still used elsewhere in the file; if not, remove it too. Verify with `grep -n "Log\." app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt`.)

- [ ] **Step 3: Swap the constructor dependencies**

In the `ReaderViewModel` constructor, remove:

```kotlin
    private val screenSaver: ScreenSaverManager,
    private val localCoverRenderer: LocalCoverRenderer,
```

and add (next to the other injected deps):

```kotlin
    private val screenSaverScheduler: com.komgareader.app.work.ScreenSaverScheduler,
```

- [ ] **Step 4: Enqueue from init instead of doing work inline**

In the `init { }` block, replace the line `updateScreenSaverCover()` with:

```kotlin
        screenSaverScheduler.schedule(routeSourceId, bookId, initialViewerMode, format)
```

(`routeSourceId`, `bookId`, `initialViewerMode`, and `format` are existing properties on the VM.)

- [ ] **Step 5: Update the test fixture**

In `app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderViewModelTest.kt`, in the `readerVm(...)` helper, remove the two lines:

```kotlin
            screenSaver = mockk(relaxed = true),
            localCoverRenderer = mockk(relaxed = true),
```

and add:

```kotlin
            screenSaverScheduler = mockk(relaxed = true),
```

- [ ] **Step 6: Run the reader VM tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*ReaderViewModelTest"`
Expected: PASS (BUILD SUCCESSFUL). A `relaxed` mock means `schedule(...)` is a no-op in tests.

- [ ] **Step 7: Full compile + assemble**

Run: `./gradlew :app:compileDebugKotlin :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/work/ScreenSaverScheduler.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderViewModelTest.kt
git commit -m "feat(screensaver): enqueue cover worker on book open instead of inline"
```

---

## Task 6: On-device verification (Boox) + docs

**Files:**
- Modify: `.claude/rules/architecture-seams.md` (screensaver section)

**Interfaces:** none (verification + docs).

- [ ] **Step 1: Install the debug build alongside the release**

Run:
```bash
./gradlew :app:assembleDebug -q
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Success`. (Debug package is `com.komgareader.app.debug`; release data untouched.)

- [ ] **Step 2: Confirm the reader opens without the screensaver work blocking it**

Open a book; the first page should appear promptly. Then check the worker ran:
```bash
adb logcat -d ScreenSaverCover:* OnyxEinkController:* '*:S' | tail -20
```
Expected: an `OnyxEinkController: setScreenSaverImage(...) -> true` line shortly AFTER the page is visible (worker is off the open path). For a low-res-thumbnail manga (e.g. One-Punch Man) an additional `ScreenSaverCover: screensaver upgraded to high-res cover (...)` line appears.

- [ ] **Step 3: Verify the high-res upgrade for a novel (EPUB)**

Open a Warhammer (EPUB) novel. Pull the published standby image and confirm it is sharp / high-resolution:
```bash
f=$(adb shell ls -t /storage/emulated/0/Pictures/KomgaReader/ | tr -d '\r' | head -1)
adb pull "/storage/emulated/0/Pictures/KomgaReader/$f" /tmp/ss_novel.png
python3 -c "import struct;d=open('/tmp/ss_novel.png','rb');d.read(16);print('dims',struct.unpack('>II',d.read(8)))"
```
Expected: `dims (1264, 1680)` and, viewed, a sharp embedded cover (not the 195×300 thumbnail upscaled). The upgrade path downloaded the EPUB and ran `extractEpubCoverImage`.

- [ ] **Step 4: Verify the crash fallback semantics**

Confirm by reading the code path: the baseline (`screenSaver.applyBytes(baseline)`) is awaited before any upgrade work, so a thrown error during `highResWorkCover` (caught by `runCatching` in the worker) leaves the baseline server cover as the standby. No device step required beyond observing that opening a book with no network (upgrade fails) still sets *a* cover when a baseline thumbnail is cached.

- [ ] **Step 5: Update the architecture doc**

In `.claude/rules/architecture-seams.md`, in the screensaver paragraph, replace the description of inline `ReaderViewModel` cover-setting with: the cover is generated by `ScreenSaverCoverWorker` (WorkManager/HiltWorker), enqueued on book open via `ScreenSaverScheduler`; `ScreenSaverCoverResolver` applies the server cover as a baseline (crash fallback) and upgrades to a high-res cover (full first page, or whole-file download + `extractEpubCoverImage`/MuPDF render) when the server cover's shorter edge is below half the screen's. Per type: Webtoon = series poster (no upgrade), else work cover.

- [ ] **Step 6: Commit**

```bash
git add .claude/rules/architecture-seams.md
git commit -m "docs: screensaver cover now generated by a background worker"
```

---

## Self-Review Notes

- **Spec coverage:** off-reader-path worker (Tasks 4–5) ✓; server cover first as fallback (Task 3 baseline-before-upgrade) ✓; load whole work + extract when server cover much smaller than screen (Task 3 `highResWorkCover` + Task 2 threshold) ✓; crash fallback = first server cover (Task 3 ordering, Task 4 `runCatching`) ✓.
- **Novel sharpness** is fixed as a side effect: EPUB has no streamable pages, so the upgrade downloads the file and runs `extractEpubCoverImage` (the high-res embedded cover) — acceptable now that it is off the open path.
- **Type consistency:** `coverKindFor`/`needsHighResUpgrade`/`ScreenSaverCoverKind` (Task 2) are used verbatim in Task 3; worker input keys `KEY_SOURCE_ID/KEY_BOOK_ID/KEY_VIEWER_MODE/KEY_FORMAT` (Task 4) match the scheduler's `workDataOf` (Task 5); `ScreenSaverScheduler.schedule(...)` signature matches the `ReaderViewModel` call site.
- **Not multi-process:** crash isolation is logical (baseline-first), not a separate OS process. If a future need arises to isolate native (MuPDF) crashes during whole-file extraction, escalate to `androidx.work:work-multiprocess` with a `:screensaver` process — out of scope here.
