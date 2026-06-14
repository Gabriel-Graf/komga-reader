# Novel Word Bookmarks — Design

Date: 2026-06-15
Status: Approved (brainstorming)
Scope: one implementation plan.

## Goal

Give the novel reader a fast, in-text multi-bookmark system. The reader can
**tap a single word to set or remove a bookmark** at that word, with each
bookmark auto-numbered on creation and optionally named later. A bookmark list
lets the reader jump to any marked spot, rename it, or delete it. Bookmarks are
marked visually on the rendered page.

This is a **novel-reader-only** feature (crengine reflow path). It does not
touch the paged/webtoon/comic readers.

## Decisions (locked in brainstorming)

| Topic | Decision |
|---|---|
| Tap conflict with existing thirds (prev/chrome/next) | A **bookmark mode** toggle in the reader chrome. Mode **on**: tap-on-word sets/removes, thirds paging suppressed. Mode **off**: normal thirds paging. |
| Set behavior | Tap sets immediately with the next free number (#1, #2 …). **No dialog** — reading is not interrupted. Naming/renaming happens later via the bookmark list. |
| Numbering | Monotonic `max+1`. Numbers are **not** reused after deletion (stable identity, simpler). |
| Visual marker | Two styles, **user-selectable**: (1) **underline** under the word, (3) **margin marker** (small numbered marker at the line edge). Both must be available as a setting. The exact settings location is decided in the plan — out of scope to fix here. |
| Position model | **xpointer-based** (same mechanism as reading progress). Layout-independent, survives relayout (font size / viewport change). |
| Sync | **Local-only.** Neither Komga nor OPDS has a per-word bookmark concept. This is a local reading aid, deliberately **not** placed in the sync queue (unlike chapter progress). |

## Approaches considered

- **A — xpointer-based (chosen).** Bookmarks are crengine xpointers, like the
  existing progress anchor. Jumping reuses `seekToAnchor`/`goToAnchor`. Survives
  relayout because an xpointer is layout-independent. Costs two new JNI calls
  (point→word, xpointer→rect).
- **B — page + coordinate.** Stores page index + pixel position. Simpler, but
  breaks on every relayout (a different font size repaginates) and cannot mark a
  word precisely. Rejected.
- **C — progress fraction.** Coarse, no word reference. Rejected.

## Architecture — three additive seam homes

The feature splits across three existing seams; no new aggregate class.

### 1. Render seam (Naht B, crengine) — two new JNI + engine-neutral domain types

New native functions in `cr3_bridge.cpp`, serialized in the existing
record/field format (`0x1E` record separator, `0x1F` field separator):

- `nativeXPointerAtPoint(handle, x, y): String` →
  `xpointer · word · left · top · right · bottom` (page-relative px). Empty
  string when no word is hit. crengine path: `getNodeByPoint(point)` → word
  `ldomXRange` → xpointer + word text + bounding rect.
- `nativeRectsForXPointers(handle, xpointers: Array<String>): String` →
  one record per xpointer that lies on the **currently rendered** page:
  `xpointer · left · top · right · bottom`. xpointers not on the current page are
  omitted. Used to draw markers for the visible page.

`ReflowableDocument` (domain) gains, with **default no-op** in `Document.kt` so
only crengine implements them and the seam stays clean:

```kotlin
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
data class WordHit(val xpointer: String, val word: String, val rect: IntRect)

fun wordAt(x: Int, y: Int): WordHit? = null
fun rectsFor(xpointers: List<String>): Map<String, IntRect> = emptyMap()
```

Jumping reuses the existing `seekToAnchor` / `goToAnchor` — nothing new.

### 2. Data seam — local-only Room table

```kotlin
data class NovelBookmark(
    val id: Long, val sourceId: Long, val bookId: String,
    val xpointer: String, val number: Int,
    val label: String?, val snippet: String, val createdAt: Long,
)
```

- Table `novel_bookmark`, keyed/queried by `(sourceId, bookId)`.
- `word` + surrounding `snippet` are captured **at set time** (from `WordHit`)
  so the list is meaningful offline and without re-rendering.
- `NovelBookmarkDao` (CRUD + `Flow<List<NovelBookmark>>` per source+book),
  `RoomNovelBookmarkRepository`, additive Room migration (new table — not a
  destructive column change, so the recreate-table pattern is unnecessary).
- Not registered with the sync queue.

### 3. App seam — NovelReader UI + ViewModel

- `NovelReaderViewModel`:
  - `bookmarks: StateFlow<List<NovelBookmark>>` for the current book (from repo).
  - `bookmarkMode: StateFlow<Boolean>` toggle.
  - `onWordTap(x, y)`: `wordAt(x,y)` → if no word, ignore; else toggle by
    xpointer match (set with `nextBookmarkNumber`, or remove the matching one).
  - `jumpTo(xpointer)` → `goToAnchor`.
  - `rename(id, label)`, `delete(id)`.
- **Tap integration via the existing declarative `ReaderTapZones` seam (A1b):**
  - mode **off** → `HorizontalThirds` as today (paging / chrome).
  - mode **on** → `tapZones = null` (the comic opt-out path) and the reader runs
    its own `pointerInput` hit-test in its content lambda to get raw `(x, y)`.
  - No change to the tap seam itself.
- **Marker overlay** drawn over the page bitmap: on page settle, call
  `rectsFor(visible bookmark xpointers)` and draw the configured style
  (underline or margin marker), monochrome black.
- **`NovelBookmarkPanel`** (built on the `NovelTocPanel` pattern): list of
  `#number` + name + snippet; tap = jump; rename + delete actions; empty state.
- Two reader-chrome action buttons (reuse the shared chrome actions row): a
  **bookmark-mode toggle** and an **open-bookmark-list** button. New
  `IconKey` + `AppIcons` mapping, new i18n keys.

### E-Ink invariants (host-enforced)

- Mode switch and marker appearance are **immediate** (no animation —
  `animation-gating.md`).
- Markers are monochrome black; no accent colour.
- Panel uses the `BaseDialog` / dialog-slot path like the other novel panels.

## TDD plan

Pure units first (`domain`, JVM):

- `nextBookmarkNumber(existing: List<Int>): Int` → smallest `max+1`, ≥1.
  Tests: empty→1, `[1,2]`→3, `[2]`→3 (no gap reuse).
- `toggleBookmark(existing, hit)` → set when xpointer absent, remove the matching
  one when present. Tests: set into empty, set second, remove existing.
- Bookmark ↔ Room entity mapper. Tests: full record and `label = null`.

Instrumented / device:

- `NovelBookmarkDao` CRUD + flow (androidTest, runs on emulator).
- crengine `wordAt` / `rectsFor` JNI (render-crengine androidTest against a test
  EPUB).

## Verification gap (important)

Per `CLAUDE.md`, the crengine `.so` is **missing for the x86_64 emulator**, so
the novel render path (and thus word-tap / markers / the JNI) is **not testable
on the emulator**.

- Pure units + mapper + DAO: emulator/JVM, normal green.
- Word-tap, marker drawing, JNI behaviour, jump: verified on a **real arm64
  Boox over USB** with a screenshot. Until that is shown, the feature is **not**
  "done".

## i18n (de + en)

Panel title, bookmark-mode toggle label, rename / delete, empty state,
`#{number}` formatting.

## Out of scope

- Cross-device / server sync of bookmarks.
- Bookmarks in the paged/webtoon/comic readers.
- Exact settings-screen location of the marker-style toggle (decided in plan).
- Highlighting ranges / annotations / notes (single word only).
