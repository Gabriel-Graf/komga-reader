# Content-Type Auto-Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Schlage den Inhaltstyp (MANGA/COMIC/WEBTOON) einer Serie automatisch aus echten Bild-Signalen vor und fülle damit die manga-vs-comic-Lücke in der Viewer-Auflösung, ohne deren Determinismus aufzuweichen.

**Architecture:** Reine Entscheidungslogik + reine Mess-Helfer in `domain` (quellen-agnostisch, JVM-unit-getestet). Imperative Bild-Extraktion (Android `BitmapFactory`) als dünne Shell in `app`. Vorschlag persistiert in eigener Room-Tabelle (parallel zum manuellen Override) und fließt als neue, niedrig-priorisierte Stufe in `ResolveViewerType` — manueller Override + Server-`readingDirection` + Bibliotheks-Default schlagen ihn weiter.

**Tech Stack:** Kotlin, Room, Hilt, Jetpack Compose, JUnit/kotlin.test. Kein neues Dependency.

## Global Constraints

- Code-facing Artefakte (Kommentare/KDoc/Doku/Commits) **immer Englisch**; UI-Text via i18n **de+en**.
- Echte Umlaute/ß überall wo Deutsch.
- `domain` hängt **nicht** an Android/Netz/Quelle. Reine Logik dort, pure Unit-Tests.
- Naht A bleibt unverletzt: kein konkreter Quellen-Typ in `domain`/ViewModel-Konstruktor; Bild-Bytes über `BrowsableSource.openPage`/`downloadFile`.
- Room-Migration **additiv** mit `@ColumnInfo`-Paritаt — destruktiven Wipe vermeiden (`room-migration-destructive-pitfall`).
- E-Ink-Designsprache + Animation-Gating bei jeder UI-Arbeit.
- Invariante 4 (deterministische Viewer-Auflösung) bleibt: Auto-Erkennung ist nur ein **vorab-persistierter Vorschlag** in eigener Stufe.

---

## File Structure

**domain (pure):**
- Create `domain/.../model/ContentSignals.kt` — `PageSample`, `ContentSignals` Wertobjekte.
- Create `domain/.../usecase/MeasureGrayFraction.kt` — reine Pixel-Graufraktion über `IntArray`.
- Create `domain/.../usecase/SuggestContentType.kt` — reine Kaskade `ContentSignals → ContentType?`.
- Modify `domain/.../usecase/ResolveViewerType.kt` — neuer Param `auto`, neue Stufe 5.
- Create `domain/.../repository/SeriesAutoTypeRepository.kt` — Persistenz-Interface.

**data:**
- Modify `data/.../db/Entities.kt` — `SeriesAutoTypeEntity`.
- Create `data/.../db/SeriesAutoTypeDao.kt`.
- Modify `data/.../db/AppDatabase.kt` — Entity+DAO, v20→21, `MIGRATION_20_21`.
- Create `data/.../repository/RoomSeriesAutoTypeRepository.kt`.
- Modify `data/.../di/DataModule.kt` — DAO+Repo bereitstellen, Migration registrieren.

**app (shell):**
- Create `app/.../data/ContentTypeDetector.kt` — @Singleton: sampelt Seiten, decode, misst, schlägt vor, persistiert.
- Modify `app/.../ui/series/SeriesDetailViewModel.kt` — Auto-Typ lesen + an `resolveViewerType` reichen + Detektion lazy triggern.

**docs:**
- Modify `docs/domain/viewer-type-resolution.md`, `.claude/skills/komga-viewer-type-resolution/SKILL.md`, `CLAUDE.md` (Invariante 4).

---

## Task 1: Reine Graufraktion-Messung (domain)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/MeasureGrayFraction.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/MeasureGrayFractionTest.kt`

**Interfaces:**
- Produces: `fun measureGrayFraction(pixels: IntArray, satEps: Int = 16): Float` — Anteil (0f..1f) der ARGB-Pixel, deren Sättigung (`max(r,g,b) - min(r,g,b)`) `< satEps`. Leeres Array → `0f`.

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasureGrayFractionTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun `all-gray pixels yield 1`() {
        val px = intArrayOf(argb(10, 10, 10), argb(200, 200, 200), argb(0, 0, 0))
        assertEquals(1f, measureGrayFraction(px))
    }

    @Test fun `all-colorful pixels yield 0`() {
        val px = intArrayOf(argb(200, 10, 10), argb(10, 200, 10), argb(10, 10, 200))
        assertEquals(0f, measureGrayFraction(px))
    }

    @Test fun `half gray yields one half`() {
        val px = intArrayOf(argb(50, 50, 50), argb(200, 10, 10))
        assertEquals(0.5f, measureGrayFraction(px))
    }

    @Test fun `near-gray within eps counts as gray`() {
        val px = intArrayOf(argb(100, 108, 95)) // span 13 < 16
        assertEquals(1f, measureGrayFraction(px))
    }

    @Test fun `empty array yields zero`() {
        assertEquals(0f, measureGrayFraction(intArrayOf()))
    }
}
```

- [ ] **Step 2: Run, verify FAIL** — `./gradlew :domain:test --tests "*MeasureGrayFractionTest"` → unresolved reference `measureGrayFraction`.

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.domain.usecase

/**
 * Fraction (0f..1f) of ARGB [pixels] that are effectively grayscale: channel span
 * `max(r,g,b) - min(r,g,b)` below [satEps]. Pure; usable on any pixel buffer. Empty
 * buffer yields 0f (no evidence either way — callers treat as "undecidable").
 */
fun measureGrayFraction(pixels: IntArray, satEps: Int = 16): Float {
    if (pixels.isEmpty()) return 0f
    var gray = 0
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val span = maxOf(r, g, b) - minOf(r, g, b)
        if (span < satEps) gray++
    }
    return gray.toFloat() / pixels.size
}
```

- [ ] **Step 4: Run, verify PASS** — `./gradlew :domain:test --tests "*MeasureGrayFractionTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/usecase/MeasureGrayFraction.kt domain/src/test/kotlin/com/komgareader/domain/usecase/MeasureGrayFractionTest.kt
git commit -m "feat(domain): pure grayscale-fraction pixel measure"
```

---

## Task 2: Signal-Wertobjekte + reine Vorschlags-Kaskade (domain)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ContentSignals.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/SuggestContentType.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/SuggestContentTypeTest.kt`

**Interfaces:**
- Consumes: nichts aus früheren Tasks (rein über Wertobjekte).
- Produces:
  - `data class PageSample(val widthPx: Int, val heightPx: Int, val grayFraction: Float)`
  - `data class ContentSignals(val samples: List<PageSample>)`
  - `class SuggestContentType { operator fun invoke(signals: ContentSignals): ContentType? }`
  - Konstanten: `WEBTOON_MIN_ASPECT = 3.0f`, `MANGA_MIN_GRAY = 0.92f`, `COMIC_MAX_GRAY = 0.60f`.

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentSignals
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.PageSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuggestContentTypeTest {
    private val suggest = SuggestContentType()
    private fun signals(vararg s: PageSample) = ContentSignals(s.toList())
    // 1000x1500 = aspect 1.5 (normal page); 800x4000 = aspect 5 (strip)
    private fun page(w: Int, h: Int, gray: Float) = PageSample(w, h, gray)

    @Test fun `empty samples yield null`() {
        assertNull(suggest(ContentSignals(emptyList())))
    }

    @Test fun `tall strips yield WEBTOON`() {
        val r = suggest(signals(page(800, 4000, 0.9f), page(800, 5000, 0.2f)))
        assertEquals(ContentType.WEBTOON, r)
    }

    @Test fun `grayscale normal pages yield MANGA`() {
        val r = suggest(signals(page(1000, 1500, 0.98f), page(1000, 1500, 0.95f), page(1000, 1500, 0.99f)))
        assertEquals(ContentType.MANGA, r)
    }

    @Test fun `colorful normal pages yield COMIC`() {
        val r = suggest(signals(page(1000, 1500, 0.2f), page(1000, 1500, 0.1f), page(1000, 1500, 0.3f)))
        assertEquals(ContentType.COMIC, r)
    }

    @Test fun `ambiguous mid-saturation yields null`() {
        val r = suggest(signals(page(1000, 1500, 0.75f), page(1000, 1500, 0.8f)))
        assertNull(r)
    }

    @Test fun `aspect ratio uses median so one tall outlier does not flip`() {
        // two normal grayscale pages + one tall outlier -> median aspect ~1.5 -> not webtoon, gray -> MANGA
        val r = suggest(signals(page(1000, 1500, 0.97f), page(1000, 1500, 0.96f), page(800, 6000, 0.9f)))
        assertEquals(ContentType.MANGA, r)
    }
}
```

- [ ] **Step 2: Run, verify FAIL** — `./gradlew :domain:test --tests "*SuggestContentTypeTest"`.

- [ ] **Step 3a: ContentSignals.kt**

```kotlin
package com.komgareader.domain.model

/** One sampled interior page: pixel dimensions + grayscale fraction (0f..1f). */
data class PageSample(val widthPx: Int, val heightPx: Int, val grayFraction: Float)

/**
 * Pixel-derived signals for content-type detection. Source-agnostic: any source's
 * sampled interior pages feed this; the decision is pure (see [com.komgareader.domain.usecase.SuggestContentType]).
 */
data class ContentSignals(val samples: List<PageSample>)
```

- [ ] **Step 3b: SuggestContentType.kt**

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentSignals
import com.komgareader.domain.model.ContentType

/**
 * Pure content-type suggestion from sampled interior pages. Returns `null` when the
 * evidence is inconclusive — callers must NOT guess. Order strong → weak:
 *   1. median page aspect (h/w) >= [WEBTOON_MIN_ASPECT]  -> WEBTOON (long strips)
 *   2. median gray fraction    >= [MANGA_MIN_GRAY]       -> MANGA  (B/W interior)
 *   3. median gray fraction    <= [COMIC_MAX_GRAY]       -> COMIC  (coloured interior)
 *   4. otherwise -> null (ambiguous mid band)
 *
 * Medians (not means) so a single coloured spread / tall outlier cannot flip the verdict.
 * Grayscale decides B/W-vs-colour ONLY — never reading direction (RTL stays metadata-driven).
 */
class SuggestContentType {
    operator fun invoke(signals: ContentSignals): ContentType? {
        val samples = signals.samples
        if (samples.isEmpty()) return null
        val medianAspect = samples
            .map { it.heightPx.toFloat() / it.widthPx.toFloat() }
            .median()
        if (medianAspect >= WEBTOON_MIN_ASPECT) return ContentType.WEBTOON
        val medianGray = samples.map { it.grayFraction }.median()
        if (medianGray >= MANGA_MIN_GRAY) return ContentType.MANGA
        if (medianGray <= COMIC_MAX_GRAY) return ContentType.COMIC
        return null
    }

    private fun List<Float>.median(): Float {
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    companion object {
        const val WEBTOON_MIN_ASPECT = 3.0f
        const val MANGA_MIN_GRAY = 0.92f
        const val COMIC_MAX_GRAY = 0.60f
    }
}
```

- [ ] **Step 4: Run, verify PASS.**

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ContentSignals.kt domain/src/main/kotlin/com/komgareader/domain/usecase/SuggestContentType.kt domain/src/test/kotlin/com/komgareader/domain/usecase/SuggestContentTypeTest.kt
git commit -m "feat(domain): pure content-type suggestion cascade from page samples"
```

---

## Task 3: `ResolveViewerType` neue Auto-Stufe (domain)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt`

**Interfaces:**
- Produces: `operator fun invoke(series, book, fallback: ContentType?, auto: ContentType? = null): ViewerType`. Neue Stufe **5** (auto) zwischen Bibliotheks-Default (4) und Format-Default (jetzt 6). Default `auto = null` → bestehende Call-Sites/Tests kompilieren unverändert.

- [ ] **Step 1: Failing tests** — anhängen an `ResolveViewerTypeTest.kt`:

```kotlin
    @Test fun `Stufe 5 — Auto-Vorschlag greift wenn kein Override Richtung oder Bibliothek`() {
        val result = resolve(series(), book(BookFormat.CBZ), fallback = null, auto = ContentType.MANGA)
        assertEquals(ViewerType.PAGED, result) // MANGA -> PAGED
    }

    @Test fun `Stufe 5 — Auto-Vorschlag WEBTOON ergibt WEBTOON bei CBZ`() {
        val result = resolve(series(), book(BookFormat.CBZ), fallback = null, auto = ContentType.WEBTOON)
        assertEquals(ViewerType.WEBTOON, result)
    }

    @Test fun `Bibliotheks-Default schlaegt Auto-Vorschlag`() {
        val result = resolve(series(), book(BookFormat.CBZ), fallback = ContentType.COMIC, auto = ContentType.MANGA)
        assertEquals(ViewerType.COMIC, result)
    }

    @Test fun `manueller Override schlaegt Auto-Vorschlag`() {
        val result = resolve(series(override = ContentType.COMIC), book(BookFormat.CBZ), fallback = null, auto = ContentType.WEBTOON)
        assertEquals(ViewerType.COMIC, result)
    }

    @Test fun `Server-Leserichtung schlaegt Auto-Vorschlag`() {
        val result = resolve(series(direction = ReadingDirection.WEBTOON), book(BookFormat.CBZ), fallback = null, auto = ContentType.MANGA)
        assertEquals(ViewerType.WEBTOON, result)
    }
```

Helper `resolve` ist `private val resolve = ResolveViewerType()` und wird `operator`-aufgerufen → die neuen Aufrufe mit `auto =` nutzen den Default-Param. Falls der bestehende Test-Helper eine eigene Wrapper-Funktion hätte: hier wird `resolve(...)` direkt als `ResolveViewerType.invoke` aufgerufen, kein Wrapper nötig.

- [ ] **Step 2: Run, verify FAIL** — `./gradlew :domain:test --tests "*ResolveViewerTypeTest"` → no value passed for `auto` is fine (default), tests fail because logic ignores `auto` (z. B. `Stufe 5 WEBTOON` liefert PAGED).

- [ ] **Step 3: Implement** — `ResolveViewerType.invoke` ändern:

```kotlin
    operator fun invoke(
        series: Series,
        book: Book,
        fallback: ContentType?,
        auto: ContentType? = null,
    ): ViewerType {
        series.contentTypeOverride?.let { return map(it) }
        if (book.format == BookFormat.EPUB) return ViewerType.NOVEL
        if (series.readingDirection == ReadingDirection.VERTICAL ||
            series.readingDirection == ReadingDirection.WEBTOON
        ) {
            return ViewerType.WEBTOON
        }
        fallback?.let { return map(it) }
        auto?.let { return map(it) }
        if (book.format == BookFormat.CBZ ||
            book.format == BookFormat.CBR ||
            book.format == BookFormat.PDF
        ) {
            return ViewerType.PAGED
        }
        return ViewerType.PAGED
    }
```

KDoc oben (Stufen-Liste) auf 7 Stufen aktualisieren (auto = Stufe 5, Renummerierung).

- [ ] **Step 4: Run, verify PASS** — `./gradlew :domain:test --tests "*ResolveViewerTypeTest"`.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt
git commit -m "feat(domain): add auto-suggestion stage to ResolveViewerType"
```

---

## Task 4: Persistenz-Interface (domain)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/repository/SeriesAutoTypeRepository.kt`

**Interfaces:**
- Produces:
```kotlin
interface SeriesAutoTypeRepository {
    suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType?
    suspend fun detectorVersion(sourceId: Long, seriesRemoteId: String): Int?
    suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?, detectorVersion: Int)
}
```

- [ ] **Step 1: Create file** (kein eigener Unit-Test — reines Interface; Verhalten wird in Room-Task gebaut)

```kotlin
package com.komgareader.domain.repository

import com.komgareader.domain.model.ContentType

/**
 * Persists the *auto-detected* content-type suggestion per series (source-agnostic via
 * [sourceId] + [seriesRemoteId]). Separate from the manual SeriesOverrideRepository: this
 * is a heuristic guess that the user / server metadata / library tag all outrank. The
 * stored [detectorVersion] lets re-detection be idempotent and re-runnable after an
 * algorithm bump without touching manual overrides.
 */
interface SeriesAutoTypeRepository {
    /** Detected type, or `null` if none stored. */
    suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType?

    /** Detector version that produced the stored value, or `null` if none stored. */
    suspend fun detectorVersion(sourceId: Long, seriesRemoteId: String): Int?

    /** Stores ([type] != null) or clears ([type] == null) the detected type. */
    suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?, detectorVersion: Int)
}
```

- [ ] **Step 2: Build** — `./gradlew :domain:compileKotlin` → success.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/SeriesAutoTypeRepository.kt
git commit -m "feat(domain): SeriesAutoTypeRepository interface"
```

---

## Task 5: Room-Persistenz — Entity, DAO, Migration, Repo, DI (data)

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/db/SeriesAutoTypeDao.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/repository/RoomSeriesAutoTypeRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`
- Test: `data/src/androidTest/kotlin/com/komgareader/data/db/SeriesAutoTypeDaoTest.kt`

**Interfaces:**
- Consumes: `SeriesAutoTypeRepository` (Task 4).
- Produces: Tabelle `series_auto_types(sourceId, seriesRemoteId, contentType, detectorVersion)`, PK `(sourceId, seriesRemoteId)`; `RoomSeriesAutoTypeRepository`; `MIGRATION_20_21`; DB-Version **21**.

- [ ] **Step 1: Entity** — an `Entities.kt` anhängen (neben `SeriesOverrideEntity`):

```kotlin
@Entity(tableName = "series_auto_types", primaryKeys = ["sourceId", "seriesRemoteId"])
data class SeriesAutoTypeEntity(
    val sourceId: Long,
    val seriesRemoteId: String,
    /** ContentType enum name; row absent when undecidable. */
    val contentType: String,
    val detectorVersion: Int,
)
```

- [ ] **Step 2: DAO** — `SeriesAutoTypeDao.kt`:

```kotlin
package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SeriesAutoTypeDao {
    @Query("SELECT * FROM series_auto_types WHERE sourceId = :sourceId AND seriesRemoteId = :seriesRemoteId")
    suspend fun get(sourceId: Long, seriesRemoteId: String): SeriesAutoTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: SeriesAutoTypeEntity)

    @Query("DELETE FROM series_auto_types WHERE sourceId = :sourceId AND seriesRemoteId = :seriesRemoteId")
    suspend fun delete(sourceId: Long, seriesRemoteId: String)
}
```

- [ ] **Step 3: AppDatabase** — Entity in `entities = [...]` ergänzen, `version = 21`, `abstract fun seriesAutoTypeDao(): SeriesAutoTypeDao`, und `MIGRATION_20_21` neben `MIGRATION_19_20`:

```kotlin
/** v20 -> v21: series_auto_types (detected content-type suggestion per series). */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `series_auto_types` (
                `sourceId` INTEGER NOT NULL,
                `seriesRemoteId` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `detectorVersion` INTEGER NOT NULL,
                PRIMARY KEY(`sourceId`, `seriesRemoteId`)
            )""".trimIndent(),
        )
    }
}
```

- [ ] **Step 4: Repo impl** — `RoomSeriesAutoTypeRepository.kt`:

```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.SeriesAutoTypeDao
import com.komgareader.data.db.SeriesAutoTypeEntity
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.repository.SeriesAutoTypeRepository

class RoomSeriesAutoTypeRepository(private val dao: SeriesAutoTypeDao) : SeriesAutoTypeRepository {
    override suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType? =
        dao.get(sourceId, seriesRemoteId)?.let { runCatching { ContentType.valueOf(it.contentType) }.getOrNull() }

    override suspend fun detectorVersion(sourceId: Long, seriesRemoteId: String): Int? =
        dao.get(sourceId, seriesRemoteId)?.detectorVersion

    override suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?, detectorVersion: Int) {
        if (type == null) dao.delete(sourceId, seriesRemoteId)
        else dao.put(SeriesAutoTypeEntity(sourceId, seriesRemoteId, type.name, detectorVersion))
    }
}
```

- [ ] **Step 5: DI** — in `DataModule.kt`: Migration `MIGRATION_20_21` in `addMigrations(...)` aufnehmen; DAO + Repo bereitstellen (gleiches Muster wie `seriesOverrideDao`/`RoomSeriesOverrideRepository` — exakt nachschlagen und spiegeln).

- [ ] **Step 6: DAO androidTest** (spiegelt vorhandene DAO-Tests, z. B. `DownloadDaoSourceIdTest`):

```kotlin
package com.komgareader.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SeriesAutoTypeDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SeriesAutoTypeDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.seriesAutoTypeDao()
    }
    @After fun tearDown() = db.close()

    @Test fun put_then_get_roundtrips() = runBlocking {
        dao.put(SeriesAutoTypeEntity(1L, "S1", "MANGA", 1))
        val e = dao.get(1L, "S1")
        assertEquals("MANGA", e?.contentType)
        assertEquals(1, e?.detectorVersion)
    }

    @Test fun delete_removes_row() = runBlocking {
        dao.put(SeriesAutoTypeEntity(1L, "S1", "COMIC", 1))
        dao.delete(1L, "S1")
        assertNull(dao.get(1L, "S1"))
    }
}
```

- [ ] **Step 7: Build + test** — `./gradlew :data:compileDebugKotlin` (compile) + `:data:test`. androidTest braucht Emulator; falls keiner läuft, mit Build-Erfolg als Beleg notieren und androidTest als geräte-gebunden markieren.

- [ ] **Step 8: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/Entities.kt data/src/main/kotlin/com/komgareader/data/db/SeriesAutoTypeDao.kt data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt data/src/main/kotlin/com/komgareader/data/repository/RoomSeriesAutoTypeRepository.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/androidTest/kotlin/com/komgareader/data/db/SeriesAutoTypeDaoTest.kt
git commit -m "feat(data): persist detected content-type in series_auto_types (v20->21)"
```

---

## Task 6: Bild-Sampler-Shell (app)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/ContentTypeDetector.kt`

**Interfaces:**
- Consumes: `SuggestContentType`, `ContentSignals`, `PageSample`, `measureGrayFraction`, `SeriesAutoTypeRepository`, `BrowsableSource` (über `ActiveSource.get`).
- Produces: `class ContentTypeDetector { suspend fun detectIfNeeded(source: BrowsableSource, seriesRemoteId: String, books: List<Book>) }` — idempotent über `detectorVersion`; schreibt Vorschlag (oder löscht bei `null`).
- Konstante `DETECTOR_VERSION = 1`.

- [ ] **Step 1: Implement** (kein Unit-Test — `BitmapFactory` ist Android-Framework; die reine Logik ist in Task 1/2 getestet. Build-verifiziert.)

```kotlin
package com.komgareader.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ContentSignals
import com.komgareader.domain.model.PageSample
import com.komgareader.domain.repository.SeriesAutoTypeRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.usecase.SuggestContentType
import com.komgareader.domain.usecase.measureGrayFraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imperative shell for content-type auto-detection (seam-respecting: pages flow through
 * [BrowsableSource.openPage]). Samples a few interior pages, decodes them downsampled,
 * measures grayscale fraction + aspect via the pure domain helpers, and persists the
 * [SuggestContentType] verdict. Idempotent: skips when a fresh-version row already exists.
 * Runs off the read path (caller launches it in the background).
 */
@Singleton
class ContentTypeDetector @Inject constructor(
    private val autoTypes: SeriesAutoTypeRepository,
) {
    private val suggest = SuggestContentType()

    suspend fun detectIfNeeded(source: BrowsableSource, seriesRemoteId: String, books: List<Book>) {
        if (autoTypes.detectorVersion(source.id, seriesRemoteId) == DETECTOR_VERSION) return
        val book = books.firstOrNull { it.format.isImageArchive() } ?: return
        val signals = runCatching { sample(source, book) }.getOrNull() ?: return
        val verdict = suggest(signals)
        autoTypes.set(source.id, seriesRemoteId, verdict, DETECTOR_VERSION)
    }

    private suspend fun sample(source: BrowsableSource, book: Book): ContentSignals = withContext(Dispatchers.IO) {
        val refs = source.pages(book.remoteId)
        if (refs.isEmpty()) return@withContext ContentSignals(emptyList())
        // Skip cover (index 0): covers are often coloured even in B/W manga.
        val interior = refs.drop(1)
        if (interior.isEmpty()) return@withContext ContentSignals(emptyList())
        val pick = listOf(0.25, 0.5, 0.75)
            .map { (it * (interior.size - 1)).toInt() }
            .distinct()
            .map { interior[it] }
        val samples = pick.mapNotNull { ref ->
            val bytes = runCatching { source.openPage(ref) }.getOrNull() ?: return@mapNotNull null
            decodeSample(bytes)
        }
        ContentSignals(samples)
    }

    private fun decodeSample(bytes: ByteArray): PageSample? {
        // Bounds first to size the downsample, then decode small for cheap pixel stats.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val target = 200 // px on the long edge for stats
        val sample = maxOf(1, maxOf(w, h) / target)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return try {
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            PageSample(widthPx = w, heightPx = h, grayFraction = measureGrayFraction(pixels))
        } finally {
            bmp.recycle()
        }
    }

    private fun com.komgareader.domain.model.BookFormat.isImageArchive() =
        this == com.komgareader.domain.model.BookFormat.CBZ ||
            this == com.komgareader.domain.model.BookFormat.CBR ||
            this == com.komgareader.domain.model.BookFormat.PDF

    companion object { const val DETECTOR_VERSION = 1 }
}
```

- [ ] **Step 2: Build** — `./gradlew :app:compileDebugKotlin` → success.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/ContentTypeDetector.kt
git commit -m "feat(app): ContentTypeDetector samples interior pages off the read path"
```

---

## Task 7: Verdrahtung in SeriesDetailViewModel (app)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailViewModel.kt`

**Interfaces:**
- Consumes: `ContentTypeDetector` (Task 6), `SeriesAutoTypeRepository` (Task 4).
- Produces: Auto-Typ fließt als `auto`-Arg in `resolveViewerType`; Detektion lazy getriggert.

- [ ] **Step 1: Konstruktor** — `SeriesAutoTypeRepository` + `ContentTypeDetector` injizieren (neben `overrideRepository`). Beide sind Hilt-bereitstellbar (Repo aus DataModule, Detector @Inject @Singleton).

- [ ] **Step 2: Auflösung** — im `baseState`-Flow nach `val manualType = ...`:

```kotlin
                            val autoType: ContentType? = autoTypeRepository.get(source.id, seriesId)
                            val viewerModes = books.associate { book ->
                                book.remoteId to mapViewerMode(
                                    resolveViewerType(seriesForResolve, book, effectiveType, autoType),
                                ).name
                            }
```

- [ ] **Step 3: Lazy-Trigger** — nach dem Bauen des `Content`-State (oder am Flow-Rand) im `viewModelScope` ohne den State zu blockieren:

```kotlin
                            // Detect off the read path; result lands on next open via autoTypeRepository.
                            if (autoType == null) {
                                viewModelScope.launch {
                                    runCatching { detector.detectIfNeeded(source, seriesId, books) }
                                }
                            }
```

(Platzierung: innerhalb des `fold`-Erfolgszweigs, vor dem `SeriesDetailUiState.Content(...)`-Return — `books`/`source`/`seriesId` sind dort im Scope. `viewModelScope.launch` + `import kotlinx.coroutines.launch` falls nötig.)

- [ ] **Step 4: Build** — `./gradlew :app:compileDebugKotlin` → success.

- [ ] **Step 5: Full build + domain tests** — `./gradlew :domain:test :app:assembleDebug` → grün.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailViewModel.kt
git commit -m "feat(app): wire auto content-type suggestion into series resolution"
```

---

## Task 8: Doku-Pflege (komga-doc-sync)

**Files:**
- Modify: `docs/domain/viewer-type-resolution.md`
- Modify: `.claude/skills/komga-viewer-type-resolution/SKILL.md`
- Modify: `CLAUDE.md` (Invariante 4)

- [ ] **Step 1:** In `viewer-type-resolution.md` + SKILL.md die neue **Stufe 5 (auto)** dokumentieren, Stufen 5/6 → 6/7 renummerieren, `auto`-Param + `series_auto_types`-Tabelle + Detektor-Quelle (Pixel-Sampler) erklären. Klarstellen: Auto schlägt nur den Format-Default, NICHT manuell/Server/Bibliothek; Grau ⇒ S/W-vs-Farbe, nie Leserichtung.

- [ ] **Step 2:** CLAUDE.md Invariante 4 ergänzen: „deterministisch; Auto-Erkennung nur als vorab-persistierter Vorschlag (eigene Stufe 5), manueller Override + Server-`readingDirection` + Bibliotheks-Default schlagen ihn".

- [ ] **Step 3: Commit**

```bash
git add docs/domain/viewer-type-resolution.md .claude/skills/komga-viewer-type-resolution/SKILL.md CLAUDE.md
git commit -m "docs: document auto content-type suggestion stage"
```

---

## Verification Summary

- `./gradlew :domain:test` — alle reinen Tests grün (Tasks 1–3).
- `./gradlew :app:assembleDebug` — voller App-Build grün (Tasks 6–7).
- `:data` androidTest (`SeriesAutoTypeDaoTest`) — Emulator-gebunden; bei fehlendem Gerät als geräte-gebunden notiert.
- **Echtes E2E** (Manga-S/W → MANGA, Farb-Comic → COMIC, Strip → WEBTOON gegen lokale Test-Komga/Emulator) ist **geräte-/bild-gebunden** und in diesem Lauf ggf. offen — analog zu anderen Features im Repo, die so markiert sind. Pixel-Mathe + Kaskade sind über die reinen Unit-Tests verifiziert.

## Self-Review-Notizen

- Spec-Abdeckung: webtoon (Aspect), manga vs comic (Graustufe), Determinismus (eigene niedrige Stufe), Persistenz/Idempotenz (detectorVersion) — alle in Tasks.
- ComicInfo-Parsing + Sprach-Tiebreaker bewusst **out of scope** (v1) — Komga-`readingDirection` deckt den manga-RTL-Server-Fall, Graustufe deckt manga-vs-comic. Additive Folge-Erweiterung.
- Typkonsistenz: `SuggestContentType`/`ContentSignals`/`PageSample`/`measureGrayFraction`/`SeriesAutoTypeRepository`/`ContentTypeDetector.DETECTOR_VERSION` über Tasks hinweg gleich benannt.
