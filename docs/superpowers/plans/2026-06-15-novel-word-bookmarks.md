# Novel Word Bookmarks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tap a single word in the novel reader to set/remove a numbered bookmark, list/rename/delete/jump to bookmarks, with markers drawn on the page.

**Architecture:** Three additive seam homes — (B) two new crengine JNI calls (point→word-xpointer, xpointer→page-rect) behind `ReflowableDocument`; (data) a local-only `novel_bookmark` Room table; (app) NovelReader VM/UI with a bookmark-mode toggle that flips `tapZones` to `null` so the reader's own `pointerInput` hit-tests words. Positions are crengine xpointers (relayout-safe), jumping reuses `goToAnchor`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, crengine-ng (C++/JNI via `cr3bridge`), JUnit + Robolectric/androidTest.

**Spec:** `docs/superpowers/specs/2026-06-15-novel-word-bookmarks-design.md`

**Verification gap (read first):** Per `CLAUDE.md`, the crengine `.so` is missing on the x86_64 emulator. Pure units + mapper + DAO + migration run green on JVM/emulator. The JNI (Task 3), word-tap, markers and jump (Tasks 7+9) are verified on a **real arm64 Boox over USB** — until shown there with a screenshot, the feature is NOT "done".

---

## File Structure

**Create:**
- `domain/.../model/NovelBookmark.kt` — domain model
- `domain/.../model/BookmarkMarkerStyle.kt` — marker-style enum
- `domain/.../usecase/BookmarkLogic.kt` — pure `nextBookmarkNumber` + `toggleBookmark`
- `domain/.../repository/NovelBookmarkRepository.kt` — repo interface
- `data/.../db/NovelBookmarkDao.kt` — DAO
- `data/.../repository/RoomNovelBookmarkRepository.kt` — Room impl + mapper
- `app/.../ui/reader/NovelBookmarkPanel.kt` — bookmark list panel
- test files (see tasks)

**Modify:**
- `domain/.../render/Document.kt` — `IntRect`, `WordHit`, `wordAt`/`rectsFor` defaults
- `render-crengine/.../CrengineNative.kt` — two `external fun`
- `render-crengine/.../CrengineDocument.kt` — override `wordAt`/`rectsFor`
- `render-crengine/src/main/cpp/cr3_bridge.cpp` — two JNI functions
- `data/.../db/Entities.kt` — `NovelBookmarkEntity`
- `data/.../db/AppDatabase.kt` — version 17→18, entity, DAO accessor
- `data/.../db/Migrations.kt` (or wherever migrations live) — `MIGRATION_17_18`
- `data/.../di/DataModule.kt` — register migration, provide repo
- `domain/.../repository/SettingsRepository.kt` + `data/.../repository/RoomSettingsRepository.kt` — `bookmarkMarkerStyle`
- `app/.../i18n/Strings.kt` — new keys (interface + de + en)
- `app/.../ui/reader/NovelReaderViewModel.kt` — bookmark state + ops
- `app/.../ui/reader/NovelReaderScreen.kt` — mode toggle, hit-test, markers, buttons
- `app/.../ui/settings/SettingsContent.kt` — marker-style row

---

## Task 1: Pure domain — model + bookmark logic (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/NovelBookmark.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/BookmarkLogic.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/BookmarkLogicTest.kt`

- [ ] **Step 1: Write the domain model**

`NovelBookmark.kt`:
```kotlin
package com.komgareader.domain.model

/**
 * A single in-text bookmark in a reflowable novel. Position is a crengine
 * xpointer ([xpointer]) — layout-independent, survives relayout. [word] +
 * [snippet] are captured at set time so the list is meaningful offline.
 * Local-only; never synced to a server.
 */
data class NovelBookmark(
    val id: Long,
    val sourceId: Long,
    val bookId: String,
    val xpointer: String,
    val number: Int,
    val label: String?,
    val snippet: String,
    val createdAt: Long,
)
```

- [ ] **Step 2: Write the failing test**

`BookmarkLogicTest.kt`:
```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.NovelBookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkLogicTest {

    private fun bm(number: Int, xp: String) = NovelBookmark(
        id = number.toLong(), sourceId = 1, bookId = "b", xpointer = xp,
        number = number, label = null, snippet = "s", createdAt = 0,
    )

    @Test fun nextNumber_empty_is_one() {
        assertEquals(1, nextBookmarkNumber(emptyList()))
    }

    @Test fun nextNumber_is_max_plus_one() {
        assertEquals(3, nextBookmarkNumber(listOf(1, 2)))
    }

    @Test fun nextNumber_does_not_reuse_gaps() {
        assertEquals(3, nextBookmarkNumber(listOf(2)))
    }

    @Test fun toggle_sets_when_absent() {
        val res = toggleBookmark(emptyList(), "/p[1]/text()[1].0")
        assertEquals(ToggleResult.Set(1), res)
    }

    @Test fun toggle_sets_second_with_next_number() {
        val res = toggleBookmark(listOf(bm(1, "/a")), "/b")
        assertEquals(ToggleResult.Set(2), res)
    }

    @Test fun toggle_removes_when_present() {
        val existing = bm(1, "/a")
        val res = toggleBookmark(listOf(existing), "/a")
        assertEquals(ToggleResult.Remove(1L), res)
    }
}
```

- [ ] **Step 3: Run it, verify it fails**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.BookmarkLogicTest"`
Expected: FAIL (unresolved `nextBookmarkNumber`/`toggleBookmark`/`ToggleResult`).

- [ ] **Step 4: Implement**

`BookmarkLogic.kt`:
```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.NovelBookmark

/** Outcome of tapping a word: either set a new bookmark (with this number) or remove an existing one (by id). */
sealed interface ToggleResult {
    data class Set(val number: Int) : ToggleResult
    data class Remove(val id: Long) : ToggleResult
}

/** Next free bookmark number: monotonic max+1, never reusing gaps. Empty → 1. */
fun nextBookmarkNumber(existing: List<Int>): Int = (existing.maxOrNull() ?: 0) + 1

/**
 * Toggle the bookmark at [xpointer] against [existing]: if a bookmark already
 * has this xpointer, remove it; otherwise set a new one with the next number.
 */
fun toggleBookmark(existing: List<NovelBookmark>, xpointer: String): ToggleResult {
    val match = existing.firstOrNull { it.xpointer == xpointer }
    return if (match != null) ToggleResult.Remove(match.id)
    else ToggleResult.Set(nextBookmarkNumber(existing.map { it.number }))
}
```

- [ ] **Step 5: Run it, verify it passes**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.BookmarkLogicTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/NovelBookmark.kt \
        domain/src/main/kotlin/com/komgareader/domain/usecase/BookmarkLogic.kt \
        domain/src/test/kotlin/com/komgareader/domain/usecase/BookmarkLogicTest.kt
git commit -m "feat(domain): novel bookmark model + pure toggle/number logic"
```

---

## Task 2: Render seam — engine-neutral types + default no-op methods

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/render/Document.kt`

- [ ] **Step 1: Add types + default methods to `ReflowableDocument`**

Add the data classes near `SearchHit` (after line 41):
```kotlin
/** A rectangle in page-relative pixels (origin = top-left of the rendered page bitmap). */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** A word hit from a tap: its stable xpointer, the word text, and its page-relative rect. */
data class WordHit(val xpointer: String, val word: String, val rect: IntRect)
```

Add to the `ReflowableDocument` interface body (after `authors()`), mirroring the
`registerFont` default-no-op style so non-crengine engines need no change:
```kotlin
    /**
     * The word at page-relative pixel ([x],[y]) on the currently rendered page, or
     * null if no word is there. Default no-op so non-crengine engines need no change.
     */
    fun wordAt(x: Int, y: Int): WordHit? = null

    /**
     * Page-relative rects for the [xpointers] that fall on the currently rendered
     * page (others omitted). Used to draw bookmark markers. Default empty.
     */
    fun rectsFor(xpointers: List<String>): Map<String, IntRect> = emptyMap()
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/render/Document.kt
git commit -m "feat(domain): ReflowableDocument.wordAt/rectsFor seam (default no-op)"
```

---

## Task 3: crengine JNI — point→word + xpointer→rect (device-verified)

**Files:**
- Modify: `render-crengine/.../CrengineNative.kt`
- Modify: `render-crengine/.../CrengineDocument.kt`
- Modify: `render-crengine/src/main/cpp/cr3_bridge.cpp`
- Test: `render-crengine/src/androidTest/kotlin/com/komgareader/render/crengine/CrengineWordBookmarkInstrumentedTest.kt`

> **Coordinate caveat:** crengine's point/rect coordinate spaces (window vs. doc,
> page-scroll offset via `view->GetPos()`) are not fully certain from headers. The
> implementation below is the best-evidence version; it is **verified and adjusted
> on a real Boox** in Task 11. If tapped words or markers are off by a constant
> vertical offset, that is the `GetPos()` translation — fix it there.

- [ ] **Step 1: Add the two `external fun` to `CrengineNative.kt`**

After `nativeSearch`:
```kotlin
    /**
     * Word at page-relative pixel ([x],[y]) on the current page.
     * Returns "xpointer<US>word<US>left<US>top<US>right<US>bottom" or "" if none.
     */
    external fun nativeXPointerAtPoint(handle: Long, x: Int, y: Int): String

    /**
     * For each of [xpointers] that lies on the current page, a record
     * "xpointer<US>left<US>top<US>right<US>bottom" (RECORD_SEP-separated); others omitted.
     */
    external fun nativeRectsForXPointers(handle: Long, xpointers: Array<String>): String
```

- [ ] **Step 2: Implement the JNI in `cr3_bridge.cpp`**

Add after `nativeSearch`'s implementation (uses existing `FIELD_SEP`/`RECORD_SEP`,
`UnicodeToUtf8`, `Utf8ToUnicode`):
```cpp
JNIEXPORT jstring JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeXPointerAtPoint(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint x, jint y) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return env->NewStringUTF("");
    view->checkRender();
    // getNodeByPoint expects document coordinates: add the current page scroll offset.
    lvPoint pt(x, y + view->GetPos());
    ldomXPointer ptr = view->getNodeByPoint(pt);
    if (ptr.isNull())
        return env->NewStringUTF("");
    ldomXRange wordRange;
    if (!ldomXRange::getWordRange(wordRange, ptr))
        return env->NewStringUTF("");
    lvRect rect;
    if (!wordRange.getRectEx(rect) || rect.isEmpty())
        return env->NewStringUTF("");
    lString32 out;
    out.append(wordRange.getStart().toString());
    out.append(1, FIELD_SEP);
    out.append(wordRange.getRangeText());
    out.append(1, FIELD_SEP);
    out.append(lString32::itoa(rect.left));
    out.append(1, FIELD_SEP);
    out.append(lString32::itoa(rect.top - view->GetPos()));
    out.append(1, FIELD_SEP);
    out.append(lString32::itoa(rect.right));
    out.append(1, FIELD_SEP);
    out.append(lString32::itoa(rect.bottom - view->GetPos()));
    return env->NewStringUTF(UnicodeToUtf8(out).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeRectsForXPointers(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jobjectArray xpointers) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return env->NewStringUTF("");
    view->checkRender();
    ldomDocument* doc = view->getDocument();
    const int pageTop = view->GetPos();
    const int pageBottom = pageTop + view->GetHeight();
    const int n = env->GetArrayLength(xpointers);
    lString32 out;
    for (int i = 0; i < n; i++) {
        auto js = (jstring) env->GetObjectArrayElement(xpointers, i);
        const char* c = env->GetStringUTFChars(js, nullptr);
        lString32 xpStr = Utf8ToUnicode(lString8(c));
        env->ReleaseStringUTFChars(js, c);
        env->DeleteLocalRef(js);
        ldomXPointer ptr = doc->createXPointer(xpStr);
        if (ptr.isNull())
            continue;
        lvRect rect;
        if (!ptr.getRect(rect) || rect.isEmpty())
            continue;
        // Only emit rects on the current page; translate doc→page coordinates.
        if (rect.bottom <= pageTop || rect.top >= pageBottom)
            continue;
        if (!out.empty())
            out.append(1, RECORD_SEP);
        out.append(xpStr);
        out.append(1, FIELD_SEP);
        out.append(lString32::itoa(rect.left));
        out.append(1, FIELD_SEP);
        out.append(lString32::itoa(rect.top - pageTop));
        out.append(1, FIELD_SEP);
        out.append(lString32::itoa(rect.right));
        out.append(1, FIELD_SEP);
        out.append(lString32::itoa(rect.bottom - pageTop));
    }
    return env->NewStringUTF(UnicodeToUtf8(out).c_str());
}
```

> If `ldomXRange::getWordRange` is not a static member in this crengine-ng version,
> use the instance form: construct `ldomXRange r(ptr, ptr);` then call the matching
> word-expansion API found in `ldomxrange.h`. Confirm the exact signature by grepping
> `render-crengine/native/prefix/aarch64-linux-android/include/crengine-ng/ldomxrange.h`
> before building. Likewise confirm `view->GetHeight()` exists (else use the viewport
> height passed at layout).

- [ ] **Step 3: Override in `CrengineDocument.kt`**

Add imports for `IntRect`, `WordHit`, then override:
```kotlin
    override fun wordAt(x: Int, y: Int): WordHit? {
        val raw = CrengineNative.nativeXPointerAtPoint(handle, x, y)
        if (raw.isEmpty()) return null
        val f = raw.split(FIELD_SEP)
        if (f.size < 6) return null
        return WordHit(
            xpointer = f[0],
            word = f[1],
            rect = IntRect(f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt()),
        )
    }

    override fun rectsFor(xpointers: List<String>): Map<String, IntRect> {
        if (xpointers.isEmpty()) return emptyMap()
        val raw = CrengineNative.nativeRectsForXPointers(handle, xpointers.toTypedArray())
        if (raw.isEmpty()) return emptyMap()
        return raw.split(RECORD_SEP).filter { it.isNotEmpty() }.mapNotNull { rec ->
            val f = rec.split(FIELD_SEP)
            if (f.size < 5) null
            else f[0] to IntRect(f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt())
        }.toMap()
    }
```
Note: `FIELD_SEP`/`RECORD_SEP` are in the private companion — these overrides are
inside `CrengineDocument`, so they are in scope.

- [ ] **Step 4: Write the instrumented test (runs on Boox; skips cleanly elsewhere)**

`CrengineWordBookmarkInstrumentedTest.kt` (mirror the helpers from
`CrengineRenderInstrumentedTest`):
```kotlin
package com.komgareader.render.crengine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.ReflowConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CrengineWordBookmarkInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val viewportW = 800
    private val viewportH = 1200

    private fun assetBytes(name: String) =
        context.assets.open(name).use { it.readBytes() }
    private fun copyAsset(asset: String): File {
        val out = File(context.cacheDir, asset.substringAfterLast('/'))
        out.parentFile?.mkdirs()
        context.assets.open(asset).use { i -> out.outputStream().use { i.copyTo(it) } }
        return out
    }
    private fun hyphDir(): String {
        val dir = File(context.cacheDir, "hyph").apply { mkdirs() }
        for (p in listOf("hyph-de-1996.pattern", "hyph-en-us.pattern"))
            context.assets.open("hyph/$p").use { i -> File(dir, p).outputStream().use { i.copyTo(it) } }
        return dir.absolutePath + "/"
    }
    private fun init() {
        val fonts = listOf("fonts/DejaVuSans.ttf", "fonts/Literata.ttf", "fonts/Bitter.ttf")
            .map { copyAsset(it).absolutePath }.toTypedArray()
        assertTrue(CrengineNative.nativeInit(fonts, hyphDir()))
    }

    @Test fun word_tap_returns_xpointer_and_round_trips_to_a_rect() {
        init()
        CrengineDocument(assetBytes("sample.epub"), "sample.epub", viewportW, viewportH).use { doc ->
            doc.applyLayout(ReflowConfig(hyphenation = Hyphenation.Language("de")))
            // Tap near the top-left of the text body.
            val hit = doc.wordAt(viewportW / 2, viewportH / 4)
            assertTrue("a word was hit", hit != null && hit.xpointer.isNotBlank())
            val rects = doc.rectsFor(listOf(hit!!.xpointer))
            assertTrue("the bookmarked word resolves to a rect on this page", rects.containsKey(hit.xpointer))
        }
    }
}
```

- [ ] **Step 5: Run on a connected Boox**

Run: `./gradlew :render-crengine:connectedDebugAndroidTest --tests "*CrengineWordBookmarkInstrumentedTest"`
Expected: PASS on arm64 Boox. (On x86_64 emulator the `.so` is absent → not runnable; that is expected, note it.)

- [ ] **Step 6: Commit**

```bash
git add render-crengine/
git commit -m "feat(render-crengine): JNI wordAt + rectsFor for novel bookmarks"
```

---

## Task 4: Data — novel_bookmark table, DAO, migration, repository

**Files:**
- Modify: `data/.../db/Entities.kt`, `data/.../db/AppDatabase.kt`, the migrations file, `data/.../di/DataModule.kt`
- Create: `data/.../db/NovelBookmarkDao.kt`, `domain/.../repository/NovelBookmarkRepository.kt`, `data/.../repository/RoomNovelBookmarkRepository.kt`
- Test: `data/src/androidTest/.../NovelBookmarkDaoTest.kt`, `data/src/androidTest/.../NovelBookmarkMigrationTest.kt`

- [ ] **Step 1: Add the entity** (`Entities.kt`)

```kotlin
/**
 * A local-only in-text novel bookmark. Keyed by autoincrement [id]; queried by
 * ([sourceId],[bookId]). [xpointer] is the relayout-safe crengine position;
 * [word]/[snippet] are captured at set time. Never synced to a server.
 */
@Entity(
    tableName = "novel_bookmark",
    indices = [Index(value = ["sourceId", "bookId"])],
)
data class NovelBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val bookId: String,
    val xpointer: String,
    val number: Int,
    val label: String?,
    val snippet: String,
    val createdAt: Long,
)
```
(Add imports `androidx.room.Index`, `androidx.room.PrimaryKey` if missing.)

- [ ] **Step 2: Write the DAO** (`NovelBookmarkDao.kt`)

```kotlin
package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** DAO for local-only novel bookmarks, ordered by number within a book. */
@Dao
interface NovelBookmarkDao {

    @Insert
    suspend fun insert(entry: NovelBookmarkEntity): Long

    @Query("SELECT * FROM novel_bookmark WHERE sourceId = :sourceId AND bookId = :bookId ORDER BY number")
    fun observe(sourceId: Long, bookId: String): Flow<List<NovelBookmarkEntity>>

    @Query("DELETE FROM novel_bookmark WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE novel_bookmark SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String?)
}
```

- [ ] **Step 3: Write the domain repository interface** (`NovelBookmarkRepository.kt`)

```kotlin
package com.komgareader.domain.repository

import com.komgareader.domain.model.NovelBookmark
import kotlinx.coroutines.flow.Flow

/** Persists local-only novel bookmarks (never synced). */
interface NovelBookmarkRepository {
    fun observe(sourceId: Long, bookId: String): Flow<List<NovelBookmark>>
    suspend fun add(bookmark: NovelBookmark)
    suspend fun remove(id: Long)
    suspend fun rename(id: Long, label: String?)
}
```

- [ ] **Step 4: Write the Room impl + mapper** (`RoomNovelBookmarkRepository.kt`)

```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.NovelBookmarkDao
import com.komgareader.data.db.NovelBookmarkEntity
import com.komgareader.domain.model.NovelBookmark
import com.komgareader.domain.repository.NovelBookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNovelBookmarkRepository(private val dao: NovelBookmarkDao) : NovelBookmarkRepository {

    override fun observe(sourceId: Long, bookId: String): Flow<List<NovelBookmark>> =
        dao.observe(sourceId, bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(bookmark: NovelBookmark) {
        dao.insert(
            NovelBookmarkEntity(
                id = 0,
                sourceId = bookmark.sourceId,
                bookId = bookmark.bookId,
                xpointer = bookmark.xpointer,
                number = bookmark.number,
                label = bookmark.label,
                snippet = bookmark.snippet,
                createdAt = bookmark.createdAt,
            ),
        )
    }

    override suspend fun remove(id: Long) = dao.delete(id)

    override suspend fun rename(id: Long, label: String?) = dao.rename(id, label)

    private fun NovelBookmarkEntity.toDomain() = NovelBookmark(
        id = id, sourceId = sourceId, bookId = bookId, xpointer = xpointer,
        number = number, label = label, snippet = snippet, createdAt = createdAt,
    )
}
```

- [ ] **Step 5: Register entity + version + DAO accessor** (`AppDatabase.kt`)

Add `NovelBookmarkEntity::class` to the `entities = [...]` list, bump `version = 17`
to `version = 18`, and add:
```kotlin
abstract fun novelBookmarkDao(): NovelBookmarkDao
```

- [ ] **Step 6: Add the migration** (in the file that defines `MIGRATION_16_17`)

```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `novel_bookmark` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sourceId` INTEGER NOT NULL, `bookId` TEXT NOT NULL, " +
                "`xpointer` TEXT NOT NULL, `number` INTEGER NOT NULL, " +
                "`label` TEXT, `snippet` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_novel_bookmark_sourceId_bookId` " +
                "ON `novel_bookmark` (`sourceId`, `bookId`)",
        )
    }
}
```

- [ ] **Step 7: Wire DI** (`DataModule.kt`)

Append `MIGRATION_17_18` to the `.addMigrations(...)` list, and add the provider
(mirror `novelProgressRepository`):
```kotlin
@Provides @Singleton
fun novelBookmarkRepository(db: AppDatabase): NovelBookmarkRepository =
    RoomNovelBookmarkRepository(db.novelBookmarkDao())
```
(Add imports for `RoomNovelBookmarkRepository` and `NovelBookmarkRepository`.)

- [ ] **Step 8: Write the DAO test** (`NovelBookmarkDaoTest.kt`, mirror `NovelProgressDaoTest`)

```kotlin
package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.NovelBookmarkDao
import com.komgareader.data.db.NovelBookmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelBookmarkDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: NovelBookmarkDao

    private fun entity(number: Int, xp: String, label: String? = null) = NovelBookmarkEntity(
        id = 0, sourceId = 1, bookId = "b1", xpointer = xp, number = number,
        label = label, snippet = "ctx $number", createdAt = number.toLong(),
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
        dao = db.novelBookmarkDao()
    }

    @After fun teardown() = db.close()

    @Test fun insert_and_observe_orders_by_number() = runBlocking {
        dao.insert(entity(2, "/b"))
        dao.insert(entity(1, "/a"))
        val list = dao.observe(1, "b1").first()
        assertEquals(listOf(1, 2), list.map { it.number })
    }

    @Test fun observe_scopes_to_source_and_book() = runBlocking {
        dao.insert(entity(1, "/a"))
        assertEquals(0, dao.observe(2, "b1").first().size)
        assertEquals(0, dao.observe(1, "other").first().size)
    }

    @Test fun delete_and_rename() = runBlocking {
        val id = dao.insert(entity(1, "/a"))
        dao.rename(id, "Intro")
        assertEquals("Intro", dao.observe(1, "b1").first().single().label)
        dao.delete(id)
        assertEquals(0, dao.observe(1, "b1").first().size)
    }
}
```

- [ ] **Step 9: Write the migration test** (`NovelBookmarkMigrationTest.kt`, mirror `NovelProgressMigrationTest` for 17→18: build a v17 DB, run `MIGRATION_17_18`, assert pre-existing `novel_progress` rows survive and `novel_bookmark` is usable). Use the existing test's structure verbatim, changing the from/to versions and the asserted table.

- [ ] **Step 10: Run the data tests**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "*NovelBookmark*"`
Expected: PASS (emulator is fine — Room only, no crengine).

- [ ] **Step 11: Commit**

```bash
git add data/ domain/src/main/kotlin/com/komgareader/domain/repository/NovelBookmarkRepository.kt
git commit -m "feat(data): novel_bookmark table + DAO + repo + migration 17->18"
```

---

## Task 5: Settings — bookmark marker style enum

**Files:**
- Create: `domain/.../model/BookmarkMarkerStyle.kt`
- Modify: `domain/.../repository/SettingsRepository.kt`, `data/.../repository/RoomSettingsRepository.kt`

- [ ] **Step 1: Define the enum** (`BookmarkMarkerStyle.kt`)

```kotlin
package com.komgareader.domain.model

/** How a set novel bookmark is drawn on the page. */
enum class BookmarkMarkerStyle { UNDERLINE, MARGIN }
```

- [ ] **Step 2: Extend `SettingsRepository`** (mirror `shellLayoutMode`)

```kotlin
    /** "UNDERLINE" | "MARGIN" — how novel bookmarks are marked on the page. */
    val bookmarkMarkerStyle: Flow<String>
    suspend fun setBookmarkMarkerStyle(value: String)
```

- [ ] **Step 3: Implement in `RoomSettingsRepository`**

```kotlin
    override val bookmarkMarkerStyle: Flow<String> =
        dao.observe(KEY_BOOKMARK_MARKER_STYLE).map { it ?: BookmarkMarkerStyle.UNDERLINE.name }

    override suspend fun setBookmarkMarkerStyle(value: String) =
        dao.put(SettingEntity(KEY_BOOKMARK_MARKER_STYLE, value))
```
Add the key constant alongside `KEY_SHELL_LAYOUT`:
```kotlin
    const val KEY_BOOKMARK_MARKER_STYLE = "bookmark_marker_style"
```
(Add import for `com.komgareader.domain.model.BookmarkMarkerStyle`.)

- [ ] **Step 4: Build**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/BookmarkMarkerStyle.kt \
        domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt \
        data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt
git commit -m "feat(settings): bookmarkMarkerStyle (UNDERLINE/MARGIN) persisted setting"
```

---

## Task 6: i18n keys (de + en)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

- [ ] **Step 1: Add keys to the `Strings` interface**

```kotlin
    val novelBookmarks: String
    val novelBookmarkMode: String
    val novelBookmarkRename: String
    val novelBookmarkDelete: String
    val novelBookmarksEmpty: String
    fun novelBookmarkNumber(n: Int): String
```

- [ ] **Step 2: Add German values (`StringsDe`)**

```kotlin
    override val novelBookmarks = "Lesezeichen"
    override val novelBookmarkMode = "Lesezeichen-Modus"
    override val novelBookmarkRename = "Umbenennen"
    override val novelBookmarkDelete = "Löschen"
    override val novelBookmarksEmpty = "Noch keine Lesezeichen"
    override fun novelBookmarkNumber(n: Int) = "#$n"
```

- [ ] **Step 3: Add English values (`StringsEn`)**

```kotlin
    override val novelBookmarks = "Bookmarks"
    override val novelBookmarkMode = "Bookmark mode"
    override val novelBookmarkRename = "Rename"
    override val novelBookmarkDelete = "Delete"
    override val novelBookmarksEmpty = "No bookmarks yet"
    override fun novelBookmarkNumber(n: Int) = "#$n"
```

- [ ] **Step 4: Build (compile-time parity check)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (both impls satisfy the interface).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "feat(i18n): novel bookmark strings (de+en)"
```

---

## Task 7: NovelReaderViewModel — bookmark state + ops

**Files:**
- Modify: `app/.../ui/reader/NovelReaderViewModel.kt`

- [ ] **Step 1: Inject the repository**

Add `private val bookmarks: NovelBookmarkRepository,` to the constructor (after
`novelProgress`). Add imports for `NovelBookmarkRepository`, `NovelBookmark`,
`BookmarkLogic` functions (`toggleBookmark`, `ToggleResult`), `WordHit`, `IntRect`,
`stateIn`, `SharingStarted`.

- [ ] **Step 2: Expose bookmark state + mode**

Add fields (near the other StateFlows). The list comes straight from the repo for
this book; mode is ephemeral UI state:
```kotlin
    val bookmarksFlow: StateFlow<List<NovelBookmark>> =
        bookmarks.observe(routeSourceId, bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bookmarkMode = MutableStateFlow(false)
    val bookmarkMode: StateFlow<Boolean> = _bookmarkMode.asStateFlow()

    fun toggleBookmarkMode() { _bookmarkMode.value = !_bookmarkMode.value }
```

- [ ] **Step 3: Word-tap toggle**

```kotlin
    /** Tap at page-relative pixel ([x],[y]) in bookmark mode: set or remove the word's bookmark. */
    fun onWordTap(x: Int, y: Int) {
        viewModelScope.launch {
            val hit: WordHit = documentMutex.withLock {
                val doc = document ?: return@launch
                withContext(Dispatchers.IO) { doc.wordAt(x, y) }
            } ?: return@launch
            when (val r = toggleBookmark(bookmarksFlow.value, hit.xpointer)) {
                is ToggleResult.Remove -> bookmarks.remove(r.id)
                is ToggleResult.Set -> bookmarks.add(
                    NovelBookmark(
                        id = 0, sourceId = routeSourceId, bookId = bookId,
                        xpointer = hit.xpointer, number = r.number, label = null,
                        snippet = hit.word, createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
```

- [ ] **Step 4: Marker rects for the current page**

```kotlin
    /** Page-relative rects of the bookmarks that fall on the currently rendered page. */
    suspend fun bookmarkRectsForCurrentPage(): Map<String, IntRect> {
        val xps = bookmarksFlow.value.map { it.xpointer }
        if (xps.isEmpty()) return emptyMap()
        return documentMutex.withLock {
            val doc = document ?: return emptyMap()
            withContext(Dispatchers.IO) { doc.rectsFor(xps) }
        }
    }
```

- [ ] **Step 5: Jump / rename / delete**

`jumpTo` reuses the existing `goToAnchor`:
```kotlin
    fun jumpToBookmark(xpointer: String) = goToAnchor(xpointer)
    fun renameBookmark(id: Long, label: String?) =
        viewModelScope.launch { bookmarks.rename(id, label?.ifBlank { null }) }
    fun deleteBookmark(id: Long) = viewModelScope.launch { bookmarks.remove(id) }
```

- [ ] **Step 6: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderViewModel.kt
git commit -m "feat(reader): novel bookmark VM state, word-tap toggle, jump/rename/delete"
```

---

## Task 8: NovelBookmarkPanel

**Files:**
- Create: `app/.../ui/reader/NovelBookmarkPanel.kt`

- [ ] **Step 1: Write the panel** (mirror `NovelTocPanel`'s `EinkInfoDialog` use)

```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.domain.model.NovelBookmark
import com.komgareader.ui.icons.AppIcons

/**
 * Lists the novel's bookmarks (#number · optional name · snippet). Tap a row to
 * jump; the trailing buttons rename and delete. Built on [EinkInfoDialog] like the
 * other novel panels (E-Ink: no animation, one dialog at a time).
 */
@Composable
fun NovelBookmarkPanel(
    bookmarks: List<NovelBookmark>,
    onJump: (String) -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    EinkInfoDialog(title = s.novelBookmarks, onDismiss = onDismiss, closeLabel = s.close, contentSpacing = 0.dp) {
        if (bookmarks.isEmpty()) {
            Text(s.novelBookmarksEmpty, Modifier.padding(16.dp))
            return@EinkInfoDialog
        }
        bookmarks.forEach { bm ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onJump(bm.xpointer); onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(s.novelBookmarkNumber(bm.number))
                Column(Modifier.weight(1f)) {
                    if (!bm.label.isNullOrBlank()) Text(bm.label!!)
                    Text(bm.snippet)
                }
                IconButton(onClick = { onRename(bm.id) }) {
                    Icon(AppIcons.Edit, contentDescription = s.novelBookmarkRename)
                }
                IconButton(onClick = { onDelete(bm.id) }) {
                    Icon(AppIcons.Delete, contentDescription = s.novelBookmarkDelete)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelBookmarkPanel.kt
git commit -m "feat(reader): NovelBookmarkPanel (list, jump, rename, delete)"
```

---

## Task 9: NovelReaderScreen — mode toggle, hit-test, markers, buttons

**Files:**
- Modify: `app/.../ui/reader/NovelReaderScreen.kt`

> The page bitmap is created at exactly the viewport pixel size
> (`CrengineDocument.renderPage` uses `width`/`height` = the `BoxWithConstraints`
> constraints), displayed with `ContentScale.Fit` inside a full-size Box of the same
> size → display is 1:1 with bitmap pixels. So a tap `Offset` in that Box maps to a
> bitmap pixel directly, and crengine's page-relative rects share that pixel space.

- [ ] **Step 1: Collect mode + marker style; add a rename dialog state**

In `NovelReaderScreen`, after the existing `collectAsState()` calls:
```kotlin
    val bookmarks by novelVm.bookmarksFlow.collectAsState()
    val bookmarkMode by novelVm.bookmarkMode.collectAsState()
    val markerStyleName by novelVm.markerStyle.collectAsState()   // see Step 1a
    var bookmarkPanelOpen by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<Long?>(null) }
```

- [ ] **Step 1a: Expose marker style on the VM**

In `NovelReaderViewModel`, add (uses the injected `settings`):
```kotlin
    val markerStyle: StateFlow<String> =
        settings.bookmarkMarkerStyle
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookmarkMarkerStyle.UNDERLINE.name)
```
(Import `com.komgareader.domain.model.BookmarkMarkerStyle`.)

- [ ] **Step 2: Add the two action buttons**

In the `actions = { ... }` block, before the TOC button, add the mode toggle and the
list button:
```kotlin
            IconButton(onClick = { novelVm.toggleBookmarkMode() }) {
                Icon(
                    if (bookmarkMode) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                    contentDescription = strings.novelBookmarkMode,
                    tint = Color.White,
                )
            }
            IconButton(onClick = { bookmarkPanelOpen = true }) {
                Icon(AppIcons.List, contentDescription = strings.novelBookmarks, tint = Color.White)
            }
```
(Add `import com.komgareader.ui.icons.AppIcons` already present; `AppIcons.List`,
`AppIcons.Bookmark`, `AppIcons.BookmarkFilled` all exist.)

- [ ] **Step 3: Show the panel + rename dialog in the exclusive `when`**

Extend the `when { ... }` block (one dialog at a time) with:
```kotlin
        bookmarkPanelOpen -> NovelBookmarkPanel(
            bookmarks = bookmarks,
            onJump = novelVm::jumpToBookmark,
            onRename = { renameId = it },
            onDelete = novelVm::deleteBookmark,
            onDismiss = { bookmarkPanelOpen = false },
        )
```
After the `when`, add the rename dialog (reuse the existing single-field dialog
pattern; if none exists, use `EinkModal` with a `TextField`):
```kotlin
    renameId?.let { id ->
        val existing = bookmarks.firstOrNull { it.id == id }
        BookmarkRenameDialog(
            initial = existing?.label.orEmpty(),
            onConfirm = { novelVm.renameBookmark(id, it); renameId = null },
            onDismiss = { renameId = null },
        )
    }
```
And add a small private `BookmarkRenameDialog` composable at the bottom of the file
using `EinkModal` (title `strings.novelBookmarkRename`, a single `TextField`, confirm
= `strings.save`, dismiss = `strings.close`). Mirror an existing text-input modal in
the codebase (grep for `EinkModal` + `TextField`) for exact field styling.

- [ ] **Step 4: Switch tap zones + hit-test in the content lambda**

Pass explicit `tapZones`/`showTapZoneHints` to `ReaderScaffold` (currently relies on
the default thirds):
```kotlin
        tapZones = if (bookmarkMode) null
            else ReaderTapZones.HorizontalThirds(novelVm::prevPage, novelVm::toggleChrome, novelVm::nextPage),
        showTapZoneHints = !bookmarkMode,
```
(Add `import com.komgareader.ui.slots.ReaderTapZones`.)

In the `else ->` page branch, wrap the page `Box` so that in bookmark mode it
hit-tests taps, and overlay markers. Replace the inner page `Box(...)` with:
```kotlin
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (bookmarkMode) Modifier.pointerInput(state.currentPage, bookmarkMode) {
                                    detectTapGestures { offset ->
                                        novelVm.onWordTap(offset.x.toInt(), offset.y.toInt())
                                    }
                                } else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bmp != null) {
                            FilteredReaderImage(
                                bitmap = bmp!!,
                                contentDescription = "Seite ${state.currentPage + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            BookmarkMarkers(novelVm, bookmarks, state.currentPage, state.layoutGeneration, markerStyleName)
                        } else {
                            LoadingIndicator()
                        }
                    }
```
(Add imports: `androidx.compose.foundation.gestures.detectTapGestures`,
`androidx.compose.ui.input.pointer.pointerInput`.)

- [ ] **Step 5: Draw the markers**

Add a private composable to the file:
```kotlin
@Composable
private fun BookmarkMarkers(
    novelVm: NovelReaderViewModel,
    bookmarks: List<com.komgareader.domain.model.NovelBookmark>,
    currentPage: Int,
    layoutGeneration: Int,
    markerStyleName: String,
) {
    val rects by produceState(initialValue = emptyMap<String, com.komgareader.domain.render.IntRect>(), currentPage, layoutGeneration, bookmarks) {
        value = novelVm.bookmarkRectsForCurrentPage()
    }
    if (rects.isEmpty()) return
    val margin = markerStyleName == com.komgareader.domain.model.BookmarkMarkerStyle.MARGIN.name
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        rects.values.forEach { r ->
            if (margin) {
                // Margin marker: a short thick bar at the left edge, vertically at the word's line.
                drawRect(
                    color = Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, r.top.toFloat()),
                    size = androidx.compose.ui.geometry.Size(8f, (r.bottom - r.top).toFloat()),
                )
            } else {
                // Underline: a thick black line just under the word.
                drawRect(
                    color = Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(r.left.toFloat(), (r.bottom - 3).toFloat()),
                    size = androidx.compose.ui.geometry.Size((r.right - r.left).toFloat(), 3f),
                )
            }
        }
    }
}
```
> Marker thickness/offset are first guesses; tune them on the Boox in Task 11.

- [ ] **Step 6: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderScreen.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderViewModel.kt
git commit -m "feat(reader): novel bookmark mode tap-to-word, markers, list/rename buttons"
```

---

## Task 10: Settings UI — marker style row

**Files:**
- Modify: `app/.../ui/settings/SettingsContent.kt` (the Novel section)
- Possibly: `app/.../ui/settings/SettingsViewModel.kt` (a setter passthrough)

- [ ] **Step 1: Add a setter to the settings ViewModel** (mirror an existing novel setter like `setNovelMarginPreset`)

```kotlin
    fun setBookmarkMarkerStyle(value: String) =
        viewModelScope.launch { settings.setBookmarkMarkerStyle(value) }
```
And expose the current value as state (mirror how `marginPreset` is collected in the
settings screen — e.g. a `bookmarkMarkerStyle` field collected from
`settings.bookmarkMarkerStyle`).

- [ ] **Step 2: Add the row in the Novel settings section** (mirror `novelMargin` `SegmentedChoiceRow` at SettingsContent.kt ~692)

```kotlin
SegmentedChoiceRow(
    label = s.novelBookmarks,
    options = listOf(
        SegmentOption(BookmarkMarkerStyle.UNDERLINE.name, s.novelBookmarkMarkerUnderline),
        SegmentOption(BookmarkMarkerStyle.MARGIN.name, s.novelBookmarkMarkerMargin),
    ),
    selectedKey = bookmarkMarkerStyle,
    onSelect = { viewModel.setBookmarkMarkerStyle(it) },
    query = query,
)
```
This needs two more i18n keys — add them in Task 6's file (interface + de + en):
```kotlin
    val novelBookmarkMarkerUnderline: String   // de "Unterstreichung" / en "Underline"
    val novelBookmarkMarkerMargin: String      // de "Rand-Marker" / en "Margin marker"
```
(Import `com.komgareader.domain.model.BookmarkMarkerStyle`.)

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/ \
        app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "feat(settings): novel bookmark marker-style picker (underline/margin)"
```

---

## Task 11: Full build, device verification, doc sync

**Files:**
- Modify: `CLAUDE.md`, `.claude/rules/architecture-seams.md`, the spec status, English community docs as the `komga-doc-sync` skill directs.

- [ ] **Step 1: Full unit + lint build**

Run: `./gradlew :domain:test :data:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all unit tests green.

- [ ] **Step 2: Install + verify on a real arm64 Boox over USB**

Run: `./gradlew :app:installDebug` then open a novel.
Verify with screenshots:
1. Toggle bookmark mode on → tap a word → an underline (or margin marker) appears at that word; the bookmark list shows `#1`.
2. Tap the same word again → marker disappears, list empties.
3. Set several, open the list, rename one, jump to one (lands on its page), delete one.
4. Change font size (relayout) → markers still align to their words (xpointer survived).
5. Switch the marker-style setting → markers redraw in the other style.
6. Mode off → thirds paging + chrome toggle work as before.

If word hits or markers are misaligned, adjust the `GetPos()` translation in Task 3's
JNI and the marker geometry in Task 9 Step 5, then rebuild.

- [ ] **Step 3: Doc sync**

Invoke the `komga-doc-sync` skill. At minimum: update
`.claude/rules/architecture-seams.md` (Naht B: new `wordAt`/`rectsFor` +
`novel_bookmark` local-only table), flip the spec `Status:` to implemented, and
update the English community docs (README/ARCHITECTURE/PROJECT-STATUS) per the skill.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: sync novel word bookmarks (seam, table, status)"
```

---

## Self-Review

- **Spec coverage:** tap-to-set/remove (T3 JNI + T7 onWordTap + T9 hit-test) ✓; auto-number, name later (T1 logic, T7, T8 rename) ✓; jump (T7 jumpToBookmark→goToAnchor) ✓; numbering monotonic (T1) ✓; two marker styles as a setting (T5 enum, T9 BookmarkMarkers, T10 picker) ✓; xpointer position model (T3) ✓; local-only, no sync (T4 repo, no sync-queue wiring) ✓; bookmark-mode toggle vs. thirds (T9 tapZones switch) ✓; E-Ink no-animation/monochrome (panels via EinkInfoDialog, Canvas black) ✓; i18n de+en (T6) ✓; verification gap on Boox (T11) ✓.
- **Type consistency:** `NovelBookmark`(id,sourceId:Long,bookId:String,xpointer,number,label?,snippet,createdAt) used identically across T1/T4/T7/T8; `WordHit`/`IntRect` from T2 used in T3/T7/T9; `ToggleResult.Set(number)`/`Remove(id)` consistent T1↔T7; `bookmarkMarkerStyle: Flow<String>` consistent T5↔T9/T10; repo methods `observe/add/remove/rename` consistent T4↔T7.
- **Placeholders:** none — every code step shows full code. The two intentionally deferred-to-device items (JNI coordinate offset, marker pixel geometry) are flagged with concrete fallback instructions and gated by T11, not left blank.
