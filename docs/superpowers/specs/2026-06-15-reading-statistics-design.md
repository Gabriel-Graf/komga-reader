# Reading Statistics — Design

**Date:** 2026-06-15
**Status:** Approved (brainstorm)
**Scope:** A local, single-user reading-statistics module: total reading time, time split
per reader type, and counts of started / finished works, shown in a new Statistics section
in Settings. No server sync (single user, local-only by user decision).

## Goal

Give the user a first foundation of reading insights:

1. **Total reading time** across all readers.
2. **Time per reader type** (paged / webtoon / comic / novel).
3. **Started works** count and **finished works** count.

Designed to extend later (per-day charts, streaks, per-work breakdown) without a schema
rewrite — hence a session log, not bare counters.

## Non-Goals (YAGNI)

- No server sync (explicit user decision: local only).
- No live elapsed-time display during reading (would require a ticking timer → E-Ink
  battery cost; deliberately avoided).
- No charts / streaks / per-day views yet (the session log makes them possible later).
- No user-configurable caps (internal constants).

## Constraints

- **E-Ink first:** measurement must be **event-driven, never polling.** No ticking timer,
  no background wake-up. Battery cost ≈ zero (only `System.currentTimeMillis()` reads at
  events that already happen: lifecycle resume/pause + page settle). See
  `animation-gating.md` rationale (motion/wake-ups are expensive on E-Ink).
- **Offline-first:** session flush uses the application scope, like Novel `onCleared()`.
- **Source-agnostic:** statistics never name a concrete source; they key on
  `sourceId` + `bookRemoteId` only.
- **Shared structure before variants:** one central tracking site for all four readers,
  not a per-ViewModel copy (`shared-structure-before-variants.md`).

## Time Measurement — capped per-page deltas (event-driven)

The chosen model (Option 3 from brainstorm): accumulate the time between page-settle
events, **capping each single delta** at a per-reader-type maximum. The cap is a pure
**idle guard** — real active per-page reading time is far below any cap (a comic page
averages ~3.75 s; a fast 30-page manga chapter ~5–10 min ⇒ ~10–20 s/page), so the cap
never clips genuine reading, only a device left lying open.

- A delta **below** the cap is recorded **verbatim** (e.g. 3.3 min on a dense page → 3.3 min).
- A delta **above** the cap is clipped to the cap (e.g. 12 min gap → cap).
- Session duration = Σ of capped per-page deltas within the session.

### Internal caps (constants, not user-settable)

| ReaderKind | Cap |
|---|---|
| `WEBTOON` | 2 min |
| `PAGED`   | 5 min |
| `COMIC`   | 5 min |
| `NOVEL`   | 7 min |

(Reference reading-time values: visuallanguagelab.com comic-page study ~3.75 s/page;
~1.5 s/panel in 4-panel studies; fast manga chapter 5–10 min / 30 pages. Caps sit well
above active reading, so they only cut idle gaps.)

## Architecture (follows existing module seams)

```
domain/   ReaderKind enum · ReadingSession model · ReadingStats aggregate ·
          ReadingStatsRepository interface · pure cap/aggregation logic
data/     ReadingSessionEntity + DAO · RoomReadingStatsRepository ·
          Room v17→v18 migration (pure CREATE TABLE) · DataModule binding
app/      ReadingSessionEffect (one central tracker, ReaderRoute) ·
          ReaderKind mapping · Settings STATISTICS section + content ·
          SettingsViewModel stats flow · i18n keys (de+en)
```

### domain

- `enum ReaderKind { PAGED, WEBTOON, COMIC, NOVEL }`.
- `data class ReadingSession(readerKind, bookRemoteId, sourceId, startTs, durationMs)`.
- `data class ReadingStats(totalMs, perKindMs: Map<ReaderKind, Long>, startedWorks, finishedWorks)`.
- `interface ReadingStatsRepository`:
  - `suspend fun record(session: ReadingSession)`
  - `fun observeStats(): Flow<ReadingStats>` (time aggregates from the session log; work
    counts derived from the existing progress tables — see below).
- **Pure helpers (unit-tested first, TDD):**
  - `capDeltaMs(kind, rawDeltaMs): Long` — `min(rawDelta, cap[kind])`, negative/zero → 0.
  - Aggregation: total + per-kind sums from a list of sessions (pure, testable without Room).

### data

- `@Entity("reading_session") ReadingSessionEntity(@PrimaryKey(autoGenerate) id, readerKind:String, bookRemoteId:String, sourceId:Long, startTs:Long, durationMs:Long)`.
- `ReadingSessionDao`: `insert(e)`, `observeAll(): Flow<List<ReadingSessionEntity>>` (+ aggregate
  queries where cheaper: `SUM(durationMs)`, `SUM(durationMs) GROUP BY readerKind`).
- `RoomReadingStatsRepository`:
  - `record()` → `insert`.
  - `observeStats()` → combine session aggregates (time) with work counts derived from
    `ReadProgressDao` + `NovelProgressDao` (no new tracking, no progress-table migration):
    - **started** = distinct works with any progress row (`read_progress` rows +
      `novel_progress` rows).
    - **finished** = `read_progress.completed = true` count + `novel_progress.fraction ≥ 0.99` count.
    - Threshold `0.99` chosen over retrofitting a `completed` flag onto `novel_progress`
      (fewer moving parts, no migration of an existing table).
- **Migration v17→v18:** pure `CREATE TABLE reading_session (...)`. Non-destructive
  (`novel_progress` v11→v12 is the precedent). Bump `@Database(version = 18)`, register
  migration in `AppDatabase`. **No `ALTER ADD COLUMN` anywhere** (Room-migration destructive
  pitfall — see memory `room-migration-destructive-pitfall`).
- DI: provide `ReadingStatsRepository` in `DataModule`.

### app — central tracking (one site, all four readers)

`ReaderRoute` already dispatches `when(ViewerMode)` / `when(ReaderContent)` and knows the
reader kind at that point. Add **one** Composable effect there, modelled on the existing
`EinkContextEffect`:

```
ReadingSessionEffect(kind: ReaderKind, viewer: Viewer)
```

- `LifecycleResumeEffect`: on resume → start a session (record `startTs`, reset accumulator,
  `lastTs = now`). On pause/leave → flush the accumulated session via the application scope
  (offline-first, survives VM teardown like Novel `onCleared`).
- On each page-settle → `acc += capDeltaMs(kind, now - lastTs)`; `lastTs = now`.
- Zero-duration sessions are not recorded.

**Open sub-decision (resolve against real code in the plan):** the `Viewer` contract may
not currently expose an observable `currentPage` flow that the effect can collect.
- **Preferred:** add a read-only `currentPage: StateFlow<Int>` (or page-settle signal) to
  the `Viewer` contract — central, no per-reader duplication, keeps the single tracking site.
- **Fallback:** each reader VM calls the tracker from its existing `onPageSettled` /
  `onCleared` (4 touch-points). Acceptable but less DRY; only if extending `Viewer` proves
  invasive.
- Whichever is chosen, the **measurement logic lives in exactly one place** (the effect or a
  small injected tracker), not copied per reader.

The `ReaderKind` mapping is derived at the `ReaderRoute` dispatch from `ViewerMode`
(PAGED/WEBTOON/COMIC) and the Novel content branch → `NOVEL`.

### app — Settings UI

- New `SettingsSectionId.STATISTICS` in `buildSettingsSections()` (read-only; place before
  ABOUT). Icon from `AppIcons` (a chart/stats glyph; add an `IconKey` + `DefaultIconPack`
  mapping if none fits).
- `StatisticsSettingsContent(query)`: flat E-Ink cards (1.5px border, no elevation, no
  animation) showing: total reading time, a per-reader-type breakdown, started works,
  finished works. Times rendered human-readable (e.g. "3 Std 12 Min" / "3 h 12 min").
- `SettingsViewModel` exposes `statsState: StateFlow<ReadingStats>` (or a UI-mapped variant)
  from `ReadingStatsRepository.observeStats()`.
- i18n: new keys in `Strings` interface + `StringsDe` + `StringsEn` + `MapBackedStrings`
  fallback (e.g. `statsTitle`, `statsTotalTime`, `statsPerReader`, `statsStarted`,
  `statsFinished`, reader-kind labels, a `formatDuration(...)` helper or fun-style key).
  Real umlauts; compile-time de/en parity.

## Data flow

```
reader open → ReadingSessionEffect starts session (startTs, lastTs)
page settle → acc += capDeltaMs(kind, now - lastTs); lastTs = now
reader leave → appScope flush → ReadingStatsRepository.record(session) → Room insert
Settings open → observeStats() = sessions aggregate (time) ⊕ progress-derived work counts
              → SettingsViewModel.statsState → StatisticsSettingsContent renders
```

## Error handling

- Recording is best-effort: a failed insert is logged, never crashes the reader (wrapped,
  appScope). Missing/zero deltas simply contribute 0.
- `observeStats()` tolerates empty tables → all-zero `ReadingStats`.
- Work-count derivation tolerates a work present in both progress tables (it is keyed by
  `bookRemoteId`; novel and paged books do not share ids in practice — counted once per table,
  which matches "a novel work" vs "a paged work" being distinct works).

## Testing

- **Unit (pure, TDD first):**
  - `capDeltaMs`: below cap verbatim; above cap clipped; zero/negative → 0; each kind's cap.
  - Aggregation: total + per-kind sums over a session list (set and empty).
  - Work-count derivation: started/finished from progress rows (set and empty; completed
    flag; novel fraction ≥/< 0.99 threshold).
- **Migration:** v17→v18 opens an existing DB without data loss (real on-disk DB, not
  in-memory — per `room-migration-destructive-pitfall`, in-memory upgrade tests are
  falsely green).
- **E2E (emulator / local test Komga):** read a few pages in a reader → open Settings →
  Statistics section shows non-zero total time, the right per-type bucket incremented, and
  started/finished counts consistent with progress. Verify a left-open gap is capped.

## Docs to update in the same commit (docs-match-code)

- `CLAUDE.md` module table / invariants if a new domain type or seam touchpoint warrants it.
- `.claude/rules/*` only if a seam rule is affected (likely none — this rides existing seams).
- Run the `komga-doc-sync` skill before the final commit.
