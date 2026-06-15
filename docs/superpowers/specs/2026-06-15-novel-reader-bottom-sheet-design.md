# Novel Reader Bottom-Sheet for Typography + TOC — Design

**Date:** 2026-06-15
**Status:** Design approved, ready for implementation plan

## Goal

Replace the novel reader's two centered modal panels — **Typography** (`NovelTypoPanel`) and
**TOC** (`NovelTocPanel`) — with a single **full-width, bottom-anchored sheet** that holds both as
two tabs. The sheet opens two ways: an **upward swipe from the bottom edge** (works even in
immersive mode) and an **expandable peek bar** shown at the bottom of the chrome overlay (the
no-swipe entry). It closes on blur (scrim tap), downward drag, or hardware back. Search and
Bookmarks stay exactly as they are (own top-bar icons + `EinkInfoDialog`).

## Why / context

Today the four novel panels (TOC, Typography, Search, Bookmarks) are mutually-exclusive
`EinkInfoDialog` modals (centered, `0.6f` width) toggled by five icons in the top chrome bar
(`NovelReaderScreen.kt:99-193`). The user wants the two **settings/navigation** panels reachable
by a bottom gesture and a bottom bar instead of a top-bar icon + centered modal.

## Decisions (locked)

- **Scope:** only Typography + TOC move into the sheet. Search + Bookmarks (+ bookmark-mode toggle)
  keep their top-bar icons and current modals.
- **Structure:** the sheet has two tabs `[Typografie | TOC]`. Swipe-from-bottom opens the
  **last-used** tab.
- **Top icons:** the TOC + Typography icons are **removed** from the top chrome bar (the bottom
  entry replaces them). Search + bookmark-mode + bookmark-list icons remain.
- **Seam placement:** the sheet lives **behind the `readerChrome` seam**. The host
  (`DefaultReaderScaffold`) owns the gesture/scrim/peek-bar/expand mechanics (exactly where the
  tap-zone layer and the frontlight edge strips already live); the reader provides only the sheet
  **content**. Other readers (paged/comic/webtoon/epub) pass `bottomSheet = null`. This keeps the
  E-Ink invariants host-enforced and makes the sheet part of the swappable chrome region.

## Architecture

### New capability surface (`:ui-api`)

Add to `com.komgareader.ui.slots` a small data class and a nullable field on
`ReaderScaffoldState`:

```kotlin
/**
 * Optional bottom-sheet capability of the reader chrome. The host owns the open mechanics
 * (bottom-edge swipe-up, peek bar in the chrome overlay, scrim, expand/collapse) and enforces
 * the E-Ink invariants (no motion on E-Ink); the reader supplies only [content] (arbitrary —
 * e.g. a tabbed panel). `null` on ReaderScaffoldState = no bottom sheet for this reader.
 */
data class ReaderBottomSheet(
    val expanded: Boolean,
    val onExpandedChange: (Boolean) -> Unit,
    val peekLabel: String,                 // shown on the collapsed bar (chrome-visible only)
    val content: @Composable () -> Unit,   // reader-provided body (tabs live here)
)
```

`ReaderScaffoldState` gains `val bottomSheet: ReaderBottomSheet? = null` (additive, default null →
no behavior change for existing readers). KDoc updated to mention the new optional capability.

`ReaderScaffold(...)` (host wrapper, `:app`) gains a `bottomSheet: ReaderBottomSheet? = null`
parameter threaded into the state. The four non-novel call sites are unaffected (default null).

### Host rendering (`:app`, `DefaultReaderScaffold` + new `ReaderBottomSheet.kt`)

Inside `DefaultReaderScaffold`'s root `Box`, when `state.bottomSheet != null`, render (in this
order, after `content()` and the tap-zone layer, coexisting with the frontlight strips):

1. **Bottom-edge swipe detector** — a full-width strip (~24dp tall) aligned `BottomCenter`,
   `detectVerticalDragGestures`; an upward drag (`dragAmount < 0`) calls `onExpandedChange(true)`
   and consumes only the vertical drag, so left/center/right page taps still pass (same coexistence
   pattern as the frontlight horizontal strips at `ReaderScaffold.kt:142-192`). Active regardless
   of `chromeVisible` (immersive entry).
2. **Peek bar** — only when `state.chromeVisible && !expanded`: a thin, flat, full-width bar at the
   bottom with a grabber glyph (▁▁▁) + `peekLabel`, tap **or** upward-drag → `onExpandedChange(true)`.
   This is the no-swipe entry. Lives above the persistent footer.
3. **Expanded sheet** — when `expanded`: a `readerOverlayScrim`-backed full-screen scrim
   (tap → `onExpandedChange(false)`) plus a `fillMaxWidth()`, `BottomCenter`-aligned flat container
   (1.5px outline border, no elevation, height = content, capped ~55% screen, vertical scroll).
   Renders `bottomSheet.content()`. A downward drag on the container → `onExpandedChange(false)`.

All motion gated by `LocalEinkMode`: E-Ink = instant appear/disappear (no slide); phone =
`slideInVertically/slideOutVertically` + scrim `fadeIn/Out`. The peek bar appears instantly on
E-Ink. New file `ReaderBottomSheet.kt` holds the host renderer composables
(`ReaderBottomSheetPeek`, `ReaderBottomSheetExpanded`) to keep `DefaultReaderScaffold` readable.

### Reader content (`:app`, novel)

New `NovelSettingsSheet.kt`: a stateless composable taking the current `ReflowConfig`, the
typography callbacks, the chapter list, `onChapterSelected`, fonts/files, and a
`selectedTab: NovelSheetTab` + `onTabChange`. Renders a flat two-segment tab row
(`[Typografie | TOC]`, E-Ink flat selectable, host-enforced look) and below it the active tab:

- **Typografie** → the existing shared `NovelTypographyControls(...)` (unchanged; same callbacks
  as `NovelTypoPanel` passes today).
- **TOC** → a new extracted `NovelTocList(chapters, onChapterSelected)` composable. The current
  TOC body (group/parent/child rows + `groupChapters`, `NovelTocPanel.kt:77-162`) is extracted from
  `NovelTocPanel` into `NovelTocList` so the sheet reuses it (`shared-structure-before-variants`).
  On select it jumps to the anchor and collapses the sheet.

`enum class NovelSheetTab { TYPOGRAPHY, TOC }` (novel-local; the host never sees tabs).

### Novel screen wiring (`NovelReaderScreen.kt`)

- Remove `typoPanelOpen` + `tocPanelOpen` flags and their `when` branches; remove the TOC +
  Typography `IconButton`s from `actions`. Keep `searchPanelOpen`/`bookmarkPanelOpen` + their
  branches + their icons + the bookmark-mode toggle unchanged.
- Add `var sheetExpanded by remember { mutableStateOf(false) }` and
  `var sheetTab by rememberSaveable { mutableStateOf(NovelSheetTab.TYPOGRAPHY) }` (last-used tab
  persists across opens within the session).
- Pass `bottomSheet = ReaderBottomSheet(expanded = sheetExpanded, onExpandedChange = { sheetExpanded = it },
  peekLabel = strings.novelSettings, content = { NovelSettingsSheet(...) })` to `ReaderScaffold`.
- `BackHandler(sheetExpanded) { sheetExpanded = false }` so hardware back closes the sheet first.
- Bookmark-mode interaction: the bottom swipe strip and the bookmark-mode word-tap both want the
  page area. The strip only consumes **vertical** drags and the word-tap is a **tap**, so they
  coexist; no special-casing needed (mirrors how the tap-zone layer and frontlight strips already
  coexist). The sheet stays available in bookmark mode.

### Files

- **Modify** `ui-api/.../slots/ReaderScaffoldState.kt` — add `ReaderBottomSheet` + `bottomSheet` field + KDoc.
- **Create** `app/.../ui/reader/ReaderBottomSheet.kt` — host peek + expanded renderers (E-Ink gated).
- **Modify** `app/.../ui/reader/ReaderScaffold.kt` — `bottomSheet` param; render block in `DefaultReaderScaffold`.
- **Create** `app/.../ui/reader/NovelSettingsSheet.kt` — 2-tab content + `NovelSheetTab`.
- **Modify** `app/.../ui/reader/NovelTocPanel.kt` — extract `NovelTocList` (+ make the row composables reachable); delete the `NovelTocPanel` modal (no longer used).
- **Delete** `app/.../ui/reader/NovelTypoPanel.kt` — replaced by the sheet's Typography tab (its body was just `EinkInfoDialog` + `NovelTypographyControls`, which the sheet uses directly).
- **Modify** `app/.../ui/reader/NovelReaderScreen.kt` — flags/actions/wiring per above.
- **Add** i18n key `novelSettings` (peek-bar label) — DE „Einstellungen" / EN "Settings". Add it in all four places: the `Strings` interface, `GermanStrings`, `EnglishStrings`, **and** `MapBackedStrings` (get-with-fallback, like the other novel keys). Reuse existing `novelTypography`/`novelToc` for the tab labels.
- **Create** `app/src/debug/.../ui/reader/ReaderBottomSheetPreview.kt` — swap/preview proof (collapsed peek + expanded), debug-only.

## E-Ink design compliance

- Flat, 1.5px `outline` border, no elevation/shadow, no ripple. Scrim opaque on E-Ink, translucent
  on phone (`readerOverlayScrim`).
- Every gesture-triggered reveal has an instant E-Ink path (`animation-gating.md`): the swipe/peek
  **input** is fine, the resulting open/close is instant on E-Ink, animated only on phone.
- Tab labels + peek label via i18n (DE+EN). No accent color (mono).

## Testing

- **Pure/unit:** `groupChapters` is already unit-testable and stays pure (regression-safe after the
  `NovelTocList` extraction — keep/extend `groupChapters` tests). The tab-state default + last-used
  behavior is trivial Compose state (no new pure function worth a unit test; covered by preview/E2E).
- **Slot/preview:** `ReaderBottomSheetPreview.kt` proves the host renders both states (collapsed
  peek bar, expanded full-width sheet) — same debug-preview discipline as the other slot regions.
- **Emulator E2E:** open via peek-bar tap and via bottom swipe; switch tabs; change a typography
  value and see live re-layout; jump to a TOC chapter; close via scrim tap / back / down-drag;
  verify full width and E-Ink instant (no slide) vs phone slide. Verify search/bookmark modals
  unaffected and that page taps still work while the swipe strip is present.
- **Boox:** the real bottom-edge swipe + E-Ink instant behavior is device-bound (note as Soll if
  only emulator-verified).

## Docs to update (same commit as code, per `docs-match-code`)

- `.claude/rules/architecture-seams.md` — under the `readerChrome` region: add the optional
  `bottomSheet` capability (host-owned mechanics, reader-provided content, novel-only consumer).
- `.claude/rules/big-picture-and-goals.md` — ui-modularity: note the readerChrome region grew the
  optional bottom-sheet capability.
- Memory + roadmap note as appropriate.

## Out of scope (YAGNI)

- No multi-detent sheet (just collapsed bar ↔ expanded). No moving Search/Bookmarks into the sheet.
- No generic per-reader bottom-sheet for paged/comic/webtoon (they pass null; revisit only if a
  second reader needs one). No external pack/ABI freeze for the new field (the readerChrome contract
  is not frozen yet anyway).
