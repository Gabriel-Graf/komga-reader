# Novel-Reader settings/TOC sheet — wide-panel UI/UX overhaul

Pure UI/UX of the novel-reader bottom sheet (`NovelSettingsSheet`), tuned for a wide panel.
No engine/render changes except passing per-bookmark style+colour into the existing marker draw.

## Decisions (user, 2026-06-16)
- **Marker mode = per-bookmark** (point 8). Each bookmark stores its own `markerStyle`; the pinned
  selector sets only the **default for new** bookmarks (default **FLAG**); existing keep theirs.
  Multi-select can re-apply a mode to a selection.
- **Colour picker = palette + custom hex** (point 9). Fixed Kaleido-friendly swatches + a `#RRGGBB`
  field. Default **black**. Bookmark colour is *content* colour (drawn over the page), not the UI accent.
- **Multi-select = delete + apply colour/mode** (point 7).

## Data / domain
- `NovelBookmark`: add `markerStyle: String = FLAG`, `color: Int = 0xFF000000.toInt()`.
- `NovelBookmarkEntity`: add `@ColumnInfo(defaultValue="FLAG") markerStyle`, `@ColumnInfo(defaultValue="-16777216") color`.
- **Migration 19→20** (DB version 20): `ALTER ADD COLUMN markerStyle TEXT NOT NULL DEFAULT 'FLAG'`,
  `ALTER ADD COLUMN color INTEGER NOT NULL DEFAULT -16777216`, then
  `UPDATE novel_bookmark SET markerStyle = COALESCE((SELECT value FROM settings WHERE key='bookmark_marker_style'),'UNDERLINE')`
  so existing bookmarks keep their prior (global) look. @ColumnInfo defaultValue MUST match the ALTER
  default (`room-migration-destructive-pitfall`). Register in `DataModule`. Extend `NovelBookmarkMigrationTest`.
- `NovelBookmarkDao`: `setMarkerStyle(id,style)`, `setColor(id,color)`, `deleteMany(ids)`,
  `setMarkerStyleMany(ids,style)`, `setColorMany(ids,color)`; insert carries new fields.
- `NovelBookmarkRepository` (+Room impl): add the matching methods; `add`/`toDomain` carry new fields.
- `SettingsRepository.bookmarkMarkerStyle` default → **FLAG** (now "default for new bookmarks").
- `NovelSettings`: add an **ordered margin step list** (preset keys → crengine-listed px; landmark flag).
  Confirmed-listed px: 12,20,25,40,50. Steps: NARROW=12, M_SNUG=20, NORMAL=25, M_RELAXED=40, WIDE=50,
  XWIDE=>50 (verify a listed value >50: 60/80/100 — fall back if not listed). Named landmarks:
  NARROW/NORMAL/WIDE/XWIDE. `marginFor` maps all; legacy NARROW/NORMAL/WIDE unchanged. Unit test parity.

## ViewModel (`NovelReaderViewModel`)
- `onWordTap` Set: `markerStyle = markerStyle.value` (active default), `color = black`.
- New: `setDefaultMarkerStyle(style)`, `setBookmarkColor(id,color)`, `setBookmarkMarkerStyle(id,style)`,
  `deleteBookmarks(ids)`, `applyColorToBookmarks(ids,color)`, `applyMarkerStyleToBookmarks(ids,style)`.
- `BookmarkMarkers`: draw **per-bookmark** style+colour (bookmarks carry them); drop global
  `markerStyleName`; `drawFlag`/underline/margin take a `color: Color`.

## New shared components (`ui/components`, komga-ui)
- `EinkSliderRow(label, valueText, position, stepCount, onPosition, landmarks: List<SliderLandmark>)`
  — −/+ buttons, discrete notched track, labelled landmark ticks. No animation; mono via
  `LocalDesignTokens.accent` behind `allowsAccentColor`. `SliderLandmark(index, label)`.
- `EinkColorPicker(title, initial, onPick, onDismiss)` — `EinkModal` with swatch palette + `#RRGGBB`
  custom field. Palette constant of ~6-8 distinct colours (black default).

## Typography tab (`NovelTypographyControls`, `HyphenationPicker`)
1. Margin → `EinkSliderRow` over the margin steps; landmarks Narrow/Normal/Wide/X-Wide.
2. Font size / line height / weight → `EinkSliderRow` (−/+ + track), tighter spacing.
3. Alignment → `SegmentedChoiceRow` (Left | Justify) instead of two `ChoiceRow`s.
4. Hyphenation order → **Off, Auto, Language** (`HyphenationPicker`, shared w/ settings).

## Bookmarks tab (`NovelBookmarkPanel`)
- Pinned (non-scroll) under the tab: default-marker segmented selector + multi-select action bar
  (select-all · count · Colour · Marker-mode · Delete on selection).
- Scrolling list: tighter rows; per row [checkbox][#n][text→jump][colour swatch→pick][rename][delete].
- Callbacks: `onJump,onJumped,onRename(id),onDelete(id),onPickColor(ids),onApplyMode(ids,style),`
  `onDeleteMany(ids),defaultMarkerStyle,onDefaultMarkerStyle(style)`. Colour modal owned by the screen
  (one modal at a time).

## Sheet + screen
- `NovelSettingsSheet`: slimmer `SheetTabRow` (point 6); BOOKMARKS branch hosts `NovelBookmarkPanel`
  full-height (own pinned+scroll), TYPOGRAPHY keeps scroll. Thread new bookmark callbacks.
- `NovelReaderScreen`: per-bookmark marker draw; colour-picker state + modal; wire new VM callbacks.
- `SettingsContent` NovelScope: marker selector relabelled "default marker"; margin gains X-Wide.

## i18n (de+en): novelMarginXWide, novelBookmarkColor(+Title/Custom/Hint), novelBookmarkSelectAll,
novelBookmarkSelectedCount(n), novelBookmarkApplyColor/Mode, novelBookmarkDefaultMarker, novelBookmarkDeleteSelected.

## Verify
`:domain:test`, `:data` migration androidTest pattern, `:app:assembleDebug`. Word-tap render +
colour on real arm64 Boox is **device-bound** (crengine .so) — emulator covers build/compile only.
