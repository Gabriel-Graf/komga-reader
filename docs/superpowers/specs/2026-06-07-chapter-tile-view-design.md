# Design: Kapitel-Ansicht Liste ↔ Kachel-Gitter (global persistiert)

**Datum:** 2026-06-07
**Status:** Freigegeben (Brainstorming)
**Betrifft:** Serien-Detail-Ansicht (`SeriesDetailScreen`), Settings-Persistenz, i18n

## Problem & Ziel

Die Serien-Detail-Ansicht zeigt die Kapitel aktuell als reine Textliste
(`ChapterRow`) unter einem **kollabierbaren** Kapitel-Header. Zwei Defizite:

1. Das Ein-/Ausklappen der Kapitelliste ist überflüssig — es ergibt nie Sinn,
   die Kapitel zu verbergen.
2. Viele Kapitel haben ein eigenes Cover, das ungenutzt bleibt.

Ziel: ein **Umschalter Liste ⇄ Kachel-Gitter**. Im Kachel-Modus erscheint je
Kapitel das eigene Cover in einem adaptiven Gitter, mit Status-/Aktions-Icons als
**Ecken-Overlays**. Der gewählte Modus gilt **global** und wird wie die anderen
Settings persistiert.

## Nicht-Ziele (YAGNI)

- Kein Modus-Wechsel pro Serie (nur global).
- Keine Änderung am Listen-Modus (`ChapterRow` bleibt funktional unverändert).
- Keine neue Cover-Abstraktion in `domain` (siehe Naht-A-Hinweis).
- Keine Animation am Toggle/Grid (E-Ink, Ghosting).

## Architektur-Überblick

```
SettingsRepository (domain)  ── chapterViewMode: Flow<String>  ("LIST"|"GRID")
        ↓
RoomSettingsRepository (data) ── Key "chapter_view_mode", Default "LIST"
        ↓
SettingsViewModel (app)      ── StateFlow<String> + setChapterViewMode(mode)
        ↓
SeriesDetailScreen           ── liest Modus, rendert EIN LazyVerticalGrid:
                                 LIST → ChapterRow (full-span)
                                 GRID → ChapterTile (adaptive Zelle)
```

## Komponenten im Detail

### 1. Setting `chapterViewMode`

Exakt das bestehende `webtoonOverlapPercent`-Muster:

- **`SettingsRepository`** (domain): `val chapterViewMode: Flow<String>` +
  `suspend fun setChapterViewMode(mode: String)`.
- **`RoomSettingsRepository`** (data): Key-Konstante `KEY_CHAPTER_VIEW_MODE =
  "chapter_view_mode"`. Flow: `dao.observe(KEY).map { it ?: "LIST" }`. Setter:
  `dao.put(SettingEntity(KEY, mode))`. **Keine** Room-Schema-Migration nötig —
  die `settings`-Tabelle ist generisch (key/value).
- **`SettingsViewModel`** (app): `val chapterViewMode =
  settings.chapterViewMode.stateIn(viewModelScope, Eagerly, "LIST")` +
  `fun setChapterViewMode(mode: String)`.

Wert ist ein String (`"LIST"`/`"GRID"`), konsistent mit den anderen Settings
(themeMode, displayMode sind ebenfalls String-Enums). Default `"LIST"` =
heutiges Verhalten.

### 2. Header-Umbau — Einklappen raus, Toggle rein

`ChaptersSectionHeader`:

- **Entfernt:** `expanded`/`onToggle`-Parameter, das `clickable` auf der Zeile,
  die `ExpandLess`/`ExpandMore`-Icon-Logik.
- **Entfernt in `SeriesDetailContent`:** `chaptersExpanded`-State, die
  `AnimatedVisibility`-Wrapper um die Kapitelliste. Kapitel sind immer sichtbar.
- **Neu:** rechts in der Header-Zeile (wo das Expand-Icon saß) ein
  **Toggle-`IconButton`**: `Outlined.ViewList` wenn aktuell GRID (tippt → LIST),
  `Outlined.GridView` wenn aktuell LIST (tippt → GRID). 24dp, `onSurface`.
  contentDescription lokalisiert.

### 3. EIN LazyVerticalGrid statt LazyColumn

Wichtig: `LazyVerticalGrid` darf **nicht** in `LazyColumn` verschachtelt werden
(doppeltes Scrollen). Lösung: der gesamte Detail-Screen wird **ein**
`LazyVerticalGrid(GridCells.Adaptive(minSize = 150.dp))`:

- **Hero-Card** → `item(span = { GridItemSpan(maxLineSpan) })` (volle Breite).
- **Kapitel-Header** → `item(span = full)`.
- **LIST-Modus:** jede `ChapterRow` als `item(span = full)` + `HorizontalDivider`.
  Bestehende `ChapterRow` unverändert wiederverwendet.
- **GRID-Modus:** jedes `ChapterTile` als normale Zelle (`items(books)`),
  Abstand `EinkTokens.tileGap` (8dp), Ränder `screenPadding`.

`GridCells.Adaptive(150.dp)` ergibt auf 1264px @ ~1.875 Dichte (≈674dp Breite)
rund 4 Spalten und skaliert robust auf andere Geräte.

### 4. Neue Composable `ChapterTile`

Signatur analog `ChapterRow` (gleiche Callbacks/State):

```
ChapterTile(
    book: Book,
    coverUrl: String?,          // "${baseUrl}books/${remoteId}/thumbnail"
    isSelected: Boolean,
    showBookmark: Boolean,
    isLocal: Boolean,
    isDownloading: Boolean,
    onOpen, onDownload, onRemoveDownload, onSetRead(Boolean),
)
```

Aufbau:

- **`Column`**, ganze Kachel auf `surfaceVariant` wenn `isSelected` (sonst
  `surface`) — gleiche Selektions-Sprache wie `ChapterRow`.
- **Cover-`Box`**: `FilteredAsyncImage` (E-Ink-Farbfilter via `LocalImageFilter`),
  `aspectRatio(2f/3f)`, `ContentScale.Crop`, 1.5dp `outlineVariant`-Border,
  Radius `EinkTokens.tileRadius` (10dp). URL wie das bestehende Series-Cover
  gebaut (`SeriesHeroCard`-Muster).
  - **Ecken-Overlays — kontraststark auf jedem Kaleido-Cover:** kein nacktes Icon,
    sondern ein **opaker Chip** — `surface`-Fläche + 1.5dp `outlineVariant`-Border,
    Radius 6dp, **Outlined**-Icon ~18dp `onSurface`.
    - **oben-rechts** (`Alignment.TopEnd`): vertikal gestapelt — Lesezeichen
      (`Outlined.Bookmark`, wenn `showBookmark`) oben, Häkchen (`Outlined.Check`,
      wenn `readCompleted`) darunter. Nur was zutrifft.
    - **unten-rechts** (`Alignment.BottomEnd`): Aktions-Chip, 36dp Touch-Target —
      `isDownloading` → Spinner (`LoadingIndicator`); `isLocal` → `Outlined.Delete`
      (`onRemoveDownload`); sonst `Outlined.CloudDownload` (`onDownload`).
- **Titel-Zeile** unter dem Cover: Nummer fett + Titel, `bodySmall`, max. 2 Zeilen,
  `TextOverflow.Ellipsis`, `onSurface`. Bold wenn `isSelected`.
- **Gesten:** tap = `onOpen`; long-press = `EinkActionMenu` mit
  gelesen/ungelesen (identisch zu `ChapterRow`).

### 5. i18n (DE + EN, echte Umlaute)

Neue `Strings`-Keys:

- `chapterViewSwitchToGrid` — DE „Kachelansicht" / EN „Grid view"
  (contentDescription wenn aktuell Liste).
- `chapterViewSwitchToList` — DE „Listenansicht" / EN „List view"
  (contentDescription wenn aktuell Gitter).

Bestehende Keys (`download`, `removeDownload`, `resumeHere`, `statusRead`,
`markRead`, `markUnread`) werden in `ChapterTile` wiederverwendet.

## Naht-A-Hinweis (akzeptierte Konsistenz)

Die Cover-URL wird in der UI aus `serverConfig.baseUrl` gebaut
(`"${baseUrl}books/${remoteId}/thumbnail"`) — Komga-spezifisches URL-Schema.
Das **spiegelt 1:1** das bereits existierende Series-Cover-Muster in
`SeriesHeroCard`. Bewusste Entscheidung: Konsistenz mit dem vorhandenen Code
statt einer neuen Cover-Abstraktion in `domain`. Wird der per-Buch-Cover-Abruf
später quellen-agnostisch gebraucht, ist das ein eigener Refactor entlang Naht A
(`source-extensibility.md`, Kochrezept A) — nicht Teil dieses Specs.

## Testing

- **Unit (TDD, zuerst):** `RoomSettingsRepository` — `chapterViewMode` liefert
  Default `"LIST"` ohne gesetzten Wert; nach `setChapterViewMode("GRID")` liefert
  der Flow `"GRID"`. (Muster wie bestehende Settings-Tests.)
- **E2E:** Screenshot der Detail-Ansicht im Listen-Modus, Toggle, Screenshot im
  Kachel-Modus — gegen die lokale Test-Komga (Emulator `eink_test`,
  1264×1680@300). Verifiziert: Toggle schaltet, Cover + Ecken-Overlays sichtbar,
  Persistenz über Re-Open.

## E-Ink-Konformität (Checkliste)

- Tokens aus `EinkTokens`/`colorScheme`, keine Magic-Werte.
- **Outlined**-Icons durchgängig in der neuen Kachel.
- Rahmen ≥ 1.5dp `outlineVariant` (auf E-Ink sichtbar).
- Keine Animationen/Schatten/Verläufe; Toggle = sofortiger State-Wechsel.
- Teil-Update statt Voll-Reload: Toggle ist reiner Settings-Flow, kein
  `Loading`-Durchlauf; `SeriesDetailViewModel`-Override-Muster bleibt unangetastet.
```
