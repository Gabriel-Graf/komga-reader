# Reading Statistics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A local-only reading-statistics module — total reading time, time split per reader type, started/finished work counts — shown in a new Settings → Statistics section.

**Architecture:** Pure domain logic (cap math, aggregation, work counting) is unit-tested first. A Room session log (`reading_session`, migration v17→v18) stores each reading session; work counts are derived from the existing `read_progress` + `novel_progress` tables (no new tracking, no progress-table migration). A `@Singleton ReadingSessionTracker` holds the event-driven, capped-delta measurement logic in **one** place; a thin `ReadingSessionEffect` Composable (mirroring the existing `EinkContextEffect`) drives it from each reader screen. No ticking timer (E-Ink battery), no server sync.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, kotlinx.coroutines Flow, kotlin.test.

**Resolved wiring decision (was the spec's open sub-decision):** The `Viewer` contract is **not** extended. ReaderRoute's base `ReaderViewModel` carries the route `bookId`/`sourceId` (same for every reader). Expose them as getters on `ReaderViewModel` only; ReaderRoute passes `readerKind` + `bookRemoteId` + `sourceId` to each reader screen as params; each screen invokes `ReadingSessionEffect(...)` with its own screen-local current page. A `DisposableEffect(bookRemoteId)` does enter/flush; `LaunchedEffect(currentPage)` signals each page change. A settings detour does not over-count (no page turns happen there; the one delta on return is cap-bounded).

---

## File Structure

**domain/ (pure Kotlin, unit-tested):**
- Create `domain/src/main/kotlin/com/komgareader/domain/model/ReadingStats.kt` — `ReaderKind`, `ReadingSession`, `ReadingStats`.
- Create `domain/src/main/kotlin/com/komgareader/domain/usecase/ReadingTimeCaps.kt` — caps + `capDeltaMs`.
- Create `domain/src/main/kotlin/com/komgareader/domain/usecase/ReadingStatsAggregator.kt` — time aggregation + work counting + duration split.
- Create `domain/src/main/kotlin/com/komgareader/domain/repository/ReadingStatsRepository.kt` — interface.
- Tests: `domain/src/test/kotlin/com/komgareader/domain/usecase/ReadingTimeCapsTest.kt`, `ReadingStatsAggregatorTest.kt`.

**data/ (Room):**
- Modify `data/src/main/kotlin/com/komgareader/data/db/Entities.kt` — add `ReadingSessionEntity`.
- Create `data/src/main/kotlin/com/komgareader/data/db/ReadingSessionDao.kt`.
- Modify `data/src/main/kotlin/com/komgareader/data/db/NovelProgressDao.kt` — add `observeAll()`.
- Modify `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt` — version 17→18, register entity + DAO + `MIGRATION_17_18`.
- Create `data/src/main/kotlin/com/komgareader/data/repository/RoomReadingStatsRepository.kt`.
- Modify `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt` — provide `ReadingStatsRepository`.
- Test: `data/src/androidTest/kotlin/com/komgareader/data/db/Migration17To18Test.kt`.

**app/ (tracker + effect + UI):**
- Create `app/src/main/kotlin/com/komgareader/app/data/ReadingSessionTracker.kt`.
- Create `app/src/main/kotlin/com/komgareader/app/ui/reader/ReadingSessionEffect.kt` — holder + effect.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt` — expose `bookRemoteId`/`sourceId` getters.
- Modify `ReaderRoute.kt`, `PagedReaderScreen.kt`, `WebtoonReaderScreen.kt`, `ComicReaderScreen.kt`, `NovelReaderScreen.kt`, `EpubReaderScreen.kt` — thread params + invoke effect.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt` — `statsState`.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSections.kt` — `STATISTICS` section.
- Create `app/src/main/kotlin/com/komgareader/app/ui/settings/StatisticsSettingsContent.kt`.
- Modify i18n: `Strings.kt`, `StringsDe`, `StringsEn`, `MapBackedStrings.kt`.
- Test: `app/src/test/kotlin/com/komgareader/app/data/ReadingSessionTrackerTest.kt`.

---

## Task 1: Domain — ReaderKind, models, caps

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ReadingStats.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/ReadingTimeCaps.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/ReadingTimeCapsTest.kt`

- [ ] **Step 1: Write the model file** (no logic, no test yet)

`domain/src/main/kotlin/com/komgareader/domain/model/ReadingStats.kt`:
```kotlin
package com.komgareader.domain.model

/** The four reader types whose reading time is tracked separately. */
enum class ReaderKind { PAGED, WEBTOON, COMIC, NOVEL }

/** One reading session: time spent in a single reader on a single book. */
data class ReadingSession(
    val readerKind: ReaderKind,
    val bookRemoteId: String,
    val sourceId: Long,
    val startTs: Long,
    val durationMs: Long,
)

/** Aggregated, ready-to-display statistics. */
data class ReadingStats(
    val totalMs: Long = 0L,
    val perKindMs: Map<ReaderKind, Long> = emptyMap(),
    val startedWorks: Int = 0,
    val finishedWorks: Int = 0,
)
```

- [ ] **Step 2: Write the failing test for capDeltaMs**

`domain/src/test/kotlin/com/komgareader/domain/usecase/ReadingTimeCapsTest.kt`:
```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingTimeCapsTest {
    @Test fun `delta below cap is kept verbatim`() {
        val raw = 3L * 60_000 + 18_000 // 3.3 min, below PAGED cap (5 min)
        assertEquals(raw, ReadingTimeCaps.capDeltaMs(ReaderKind.PAGED, raw))
    }

    @Test fun `delta above cap is clipped to the cap`() {
        val raw = 12L * 60_000 // 12 min gap
        assertEquals(5L * 60_000, ReadingTimeCaps.capDeltaMs(ReaderKind.PAGED, raw))
    }

    @Test fun `negative or zero delta becomes zero`() {
        assertEquals(0L, ReadingTimeCaps.capDeltaMs(ReaderKind.NOVEL, -500L))
        assertEquals(0L, ReadingTimeCaps.capDeltaMs(ReaderKind.NOVEL, 0L))
    }

    @Test fun `each kind has its own cap`() {
        assertEquals(2L * 60_000, ReadingTimeCaps.capMsFor(ReaderKind.WEBTOON))
        assertEquals(5L * 60_000, ReadingTimeCaps.capMsFor(ReaderKind.PAGED))
        assertEquals(5L * 60_000, ReadingTimeCaps.capMsFor(ReaderKind.COMIC))
        assertEquals(7L * 60_000, ReadingTimeCaps.capMsFor(ReaderKind.NOVEL))
    }
}
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ReadingTimeCapsTest"`
Expected: FAIL (unresolved reference `ReadingTimeCaps`).

- [ ] **Step 4: Implement ReadingTimeCaps**

`domain/src/main/kotlin/com/komgareader/domain/usecase/ReadingTimeCaps.kt`:
```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderKind

/**
 * Per-reader-type caps for a single page's reading-time delta. The cap is an **idle guard**:
 * real active per-page reading is far below it (a comic page averages ~3.75 s), so it never
 * clips genuine reading — only a device left lying open between page turns.
 */
object ReadingTimeCaps {
    private val capMs: Map<ReaderKind, Long> = mapOf(
        ReaderKind.WEBTOON to 2L * 60_000,
        ReaderKind.PAGED to 5L * 60_000,
        ReaderKind.COMIC to 5L * 60_000,
        ReaderKind.NOVEL to 7L * 60_000,
    )

    fun capMsFor(kind: ReaderKind): Long = capMs.getValue(kind)

    /** Below cap → verbatim; above cap → clipped; negative/zero → 0. */
    fun capDeltaMs(kind: ReaderKind, rawDeltaMs: Long): Long =
        rawDeltaMs.coerceIn(0L, capMsFor(kind))
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ReadingTimeCapsTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ReadingStats.kt \
        domain/src/main/kotlin/com/komgareader/domain/usecase/ReadingTimeCaps.kt \
        domain/src/test/kotlin/com/komgareader/domain/usecase/ReadingTimeCapsTest.kt
git commit -m "feat(domain): ReaderKind, reading-stats models, per-kind time caps"
```

---

## Task 2: Domain — aggregation + work counting + duration split

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/ReadingStatsAggregator.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/ReadingStatsAggregatorTest.kt`

- [ ] **Step 1: Write the failing test**

`domain/src/test/kotlin/com/komgareader/domain/usecase/ReadingStatsAggregatorTest.kt`:
```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingStatsAggregatorTest {
    private fun session(kind: ReaderKind, ms: Long) =
        ReadingSession(kind, "b", 1L, startTs = 0L, durationMs = ms)

    @Test fun `total and per-kind sums over sessions`() {
        val sessions = listOf(
            session(ReaderKind.PAGED, 1000),
            session(ReaderKind.PAGED, 500),
            session(ReaderKind.NOVEL, 2000),
        )
        val stats = ReadingStatsAggregator.aggregate(
            sessions, pagedCompleted = emptyList(), novelFractions = emptyList(),
        )
        assertEquals(3500, stats.totalMs)
        assertEquals(1500, stats.perKindMs[ReaderKind.PAGED])
        assertEquals(2000, stats.perKindMs[ReaderKind.NOVEL])
        assertEquals(0, stats.perKindMs[ReaderKind.WEBTOON])
        assertEquals(0, stats.perKindMs[ReaderKind.COMIC])
    }

    @Test fun `empty input is all-zero with every kind present`() {
        val stats = ReadingStatsAggregator.aggregate(emptyList(), emptyList(), emptyList())
        assertEquals(0, stats.totalMs)
        assertEquals(0, stats.startedWorks)
        assertEquals(0, stats.finishedWorks)
        ReaderKind.entries.forEach { assertEquals(0, stats.perKindMs[it]) }
    }

    @Test fun `started counts every progressed work, finished counts completed and novels at threshold`() {
        val stats = ReadingStatsAggregator.aggregate(
            sessions = emptyList(),
            pagedCompleted = listOf(true, false, false), // 3 started, 1 finished
            novelFractions = listOf(1.0f, 0.99f, 0.5f),   // 3 started, 2 finished (>= 0.99)
        )
        assertEquals(6, stats.startedWorks)
        assertEquals(3, stats.finishedWorks)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ReadingStatsAggregatorTest"`
Expected: FAIL (unresolved reference `ReadingStatsAggregator`).

- [ ] **Step 3: Implement ReadingStatsAggregator**

`domain/src/main/kotlin/com/komgareader/domain/usecase/ReadingStatsAggregator.kt`:
```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats

/**
 * Pure aggregation of the session log + progress facts into [ReadingStats].
 * Time aggregates come from the sessions; work counts are derived from progress rows:
 * started = any progressed work; finished = completed paged works + novels at/above [NOVEL_DONE].
 */
object ReadingStatsAggregator {
    /** Novel completion threshold (no `completed` flag on `novel_progress`; use fraction). */
    const val NOVEL_DONE: Float = 0.99f

    fun aggregate(
        sessions: List<ReadingSession>,
        pagedCompleted: List<Boolean>,
        novelFractions: List<Float>,
    ): ReadingStats {
        val perKind = ReaderKind.entries.associateWith { k ->
            sessions.filter { it.readerKind == k }.sumOf { it.durationMs }
        }
        return ReadingStats(
            totalMs = sessions.sumOf { it.durationMs },
            perKindMs = perKind,
            startedWorks = pagedCompleted.size + novelFractions.size,
            finishedWorks = pagedCompleted.count { it } + novelFractions.count { it >= NOVEL_DONE },
        )
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ReadingStatsAggregatorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/usecase/ReadingStatsAggregator.kt \
        domain/src/test/kotlin/com/komgareader/domain/usecase/ReadingStatsAggregatorTest.kt
git commit -m "feat(domain): pure reading-stats aggregation + work counting"
```

---

## Task 3: Domain — ReadingStatsRepository interface

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/repository/ReadingStatsRepository.kt`

- [ ] **Step 1: Write the interface**

`domain/src/main/kotlin/com/komgareader/domain/repository/ReadingStatsRepository.kt`:
```kotlin
package com.komgareader.domain.repository

import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats
import kotlinx.coroutines.flow.Flow

/** Local-only reading statistics. No server sync (single user). */
interface ReadingStatsRepository {
    /** Append one finished reading session to the log. */
    suspend fun record(session: ReadingSession)

    /** Reactive aggregated statistics (time from the session log; work counts from progress). */
    fun observeStats(): Flow<ReadingStats>
}
```

- [ ] **Step 2: Compile-check the domain module**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/ReadingStatsRepository.kt
git commit -m "feat(domain): ReadingStatsRepository interface"
```

---

## Task 4: Data — entity, DAO, migration v17→v18

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/db/ReadingSessionDao.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/db/NovelProgressDao.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`

> **Migration safety (see memory `room-migration-destructive-pitfall`):** this is a pure
> `CREATE TABLE` for a brand-new table — **no** `ALTER ... ADD COLUMN`, no table recreate.
> Follow the existing `novel_progress` v11→v12 precedent exactly. Bump the `@Database` version
> and register the migration; do **not** rely on `fallbackToDestructiveMigration` to cover it.

- [ ] **Step 1: Add the entity to Entities.kt**

Append to `data/src/main/kotlin/com/komgareader/data/db/Entities.kt`:
```kotlin
@Entity(tableName = "reading_session")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val readerKind: String,
    val bookRemoteId: String,
    val sourceId: Long,
    val startTs: Long,
    val durationMs: Long,
)
```
(Ensure `androidx.room.Entity` and `androidx.room.PrimaryKey` are imported — they already are for the other entities in this file.)

- [ ] **Step 2: Create ReadingSessionDao**

`data/src/main/kotlin/com/komgareader/data/db/ReadingSessionDao.kt`:
```kotlin
package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Insert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_session")
    fun observeAll(): Flow<List<ReadingSessionEntity>>
}
```

- [ ] **Step 3: Add observeAll() to NovelProgressDao**

In `data/src/main/kotlin/com/komgareader/data/db/NovelProgressDao.kt`, add inside the interface:
```kotlin
    @Query("SELECT * FROM novel_progress")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<NovelProgressEntity>>
```

- [ ] **Step 4: Wire entity + DAO + migration + version in AppDatabase.kt**

In `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`:
- Add `ReadingSessionEntity::class` to the `@Database(entities = [...])` list.
- Change `version = 17` to `version = 18`.
- Add the abstract DAO accessor: `abstract fun readingSessionDao(): ReadingSessionDao`.
- Define the migration next to the existing ones:
```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_session` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`readerKind` TEXT NOT NULL, " +
                "`bookRemoteId` TEXT NOT NULL, " +
                "`sourceId` INTEGER NOT NULL, " +
                "`startTs` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL)"
        )
    }
}
```
- Register it in the `.addMigrations(...)` call (append `MIGRATION_17_18`).

> If `Migration` / `SupportSQLiteDatabase` imports are not yet present in this file, add
> `import androidx.room.migration.Migration` and `import androidx.sqlite.db.SupportSQLiteDatabase`
> (the file already uses them for the other 16 migrations — match its style).

- [ ] **Step 5: Compile-check data module**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Room schema generation succeeds for v18).

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/Entities.kt \
        data/src/main/kotlin/com/komgareader/data/db/ReadingSessionDao.kt \
        data/src/main/kotlin/com/komgareader/data/db/NovelProgressDao.kt \
        data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt
git commit -m "feat(data): reading_session table + DAO + migration v17->v18"
```

---

## Task 5: Data — RoomReadingStatsRepository + DI

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/repository/RoomReadingStatsRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`

- [ ] **Step 1: Implement the repository**

`data/src/main/kotlin/com/komgareader/data/repository/RoomReadingStatsRepository.kt`:
```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.NovelProgressDao
import com.komgareader.data.db.ReadProgressDao
import com.komgareader.data.db.ReadingSessionDao
import com.komgareader.data.db.ReadingSessionEntity
import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats
import com.komgareader.domain.repository.ReadingStatsRepository
import com.komgareader.domain.usecase.ReadingStatsAggregator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomReadingStatsRepository(
    private val sessions: ReadingSessionDao,
    private val readProgress: ReadProgressDao,
    private val novelProgress: NovelProgressDao,
) : ReadingStatsRepository {

    override suspend fun record(session: ReadingSession) {
        sessions.insert(
            ReadingSessionEntity(
                readerKind = session.readerKind.name,
                bookRemoteId = session.bookRemoteId,
                sourceId = session.sourceId,
                startTs = session.startTs,
                durationMs = session.durationMs,
            ),
        )
    }

    override fun observeStats(): Flow<ReadingStats> =
        combine(
            sessions.observeAll(),
            readProgress.observeAll(),
            novelProgress.observeAll(),
        ) { sessionRows, pagedRows, novelRows ->
            ReadingStatsAggregator.aggregate(
                sessions = sessionRows.map { row ->
                    ReadingSession(
                        readerKind = runCatching { ReaderKind.valueOf(row.readerKind) }
                            .getOrDefault(ReaderKind.PAGED),
                        bookRemoteId = row.bookRemoteId,
                        sourceId = row.sourceId,
                        startTs = row.startTs,
                        durationMs = row.durationMs,
                    )
                },
                pagedCompleted = pagedRows.map { it.completed },
                novelFractions = novelRows.map { it.fraction },
            )
        }
}
```

> Verify the property names while implementing: `ReadProgressEntity.completed: Boolean` and
> `NovelProgressEntity.fraction: Float` (both confirmed in `Entities.kt`). `ReadProgressDao`
> already exposes `observeAll(): Flow<List<ReadProgressEntity>>`.

- [ ] **Step 2: Provide it in DataModule**

In `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`, add (matching the existing
`@Provides @Singleton` style):
```kotlin
@Provides
@Singleton
fun readingStatsRepository(db: AppDatabase): ReadingStatsRepository =
    RoomReadingStatsRepository(
        sessions = db.readingSessionDao(),
        readProgress = db.readProgressDao(),
        novelProgress = db.novelProgressDao(),
    )
```
(Add imports for `ReadingStatsRepository` and `RoomReadingStatsRepository`.)

- [ ] **Step 3: Compile-check data module**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/repository/RoomReadingStatsRepository.kt \
        data/src/main/kotlin/com/komgareader/data/di/DataModule.kt
git commit -m "feat(data): RoomReadingStatsRepository + DI binding"
```

---

## Task 6: Data — migration test v17→v18 (on-disk)

**Files:**
- Create: `data/src/androidTest/kotlin/com/komgareader/data/db/Migration17To18Test.kt`

> In-memory upgrade tests are **falsely green** for migrations (memory `room-migration-destructive-pitfall`).
> Use `MigrationTestHelper` with a real on-disk DB. Check whether the data module already has a
> `MigrationTestHelper`-based test (e.g. an existing migration test) and mirror its setup, including
> the exported schema JSON requirement (`room.schemaLocation` must be configured — it already is if
> prior migration tests exist).

- [ ] **Step 1: Write the migration test**

`data/src/androidTest/kotlin/com/komgareader/data/db/Migration17To18Test.kt`:
```kotlin
package com.komgareader.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class Migration17To18Test {
    private val dbName = "migration-17-18-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrate17To18_createsReadingSessionTable_keepsExistingData() {
        helper.createDatabase(dbName, 17).apply {
            execSQL(
                "INSERT INTO read_progress " +
                    "(bookRemoteId, sourceId, page, completed, totalPages, dirty, updatedAt) " +
                    "VALUES ('b1', 1, 3, 1, 10, 0, 123)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 18, true, MIGRATION_17_18)

        // existing row survived
        db.query("SELECT COUNT(*) FROM read_progress").use {
            it.moveToFirst(); assertTrue(it.getInt(0) == 1)
        }
        // new table is usable
        db.execSQL(
            "INSERT INTO reading_session " +
                "(readerKind, bookRemoteId, sourceId, startTs, durationMs) " +
                "VALUES ('PAGED', 'b1', 1, 0, 5000)"
        )
        db.query("SELECT COUNT(*) FROM reading_session").use {
            it.moveToFirst(); assertTrue(it.getInt(0) == 1)
        }
        db.close()
    }
}
```

> Match the `MigrationTestHelper` constructor to the Room version in this project — if existing
> migration tests use the 3-arg constructor (`instrumentation, AppDatabase::class.java, factory`),
> use that signature instead. Copy the exact form from a sibling migration test if one exists.

- [ ] **Step 2: Run the migration test**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "com.komgareader.data.db.Migration17To18Test"`
Expected: PASS (requires a running emulator; start `eink_test` AVD if needed — see memory `local-test-komga`).

- [ ] **Step 3: Commit**

```bash
git add data/src/androidTest/kotlin/com/komgareader/data/db/Migration17To18Test.kt
git commit -m "test(data): v17->v18 migration creates reading_session, preserves data"
```

---

## Task 7: App — ReadingSessionTracker (event-driven, capped)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/ReadingSessionTracker.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/data/ReadingSessionTrackerTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/com/komgareader/app/data/ReadingSessionTrackerTest.kt`:
```kotlin
package com.komgareader.app.data

import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats
import com.komgareader.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingRepo : ReadingStatsRepository {
    val recorded = mutableListOf<ReadingSession>()
    override suspend fun record(session: ReadingSession) { recorded.add(session) }
    override fun observeStats(): Flow<ReadingStats> = flowOf(ReadingStats())
}

class ReadingSessionTrackerTest {
    private fun tracker(repo: ReadingStatsRepository, scope: TestScope): ReadingSessionTracker {
        val t = ReadingSessionTracker(repo, scope)
        return t
    }

    @Test fun `sums capped per-page deltas and records on leave`() = runTest {
        val repo = RecordingRepo()
        val t = tracker(repo, this)
        var now = 1_000L
        t.clock = { now }

        t.enter(ReaderKind.PAGED, "b1", 7L)   // lastTs = 1000
        now = 1_000L + 90_000                 // +90s (below 5min cap)
        t.page()                              // acc += 90s
        now += 12L * 60_000                   // +12min gap (above cap)
        t.page()                              // acc += 5min cap
        t.leave()

        assertEquals(1, repo.recorded.size)
        val s = repo.recorded.single()
        assertEquals(ReaderKind.PAGED, s.readerKind)
        assertEquals("b1", s.bookRemoteId)
        assertEquals(7L, s.sourceId)
        assertEquals(90_000L + 5L * 60_000, s.durationMs)
    }

    @Test fun `zero-duration session is not recorded`() = runTest {
        val repo = RecordingRepo()
        val t = tracker(repo, this)
        t.clock = { 5_000L }
        t.enter(ReaderKind.NOVEL, "b2", 1L)
        t.leave()
        assertEquals(0, repo.recorded.size)
    }

    @Test fun `entering a new book flushes the previous session`() = runTest {
        val repo = RecordingRepo()
        val t = tracker(repo, this)
        var now = 0L
        t.clock = { now }
        t.enter(ReaderKind.COMIC, "b1", 1L)
        now = 60_000
        t.page()                              // acc 60s
        t.enter(ReaderKind.COMIC, "b2", 1L)   // flush b1
        assertEquals(1, repo.recorded.size)
        assertEquals("b1", repo.recorded.single().bookRemoteId)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.data.ReadingSessionTrackerTest"`
Expected: FAIL (unresolved reference `ReadingSessionTracker`).

- [ ] **Step 3: Implement the tracker**

`app/src/main/kotlin/com/komgareader/app/data/ReadingSessionTracker.kt`:
```kotlin
package com.komgareader.app.data

import com.komgareader.app.di.ApplicationScope
import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.repository.ReadingStatsRepository
import com.komgareader.domain.usecase.ReadingTimeCaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event-driven, capped-delta reading-time measurement — the single place this logic lives.
 * **No ticking timer** (E-Ink battery): time is only sampled at events that already happen
 * (enter / page-settle / leave). A page delta above the per-kind cap is clipped (idle guard).
 * Flushing uses the application scope, so a session survives reader/VM teardown (offline-first).
 */
@Singleton
class ReadingSessionTracker @Inject constructor(
    private val repo: ReadingStatsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    /** Overridable for tests. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private var kind: ReaderKind? = null
    private var bookRemoteId: String = ""
    private var sourceId: Long = 0L
    private var startTs: Long = 0L
    private var lastTs: Long = 0L
    private var accMs: Long = 0L

    @Synchronized
    fun enter(kind: ReaderKind, bookRemoteId: String, sourceId: Long) {
        flushInternal()
        this.kind = kind
        this.bookRemoteId = bookRemoteId
        this.sourceId = sourceId
        val now = clock()
        startTs = now
        lastTs = now
        accMs = 0L
    }

    @Synchronized
    fun page() {
        val k = kind ?: return
        val now = clock()
        accMs += ReadingTimeCaps.capDeltaMs(k, now - lastTs)
        lastTs = now
    }

    @Synchronized
    fun leave() = flushInternal()

    private fun flushInternal() {
        val k = kind ?: return
        val duration = accMs
        val session = ReadingSession(k, bookRemoteId, sourceId, startTs, duration)
        kind = null
        accMs = 0L
        if (duration > 0L) {
            appScope.launch { runCatching { repo.record(session) } }
        }
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.data.ReadingSessionTrackerTest"`
Expected: PASS (3 tests). (`runTest`'s `TestScope` is the injected `appScope`; the launched
`record` runs on the test dispatcher.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/ReadingSessionTracker.kt \
        app/src/test/kotlin/com/komgareader/app/data/ReadingSessionTrackerTest.kt
git commit -m "feat(app): ReadingSessionTracker (event-driven capped reading-time)"
```

---

## Task 8: App — ReadingSessionEffect + reader wiring

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReadingSessionEffect.kt`
- Modify: `ReaderViewModel.kt`, `ReaderRoute.kt`, `PagedReaderScreen.kt`, `WebtoonReaderScreen.kt`, `ComicReaderScreen.kt`, `NovelReaderScreen.kt`, `EpubReaderScreen.kt`

- [ ] **Step 1: Create the holder + effect**

`app/src/main/kotlin/com/komgareader/app/ui/reader/ReadingSessionEffect.kt`:
```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.komgareader.app.data.ReadingSessionTracker
import com.komgareader.domain.model.ReaderKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Bridges the singleton [ReadingSessionTracker] into Composition (mirrors EinkContextHolder). */
@HiltViewModel
class ReadingSessionHolder @Inject constructor(
    val tracker: ReadingSessionTracker,
) : ViewModel()

/**
 * Drives reading-time tracking for the current reader screen. Enter on book load, accumulate a
 * capped delta on every page change, flush on dispose. No ticking timer (E-Ink). A settings
 * detour does not over-count: no page turns happen there, and the one delta on return is
 * cap-bounded by [ReadingSessionTracker].
 */
@Composable
fun ReadingSessionEffect(
    readerKind: ReaderKind,
    bookRemoteId: String,
    sourceId: Long,
    currentPage: Int,
) {
    val holder = hiltViewModel<ReadingSessionHolder>()
    DisposableEffect(bookRemoteId) {
        holder.tracker.enter(readerKind, bookRemoteId, sourceId)
        onDispose { holder.tracker.leave() }
    }
    LaunchedEffect(currentPage) { holder.tracker.page() }
}
```

- [ ] **Step 2: Expose ids on ReaderViewModel**

In `ReaderViewModel.kt`, right after the private `routeSourceId` declaration (around line 61), add:
```kotlin
    /** Route-scoped identity, identical for every reader of this book (used by stats tracking). */
    val bookRemoteId: String get() = bookId
    val sourceId: Long get() = routeSourceId
```

- [ ] **Step 3: Thread params from ReaderRoute into the screens**

In `ReaderRoute.kt`, pass three new arguments to each reader screen call. The `bookRemoteId` and
`sourceId` come from the route-level `viewModel`; the `readerKind` is the literal per branch:
- Novel branch → `ReaderKind.NOVEL`
- Rendered (EpubReaderScreen) → `ReaderKind.PAGED`
- Streamed/Webtoon `when(mode)`: `PAGED -> ReaderKind.PAGED`, `WEBTOON -> ReaderKind.WEBTOON`, `COMIC -> ReaderKind.COMIC`

For each screen call add:
```kotlin
    readerKind = <kind for this branch>,
    bookRemoteId = viewModel.bookRemoteId,
    sourceId = viewModel.sourceId,
```
(Import `com.komgareader.domain.model.ReaderKind` in `ReaderRoute.kt`.)

- [ ] **Step 4: Accept the params and invoke the effect in each reader screen**

Each reader screen gains three params and one effect call placed next to its existing
`onPageSettled` hook, using its screen-local current page:

**PagedReaderScreen.kt** — add params `readerKind: ReaderKind, bookRemoteId: String, sourceId: Long`;
near the existing `LaunchedEffect(pagerState.currentPage) { viewModel.onPageSettled(...) }`:
```kotlin
ReadingSessionEffect(readerKind, bookRemoteId, sourceId, pagerState.currentPage)
```

**WebtoonReaderScreen.kt** — add the three params; current page is `listState.firstVisibleItemIndex`:
```kotlin
ReadingSessionEffect(readerKind, bookRemoteId, sourceId, listState.firstVisibleItemIndex)
```

**ComicReaderScreen.kt** — add the three params; current page is `state.position.page`:
```kotlin
ReadingSessionEffect(readerKind, bookRemoteId, sourceId, state.position.page)
```

**NovelReaderScreen.kt** — add the three params; current page is `state.currentPage`:
```kotlin
ReadingSessionEffect(readerKind, bookRemoteId, sourceId, state.currentPage)
```

**EpubReaderScreen.kt** — add the three params; current page is `pagerState.currentPage`:
```kotlin
ReadingSessionEffect(readerKind, bookRemoteId, sourceId, pagerState.currentPage)
```

> `ReadingSessionEffect` is a top-level `@Composable`; call it directly in the screen body, **not**
> inside a `LaunchedEffect`. Import `com.komgareader.domain.model.ReaderKind` and
> `com.komgareader.app.ui.reader.ReadingSessionEffect` where needed (same package for the latter).

- [ ] **Step 5: Build the app module**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ReadingSessionEffect.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderRoute.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/PagedReaderScreen.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/WebtoonReaderScreen.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderScreen.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/EpubReaderScreen.kt
git commit -m "feat(app): wire ReadingSessionEffect into all reader screens"
```

---

## Task 9: App — Settings Statistics section + i18n

**Files:**
- Modify: i18n `Strings.kt`, `StringsDe`, `StringsEn`, `MapBackedStrings.kt`
- Modify: `SettingsViewModel.kt`, `SettingsSections.kt`
- Create: `StatisticsSettingsContent.kt`

- [ ] **Step 1: Add i18n keys**

In the `Strings` interface (`app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`) add:
```kotlin
    val statsTitle: String
    val statsTotalTime: String
    val statsPerReader: String
    val statsStarted: String
    val statsFinished: String
    val statsReaderPaged: String
    val statsReaderWebtoon: String
    val statsReaderComic: String
    val statsReaderNovel: String
    val statsEmpty: String
    /** Human-readable duration, e.g. "3 Std 12 Min" / "3 h 12 min". */
    fun statsDuration(hours: Int, minutes: Int): String
```

In `StringsDe`:
```kotlin
    override val statsTitle = "Statistik"
    override val statsTotalTime = "Gesamtlesezeit"
    override val statsPerReader = "Nach Lesemodus"
    override val statsStarted = "Begonnene Werke"
    override val statsFinished = "Abgeschlossene Werke"
    override val statsReaderPaged = "Seiten"
    override val statsReaderWebtoon = "Webtoon"
    override val statsReaderComic = "Comic (geführt)"
    override val statsReaderNovel = "Roman"
    override val statsEmpty = "Noch keine Lesezeit erfasst."
    override fun statsDuration(hours: Int, minutes: Int) =
        if (hours > 0) "$hours Std $minutes Min" else "$minutes Min"
```

In `StringsEn`:
```kotlin
    override val statsTitle = "Statistics"
    override val statsTotalTime = "Total reading time"
    override val statsPerReader = "By reader mode"
    override val statsStarted = "Started works"
    override val statsFinished = "Finished works"
    override val statsReaderPaged = "Paged"
    override val statsReaderWebtoon = "Webtoon"
    override val statsReaderComic = "Comic (guided)"
    override val statsReaderNovel = "Novel"
    override val statsEmpty = "No reading time recorded yet."
    override fun statsDuration(hours: Int, minutes: Int) =
        if (hours > 0) "$hours h $minutes min" else "$minutes min"
```

In `MapBackedStrings.kt` add the override lines, e.g.:
```kotlin
    override val statsTitle: String get() = overrides["statsTitle"] ?: fallback.statsTitle
    override val statsTotalTime: String get() = overrides["statsTotalTime"] ?: fallback.statsTotalTime
    override val statsPerReader: String get() = overrides["statsPerReader"] ?: fallback.statsPerReader
    override val statsStarted: String get() = overrides["statsStarted"] ?: fallback.statsStarted
    override val statsFinished: String get() = overrides["statsFinished"] ?: fallback.statsFinished
    override val statsReaderPaged: String get() = overrides["statsReaderPaged"] ?: fallback.statsReaderPaged
    override val statsReaderWebtoon: String get() = overrides["statsReaderWebtoon"] ?: fallback.statsReaderWebtoon
    override val statsReaderComic: String get() = overrides["statsReaderComic"] ?: fallback.statsReaderComic
    override val statsReaderNovel: String get() = overrides["statsReaderNovel"] ?: fallback.statsReaderNovel
    override val statsEmpty: String get() = overrides["statsEmpty"] ?: fallback.statsEmpty
    override fun statsDuration(hours: Int, minutes: Int) = fallback.statsDuration(hours, minutes)
```

- [ ] **Step 2: Expose statsState on SettingsViewModel**

In `SettingsViewModel.kt`, inject `ReadingStatsRepository` (add constructor param
`private val readingStats: ReadingStatsRepository`) and expose:
```kotlin
    val statsState: StateFlow<ReadingStats> =
        readingStats.observeStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingStats())
```
(Add imports for `ReadingStats`, `ReadingStatsRepository`, `stateIn`, `SharingStarted` if missing.)

- [ ] **Step 3: Create the content composable**

`app/src/main/kotlin/com/komgareader/app/ui/settings/StatisticsSettingsContent.kt`:
```kotlin
package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingStats

@Composable
fun StatisticsSettingsContent(viewModel: SettingsViewModel) {
    val s = LocalStrings.current
    val stats by viewModel.statsState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatRow(s.statsTotalTime, formatDuration(stats.totalMs))
        Text(s.statsPerReader)
        StatRow(s.statsReaderPaged, formatDuration(stats.perKindMs[ReaderKind.PAGED] ?: 0L))
        StatRow(s.statsReaderWebtoon, formatDuration(stats.perKindMs[ReaderKind.WEBTOON] ?: 0L))
        StatRow(s.statsReaderComic, formatDuration(stats.perKindMs[ReaderKind.COMIC] ?: 0L))
        StatRow(s.statsReaderNovel, formatDuration(stats.perKindMs[ReaderKind.NOVEL] ?: 0L))
        StatRow(s.statsStarted, stats.startedWorks.toString())
        StatRow(s.statsFinished, stats.finishedWorks.toString())
        if (stats.totalMs == 0L) Text(s.statsEmpty)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
    }
}

@Composable
private fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000L).toInt()
    return LocalStrings.current.statsDuration(totalMin / 60, totalMin % 60)
}
```

> Keep the look E-Ink-flat (no elevation/animation). If the project has a card primitive used by
> other settings rows (check `SettingsContent.kt` for an existing flat-card/section helper), wrap
> each `StatRow` in it for visual consistency instead of bare `Row`s. Use existing tokens, not
> hardcoded colors.

- [ ] **Step 4: Register the STATISTICS section**

In `SettingsSections.kt`:
- Add `STATISTICS` to the `SettingsSectionId` enum (locate the enum — it may be in this file or a
  sibling; add the entry).
- In `buildSettingsSections(...)`, add a section **before** `ABOUT`:
```kotlin
SettingsSection(
    id = SettingsSectionId.STATISTICS,
    icon = AppIcons.Stats,            // see step 5
    title = s.statsTitle,
    searchTerms = listOf(s.statsTitle, s.statsTotalTime, s.statsStarted, s.statsFinished),
    content = { StatisticsSettingsContent(viewModel) },
),
```

- [ ] **Step 5: Provide the icon**

Check `app/src/main/kotlin/com/komgareader/app/ui/icons/` for an existing chart/stats glyph in
`IconKey` / `DefaultIconPack`. If one exists, reuse it as `AppIcons.Stats`. If not, add an
`IconKey.Stats` + a Lucide glyph mapping in `DefaultIconPack` and an `AppIcons.Stats` accessor,
following the existing icon-registry pattern (`eink-design-language.md` → Icon-System). Do **not**
use a Material icon.

- [ ] **Step 6: Build + run unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all unit tests pass (i18n de/en compile-time parity holds).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/ \
        app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt \
        app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSections.kt \
        app/src/main/kotlin/com/komgareader/app/ui/settings/StatisticsSettingsContent.kt \
        app/src/main/kotlin/com/komgareader/app/ui/icons/
git commit -m "feat(app): Settings Statistics section + i18n (de/en)"
```

---

## Task 10: Verify end-to-end + docs

- [ ] **Step 1: Full build + all unit tests**

Run: `./gradlew :domain:test :data:compileDebugKotlin :app:testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: E2E on emulator** (start `eink_test` AVD; see memory `local-test-komga`)

- Read a few pages in a paged reader, leave the reader.
- Open Settings → Statistics.
- Verify: total time non-zero; the **Paged** bucket increased; **Started works** ≥ 1.
- Read a few pages in another reader type (e.g. webtoon or novel) → its bucket increases independently.
- Finish a book (reach the last page / completed) → **Finished works** increments.
- Leave a reader open for > the cap between two page turns → that one delta is clipped (sanity:
  total grows by at most the cap, not the full idle gap).

Capture a screenshot of the Statistics section as the completion evidence.

- [ ] **Step 3: docs-match-code**

Run the `komga-doc-sync` skill. At minimum update `CLAUDE.md` (note the new `ReaderKind` domain
type + the reading-stats seam touchpoints: `ReadingSessionTracker`, `ReadingSessionEffect`,
`ReadingStatsRepository`, Room v18). The change rides existing seams, so `.claude/rules/*` likely
need no edit — confirm via the skill.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "docs: record reading-statistics module (CLAUDE.md, status)"
```

---

## Self-Review notes (author)

- **Spec coverage:** total time (Tasks 1,2,7,9) · per-reader split (Tasks 1,2,8,9) · started/finished
  derived from progress (Tasks 2,5,9) · session-log storage + migration (Tasks 4,5,6) · event-driven
  capped, no timer (Tasks 1,7) · central single tracking site (Tasks 7,8) · Settings section + i18n
  (Task 9) · offline-first flush via appScope (Task 7) · E-Ink-flat UI (Task 9) · tests incl. on-disk
  migration test (Tasks 1,2,6,7) · docs (Task 10). All spec sections map to a task.
- **Type consistency:** `ReaderKind`, `ReadingSession`, `ReadingStats`, `ReadingTimeCaps.capDeltaMs`/
  `capMsFor`, `ReadingStatsAggregator.aggregate(sessions, pagedCompleted, novelFractions)`,
  `ReadingStatsRepository.record`/`observeStats`, `ReadingSessionTracker.enter`/`page`/`leave`/`clock`,
  `ReadingSessionEffect(readerKind, bookRemoteId, sourceId, currentPage)` — names used identically across tasks.
- **Verify-while-implementing flags** (read real code, don't assume): `MigrationTestHelper` constructor
  arity (Task 6); `ReadProgressDao.observeAll` exact name + `ReadProgressEntity.completed` /
  `NovelProgressEntity.fraction` field names (Task 5); existing flat-card settings helper + icon-registry
  entry (Task 9); exact insertion lines in the reader screens (Task 8).
