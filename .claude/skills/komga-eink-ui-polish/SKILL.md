---
name: komga-eink-ui-polish
description: Use at the START of any UI/visual task in the Komga-Reader app (Jetpack Compose, Onyx Boox Go Color 7 Gen2). Entry point + index for the Komga UI — the device-native "Onyx-look" design language, the three device classes (mono E-Ink / colour E-Ink Kaleido / LCD) on two orthogonal axes (motion ⟂ accent colour), component catalogue, tokens, Lucide/AppIcons, settings architecture, anti-patterns. Routes to the domain skills (viewer-type, guided-comic, colour-filter), to frontend-design for LCD craft, and to android-development / chrisbanes-skills for Kotlin/Compose design patterns and code conventions (E-Ink invariants override them). Building or refactoring a screen, component, dialog, theme, indicator, list, reader chrome — start here.
---

# Komga UI — Design-Philosophie & Index

Einstiegspunkt für **jede** UI-Arbeit in `app/` dieses Projekts. Er trägt die Design-Sprache
(„Onyx-Boox-Look") **und** die Geräteklassen-Disziplin, und verweist auf die fokussierten
Domain-Skills. Lade ihn zuerst, dann den passenden Sub-Skill.

> Design-Sprache extrahiert aus der **Stock-Firmware der Onyx Boox Go Color 7 Gen2**. Ziel: die App
> fühlt sich **nativ zum Gerät** an — flach, maximaler Kontrast, Tiefe über Rahmen statt Schatten,
> keine Bewegung auf E-Ink. Onyx-Icon-Assets sind proprietär; wir treffen ihre Form-Sprache mit
> **Lucide-Icons** (E-Ink-getunt auf 2.5px Stroke), kopieren keine Assets.

> **Begleit-Skill für LCD-Craft:** Für die kreative Umsetzung auf **Nicht-E-Ink** (LCD-Phone/-Tablet,
> wo Bewegung + Farbe erlaubt sind) zusätzlich `frontend-design` laden. Dieser Skill liefert **Regeln
> & Bausteine + die E-Ink-Invarianten**; `frontend-design` die **Design-Qualität** für den
> Bewegungs-/Farb-Pfad. Sie widersprechen sich nicht — die E-Ink-Designsprache ist auf mono/Farb-E-Ink
> Pflicht, auf LCD bewusst **nicht** das Ziel.

## Die Wirbelsäule: drei Geräteklassen, zwei orthogonale Achsen

Das Zielgerät ist **nicht binär.** „E-Ink vs. Smartphone" wirft zwei *unabhängige* Achsen zusammen,
die getrennt gehören (siehe `big-picture-and-goals.md` → „Geräteklassen sind nicht binär"):

| Klasse | `allowsMotion` | `allowsAccentColor` | Konsequenz fürs UI |
|---|---|---|---|
| mono E-Ink | **false** | **false** | keine Bewegung, monochrom (schwarz/weiß-Akzent) |
| Farb-E-Ink (Kaleido) | **false** | **false** | keine Bewegung, **UI-Akzent Schwarz** (Cover-Farbe via Color-Filter, separat) |
| LCD-Phone/-Tablet | **true** | **true** | Bewegung + Farbe erlaubt (hier glänzt `frontend-design`) |

> **User-Entscheidung (auf echter Go Color 7 verifiziert):** der E-Ink-Modus ist beim UI-Akzent
> **monochrom — auch auf Kaleido Schwarz** (`DisplayMode.EINK` setzt `allowsAccentColor = false`,
> unabhängig von `canColor`). Farbe der **Cover/Seiten** regelt der Color-Filter, nicht der Akzent.
> Das Modell behält beide Achsen (kein binäres `isEink`); nur der E-Ink-Modus wählt Schwarz.

**Quelle der Wahrheit (Ist, verdrahtet):** `DisplayBehavior(allowsMotion, allowsAccentColor)`
(`domain/model/DisplayBehavior.kt`), abgeleitet via `displayBehaviorFor(mode, capabilities)` in
`MainActivity`, in die UI gereicht als **`LocalDisplayBehavior.current`**
(`app/ui/components/LoadingIndicator.kt`). `LocalEinkMode` ist die **abgeleitete dünne Brücke**
`!allowsMotion` — bleibt für bestehende Animations-Gates, ist aber **nicht** die Stelle für *neues*
modus-sensitives Verhalten.

**Die Pro-Element-Regel:** Jedes UI-Stück beantwortet **beide** Achsen getrennt:
- **Bewegung?** → `LocalDisplayBehavior.current.allowsMotion` (bzw. `LocalEinkMode` = `!allowsMotion`
  für bestehende Gates). Siehe `animation-gating.md` — jede Animation hat einen bewegungsfreien Pfad.
- **Farbe/Akzent?** → `LocalDisplayBehavior.current.allowsAccentColor`. Akzentfarbe **nur** hinter
  diesem Gate, **nie** unkonditional, **nie** an `allowsMotion`/`LocalEinkMode` koppeln (das ist die
  *Bewegungs*-Achse). Heute setzt der **E-Ink-Modus `allowsAccentColor = false`** → UI-Akzent Schwarz
  (auch Kaleido); Akzentfarbe nur im Smartphone-Modus.

> **Soll/Ist-Ehrlichkeit (docs-match-code):** Die Achse `allowsAccentColor` ist verdrahtet, **aber**
> `Theme.kt` ist heute monochrom (`primary = Black`/`White`) — es gibt **noch kein Akzent-Farbtoken**
> und damit **keinen** Consumer von `allowsAccentColor`. In der Praxis rendert daher heute jede Klasse
> monochrom. Wer das **erste** farbsensitive Element baut: ein **zentrales gedämpftes Akzent-Token** in
> `Theme.kt` anlegen, **ausschließlich** hinter `allowsAccentColor` lesen, auf echter Kaleido-HW
> verifizieren („sieht man am LCD" reicht nicht — Kaleido dämpft die Sättigung) und diesen Absatz im
> selben Commit nachziehen.

## Index — was wohin gehört

| Du baust / brauchst… | → Skill / Regel |
|---|---|
| Reader-/Viewer-**Modus** wählen (PAGED/WEBTOON/COMIC/NOVEL), `readingDirection` | **`komga-viewer-type-resolution`** |
| Geführten Comic-Reader (Panel-Zoom, PanelDetector, Tap-Zonen) | **`komga-guided-comic-reader`** |
| Farbfilter für Cover/Seiten (Kaleido-Sättigung/Kontrast, `ColorProfile`) | **`komga-eink-color-filter`** |
| LCD-Craft (Bewegung + Farbe erlaubt) — kreative Qualität | **`frontend-design`** |
| Kotlin/Compose **Design-Patterns** (Slot-API, State-Hoisting, State-Holder/UI-Split, Modifier-Konvention, Flow/Event-Modeling, Recomposition-Perf, Stability) | **`chrisbanes-skills:*`** — *E-Ink-Invarianten schlagen sie* (s. u.) |
| Android-**Architektur & Namens-/Datei-Konventionen** (Route/Screen-Split, `UiState` sealed interface, Repo-Pattern, Offline-first) | **`android-development`** — *E-Ink-Invarianten schlagen sie* (s. u.) |
| Animation gaten (jede Bewegung braucht E-Ink-Pfad) | Regel `animation-gating.md` |
| Verbindliche Kurzfassung der Designsprache (Spec) | Regel `eink-design-language.md` |
| Warum nicht binär (Geräteklassen, Ziele, Nogos) | Regel `big-picture-and-goals.md` |
| Chrome/UI-Element austauschbar bauen (modulare UI, Community-Looks) | Regel `big-picture-and-goals.md` → „Modulare UI"/`ui-modularity` |

Die Geräteklassen-Disziplin (oben) gilt in **allen** Sub-Skills. **Ziel modulare UI:** jedes
Chrome-Element (Overlay/Header/Buttons/Navigation/Tiles/Settings) gehört hinter eine adressierbare,
austauschbare Grenze — nie hart in den Compose-Baum verdrahtet (sonst kann die Community nichts
ersetzen ohne Fork). Beim Bauen mitdenken.

## Android-/Compose-Handwerk: Quell-Skills — E-Ink schlägt sie

Zwei externe Skill-Familien tragen allgemeines Kotlin/Compose-Handwerk. Lade sie für **Design-Patterns
und Code-/Namens-Konventionen** — **nicht** für Look oder Verhalten. Look/Bewegung/Farbe/Icons/Dialoge
diktiert **ausschließlich** dieser Skill + die Projekt-Regeln.

- **`android-development`** — Architektur- & Datei-/Namens-Konventionen (Route/Screen-Split,
  `UiState` als sealed interface, `onAction`/explizite Callbacks, Repo-Interface + `OfflineFirst`-Impl,
  `toModel`/`toEntity`-Mapper, `stateIn(WhileSubscribed(5_000))`). Es ist **NowInAndroid/Material-3-orientiert** —
  Struktur übernehmen, **Material-Look nie**.
- **`chrisbanes-skills:*`** — die kanonische Referenz für **einzelne** Compose-Patterns. Lade den
  passenden Sub-Skill gezielt: `compose-slot-api-pattern`, `compose-state-hoisting`,
  `compose-state-holder-ui-split`, `compose-modifier-and-layout-style`, `kotlin-flow-state-event-modeling`,
  `compose-state-authoring`, `compose-recomposition-performance`, `compose-stability-diagnostics`,
  `kotlin-types-value-class`, `compose-side-effects`, `compose-animations`.

> **REQUIRED — die E-Ink-/Projekt-Invarianten überschreiben jeden Rat dieser Skills.** Diese Skills kennen
> weder E-Ink noch die zwei Nähte noch die Geräteklassen-Disziplin. Wo ihr Rat mit etwas in **diesem**
> Skill oder den `.claude/rules/*` kollidiert, **gewinnt die E-Ink/Projekt-Seite — immer.** Den Buchstaben
> eines externen Patterns zu befolgen und dabei eine Invariante zu brechen, ist ein Bug, kein „best practice".

**Kollisionen, bei denen die E-Ink/Projekt-Seite gewinnt (Schließe-die-Lücke):**

| Externer Rat | Hier gilt stattdessen |
|---|---|
| `compose-animations` zeigt `AnimatedVisibility`/`animate*AsState` als Standard | **Jede** Bewegung über `allowsMotion`/`LocalEinkMode` gegatet, bewegungsfreier Pfad Pflicht (`animation-gating.md`). Default = **keine** Bewegung. |
| Material-3-Komponenten (`AlertDialog`, `Checkbox`, `Button`, `OutlinedTextField`, `ListItem`) als Bausteine | **Nie roh.** Erst greppen, geteilte `Eink*`-Komponente nutzen (Ersatztabelle unten). Dialog = `EinkModal`. |
| Material-Icons / `Icons.*` in Beispielen | **Nur** `AppIcons.<Zweck>` (Lucide). Material-Icons sind aus den Deps raus. |
| `dynamicColor`/Material-`primary`-Akzent, farbige Indikatoren | UI-Akzent nur hinter `allowsAccentColor`; E-Ink-Modus (mono **+** Kaleido) = Schwarz. Nie an `allowsMotion` koppeln. |
| `isSystemInDarkTheme()`/Geräte-Boolean als Verhaltens-Gate | Verhalten aus `LocalDisplayBehavior` (zwei Achsen), **nie** ein binäres `isEink`/Boolean (Nogo, `big-picture-and-goals.md`). |
| Material-Default-`FontWeight.Normal`, disabled-Grau | Zentral `EinkTypography` (Body 500 / Labels 600); disabled-Text explizit `onSurface` (E-Ink-Lesbarkeit). |
| Multi-Modul-Schnitt `feature:api/impl`, `core:*` aus NowInAndroid | Komga hat **seinen eigenen** Modulschnitt (`domain`/`source-api`/`ui-api`/`app`/…) + die zwei Nähte. **Nicht** auf NiA umbauen — `architecture-seams.md` ist maßgeblich. |

**Konventionen, die wir übernehmen (Design-Pattern + Sprach-/Namens-Konvention):**

| Konvention (Quelle) | Regel für neue Komponenten/VMs hier |
|---|---|
| **Slot-API statt Primitiv-Flut** (`compose-slot-api-pattern`) | Variiert Inhalt pro Aufrufer → `@Composable () -> Unit`-Slots, **keine** `title: String, …icon: ImageVector?, showX: Boolean`-Anhäufung. Deckt sich mit der Slot-Naht (`UiSlotPack`/Capability-Surfaces) — auf **neue** Komponenten anwenden. |
| **State-Holder/UI-Split** (`compose-state-holder-ui-split`) | `XRoute(viewModel)` sammelt State/Effekte + verdrahtet → `XScreen(state, onAbc, modifier)` ist pur (nur State + Callbacks), previewbar. Entspricht den `*Route.kt`/`Viewer`-Mustern — so weiterführen. |
| **`modifier`-Parameter-Konvention** (`compose-modifier-and-layout-style`) | Layout-Composable bekommt `modifier: Modifier = Modifier`, **exakt so benannt**, **nach** Pflicht-Params, **vor** Content-Lambdas, auf den Root angewandt. Kein hartes `.fillMaxWidth()` auf dem Root. Modifier-Kette als **ein** fluenter Ausdruck. |
| **UiState als sealed interface** (`android-development`) | `sealed interface XUiState { data object Loading; data class Success(...); data class Error(...) }` statt loser Bools/Nullables. |
| **Flow/Event-Modeling** (`kotlin-flow-state-event-modeling`) | State: `StateFlow` via `stateIn(WhileSubscribed(5_000))`, **keine** Sentinel-Werte (Phasing). One-shot-Events (Navigation/Snackbar): `Channel(BUFFERED).receiveAsFlow()`, **nicht** `SharedFlow`. |
| **collectAsStateWithLifecycle** (`compose-state-holder-ui-split`) | In `*Route`-Composables `collectAsStateWithLifecycle()` statt `collectAsState()`. |
| **Stability/Value-Class** (`kotlin-types-value-class`, `compose-stability-diagnostics`) | Bei Recompose-Hotspots: `@JvmInline value class` für ID-Wrapper, stabile Parameter-Typen prüfen (Compiler-Report) — **bevor** man eine Bewegung „optimiert" (auf E-Ink gibt es eh keine). |

Namens-Konvention bleibt projektweit: Komponenten `Eink*`/semantischer Zweck, Icons `AppIcons.<Zweck>`,
View*-Split `*Route`/`*Screen`, Sub-Skill-Patterns **innerhalb** dieser Grenzen — nie gegen sie.

## Token-Quelle (Single Source of Truth)

| Token | Wert | Verwendung |
|-------|------|-----------|
| `outline` (colorScheme) | Schwarz / invertiert Weiß | **starke** Rahmen: Modals, aktive Elemente |
| `outlineVariant` (colorScheme) | `#777777` / `#8A8A8A` | **Hairline** für Tiles/Cards/Divider (mittelgrau — auf E-Ink sichtbar) |
| Radius small/medium/large | 6 / 8 / 12 dp | Standard Compose `Shapes` |
| `EinkTokens.tileRadius` | 10 dp | Settings-Tiles, Quick-Action-Kacheln |
| `EinkTokens.hairline` | 1.5 dp | Card-/Tile-/Row-Rahmen (dünner wird auf E-Ink unsichtbar) |
| `EinkTokens.strongBorder` | 2 dp | Modal-Rand (immer schwarz) |
| `EinkTokens.screenPadding` | 16 dp | Screen-Rand |
| `EinkTokens.sectionGap` | 16 dp | zwischen Sektionen |
| `EinkTokens.tileGap` | 8 dp | im Tile-Grid |
| Icon-Größe | 24 dp Standard, 28 dp Bottom-Nav, 20 dp inline | |

Hairline (`outlineVariant`) für ruhige Flächen (Tiles, Listenzeilen), **schwarzer** `outline` nur für
Betonung und **immer** für Modals. **Akzent-/Farbtoken: siehe Soll/Ist-Hinweis oben** — heute keins.

## Baustein-Katalog (`ui/components/`)

> **Erst suchen, nie roh bauen (verbindlich).** Bevor du ein Chrome-Stück baust (Suchfeld, TopBar,
> Tile, Listenzeile, Dialog, Toggle, Auswahl), **grep zuerst** nach einer bestehenden `Eink*`-/
> geteilten Komponente und nutze **dieselbe** wie der Rest der App — nie ein rohes Material-Control,
> nie ein Eigenbau neben einem existierenden Muster. Fehlt eine, ergänze sie **in `ui/components/`**
> (nicht lokal duplizieren). Real passiert: rohes Suchfeld/eigene TopBar gebaut, obwohl `EinkSearchBar`
> + die `HomeScreen`-`TopAppBar` existierten → „sieht anders aus als der Rest der App".

| Statt (roh/Material) | nutze (geteilt) |
|---|---|
| `OutlinedTextField` als **Suche** | **`EinkSearchBar`** |
| `Checkbox` | `ChoiceRow` (Häkchen) |
| `FilterChip` / `RadioButton` | `SegmentedChoiceRow` bzw. `ChoiceRow` |
| `Button` | `EinkOutlinedButton` |
| eigene TopBar / `SubPageScaffold` in einem Tab | **dieselbe** `TopAppBar` wie `HomeScreen` (Box: Back · Titel/Suche · Aktionen) |
| `AlertDialog` | `EinkModal` (Titel optional — leer = ohne Kopf, kompakt) |
| Status-/Kontext-Dropdown | `AnchoredMenuPopup` (Outside-Tap schließt) |
| Cover-Kachel / Collage / Listenzeile | `CollageTile` · `CompositeCover` · `EntityListRow` · `TileTitleBand` |

### Bottom-Menubar — `EinkBottomBar`
Haupt-Navigation. Flach, gleich breite Items, **Icon über Label**, Outlined-Icon (28 dp) + ~11 sp Label.
Aktives Item: **kurzer Akzent-Balken (3 dp) über dem Icon** + Label fett. Reihenfolge fix: primärer Tab
**links**, Einstellungen **rechts**. Aktueller Satz: `Bibliothek · Gruppen · Plugins(bald) · Einstellungen`.
> **Akzent-Balken-Farbe:** liest `LocalDesignTokens.current.accent` (hinter `allowsAccentColor`) —
> **E-Ink-Modus (mono + Kaleido) → Schwarz**, LCD/Smartphone → Akzent (Indigo). Die **Mechanik**
> (Balken, kein Material-Indikator, keine Animation) bleibt über alle Klassen gleich — nur die Farbe verzweigt.

### Top-Action-Leiste
Nur Icons (kein Text) rechts in der `TopAppBar`, Outlined, 24 dp. Nur **funktionierende** Aktionen —
keine Dead-Buttons. Links optional View-Toggle-Cluster.

### Tile (Kachel) — `SettingsTile` / `Tile`
Rechteck mit **Hairline-Rahmen** (`outlineVariant`), Radius 10 dp, Padding 14–16 dp. **Icon links** (24 dp)
· Titel (titleSmall) + Summary (bodySmall, `onSurfaceVariant`) · Chevron-rechts bei Drill-in. Zwei-Spalten-
Grid auf dem Landing (`GridCells.Fixed(2)`, `tileGap`). Summary zeigt den **aktuellen Wert**.

### Listenzeile / Auswahl — `ChoiceRow`
Volle Breite, `selectable`, Label links + **Häkchen rechts wenn gewählt** (ruhiger auf E-Ink als
RadioButton). Hairline-Divider zwischen Zeilen.

### Sub-Page-Gerüst — `SubPageScaffold`
`Scaffold` + `TopAppBar` mit Titel + Zurück-Pfeil, scrollender Body mit `screenPadding`.

### Modal — `EinkModal`
**Immer schwarzer Rand** (`strongBorder`, `outline`), weiße Surface, Radius `large`. `Dialog` + bordered
`Surface`, **nicht** das nackte `AlertDialog`. Sticky-Titel oben, Aktionen unten (Bestätigen rechts,
Abbrechen links). Genau **ein** Modal gleichzeitig.

### Empty-State
Zentrierte Line-Art/Icon + Label + darunter gestapelte **full-width Outlined-Buttons** für Primäraktionen.

## Icon-System: Lucide via zentrale `AppIcons`-Registry (Pflicht)

**Keine Material-Icons** (`material-icons-extended` ist aus den Gradle-Deps entfernt). Stattdessen
**Lucide-Glyphen** als `ImageVector` aus den Lucide-MIT-SVGs **generiert** mit E-Ink-tauglichem
**Stroke 2.5px** (statt Lucides 2px-Default).

**Drei Schichten:**
1. `tools/icons/` — Node-Generator (`icon-set.mjs` listet Glyphen, `lib/svg-to-pathdata.mjs` pure +
   unit-getestet, `generate.mjs` emittiert Kotlin).
2. `app/ui/icons/LucideIcons.kt` — **generiert**, NICHT von Hand editieren. Hält `ImageVector`s + die
   zentrale **`STROKE`-Konstante** (Stroke tunbar **ohne** Neu-Generierung).
3. `app/ui/icons/AppIcons.kt` — **SSOT**. Semantische Namen (Zweck, nicht Glyph) → Lucide-Glyph.
   **Die UI nutzt ausschließlich `AppIcons.*`.**

**Regel:** In `app/` **nie** `androidx.compose.material.icons.*` und **nie** `LucideIcons.*` direkt —
immer `AppIcons.<Zweck>`. Eine Variante pro Zweck (Outline), kein **stilistischer** Filled/Outlined-Mix.
**Ausnahme:** ein **gefülltes** Glyph als **Aktiv-Zustand** desselben Outline-Glyphs ist erlaubt/erwünscht
(z. B. Lesezeichen gesetzt) — via `lucideFilled(...)` in `LucideIcons.kt` + eigener `AppIcons`-Eintrag
(`BookmarkFilled`); `Icon(...)` tönt es wie jedes andere.
(`androidx.compose.material3.Icon` — das Composable — bleibt.)

**Neues Icon:** Glyph auf [lucide.dev/icons](https://lucide.dev/icons) → `icon-set.mjs` ergänzen →
`cd tools/icons && npm run generate` → `AppIcons.kt` semantischen Eintrag setzen → Aufrufstelle nutzt
`AppIcons.<Zweck>`. **Stroke anpassen:** nur `STROKE` in `LucideIcons.kt`. Auf echter Boox/`eink_test`
verifizieren (zu dünn = blass auf E-Ink).

### Animierte Icons: `AnimatedAppIcon` (LCD) — aber E-Ink dreht NICHT

`AnimatedAppIcon(imageVector, animation, running, …)` (`app/ui/components/AnimatedAppIcon.kt`) ist die
**eine** Heimat für bewegte Icons — Bewegung wird hier zentral über `LocalEinkMode` entschieden, nie am
Aufrufer. `IconAnimation` = additives Vokabular (`SpinClockwise`, `BobVertical`); neue Animation = neue
Variante + Branch in `iconAnimationPlan`. **Nie** ein Icon mit rohem `rotate`/`animate*` direkt bewegen.
Konkretisiert `animation-gating.md`.

**HW-Befund (2026-06-16, Go Color 7 Gen2): glatte Rotation/Bob rendert auf E-Ink NICHT sichtbar.** Eine
`graphicsLayer`-Rotation über 400 ms erzeugt Zwischenframes, die das Panel im normalen GC-Refresh nicht
durchschiebt → das Icon bleibt schlicht statisch. Das deckt sich mit der `LoadingIndicator`-Entscheidung
(statischer „Lädt…"-Text statt Spinner). **Sub-Pixel-Bewegung ist auf E-Ink kein gültiges Feedback** —
nur ein **diskreter Zustandswechsel** (anderer Glyph / anderer Text) refresht zuverlässig. `AnimatedAppIcon`
ist damit effektiv **nur der LCD-Pfad**; auf E-Ink nichts erwarten.

**Sync-/Reload-/Refresh-Buttons: `SyncIconButton`** (`app/ui/components/SyncIconButton.kt`) — die geteilte
Heimat **aller** Sync-Buttons (`shared-structure-before-variants`). **LCD:** `AnimatedAppIcon(SpinClockwise)`
dreht während `syncing`. **E-Ink:** der Glyph **wechselt diskret** auf eine Sanduhr (`AppIcons.Busy` →
Lucide `hourglass`) solange `syncing`, zurück auf `Refresh` wenn fertig — ein State-Wechsel, refresht.
Jeder Refresh-Button nutzt das statt `IconButton { Icon(AppIcons.Refresh) }`.

**`syncing`-Flag mit Mindestdauer halten:** `holdSpinning()` (`app/ui/common/SyncSpin.kt`, Default 700 ms)
umschließt die Sync-Arbeit im VM (`CollectionsViewModel.syncing`, `PluginsViewModel.reloading`,
`LibraryViewModel.refreshing`, `GroupBrowseViewModel.refreshing` — **eigener Latch, NICHT** aus einem
`Loading`-State abgeleitet). Ohne den Boden flippt ein Sofort-Sync (offline, kein Server, lokale DB)
`true→false` in <1 Frame; `StateFlow` **konflatiert** das, der Collector sieht `true` nie, der Busy-Zustand
erscheint nicht. Der Boden garantiert, dass der diskrete Busy-Glyph sichtbar wird.

## Settings-Architektur

**Adaptives Master-Detail.** Eine Section-Registry (`SettingsSection`: id, Icon, Titel, `searchTerms`,
`content(query)` in `SettingsSections.kt`) treibt drei Layouts über `BoxWithConstraints`
(`SettingsScreen.kt`) — **keine** NavHost-Seiten pro Sektion:

- **< 600 dp (Phone):** Accordion — Sektionskopf tappbar, Inhalt klappt inline auf
  (`AnimatedVisibility`, **nur** über `allowsMotion`/`LocalEinkMode` gegatetes Motion).
- **600–900 dp (Tablet):** Sidebar links (Icon 28 dp + `titleMedium`, aktiv = Akzent-Balken links + fett)
  + scrollendes Detail rechts.
- **≥ 900 dp (großes E-Ink):** wie Tablet, größer (Icon 32 dp, mehr Padding).

Sektionen (fix): **Verbindung** · **Darstellung** (Theme + **Anzeige-Modus**) · **Reader** · **Downloads**
· **Sprache** · **Über**. Inhalte sind scaffold-freie Content-Composables (`SettingsContent.kt`).

**Such-Highlight:** TopBar-Suche reicht `query` live an `SettingsScreen`. Reine Funktionen
`matchRanges`/`sectionMatches` (`SettingsSearch.kt`, unit-getestet) filtern + springen zur ersten Sektion;
`HighlightText` markiert **fett + `outlineVariant`-Hintergrund** (monochrom).

## Muster: lange Beschreibung neben dem Cover (Truncate + „Mehr lesen")

Beschreibung **neben** dem Cover, füllt bis zur **Cover-Unterkante** (rechte Spalte
`height(HERO_COVER_HEIGHT)`, Text mit `weight(1f)`). Passt der Text nicht: mit **„…"** kürzen
(`TextOverflow.Ellipsis`, `maxLines` aus Box-Höhe via `BoxWithConstraints`, Überlauf über
`onTextLayout { hasVisualOverflow }`), **„Mehr lesen"**-Zeile, voller Text im Readonly-Modal
(`EinkInfoDialog`: Titel + **nur X**, kein Footer, scrollbar). Hero-Karte behält **immer** ihre Form.
Fehlt Beschreibung → Platzhalter (`noDescription`, gedämpft). Referenz: `TruncatedDescription` /
`DescriptionModal` in `SeriesDetailScreen.kt`.

## E-Ink-Layout-Stabilität (kein Reflow beim Öffnen/Laden)

Auf E-Ink ist **jeder Layout-Sprung ein sichtbarer Full-Refresh/Flash**. Modal/Liste **öffnet in
Endgröße** und wächst nicht nach:
- Cover-/Inhalts-Gitter im Modal: **feste Höhe** (`Modifier.height(…)`) — nicht `heightIn`/
  `wrapContent`, das mit dem Inhalt wächst.
- Kacheln: **fester Rahmen-Platzhalter** (`aspectRatio` + Border) **vor** dem Bild; im Picker
  **ohne Titel** (uniforme Frames). `crossfade(false)`.
- Kein „Loading → Content"-Tausch, der die Höhe ändert (siehe Voll-Reload-Anti-Pattern).

Real beobachtet: das „Werke hinzufügen"-Modal wuchs beim Öffnen (Liste/Cover luden nach) →
sichtbare Bewegung/Flash. Fix: feste Gitterhöhe + Rahmen-Platzhalter.

## Weitere Muster (aus der Praxis)

- **Ein Auswahl-Signal:** ALLE Selektions-Indikatoren (Häkchen, Nav-/Sidebar-Balken, Segment,
  +/✓-Overlay) lesen **denselben** `LocalDesignTokens.current.accent`. Nie eines schwarz, das andere
  Akzent (real: `ChoiceRow`-Häkchen war `onSurface`, während Balken/Segmente Akzent trugen).
- **Such-als-Overlay:** Lupe ersetzt den Titel durch `EinkSearchBar`; ein **immer sichtbares X**
  (Action-Icon = `AppIcons.Close`, `onSubmit` = schließen) klappt zurück zum Titel; Blur **mit
  leerem Feld** schließt — Guard: erst nach **erstem** Fokus schließen (sonst schließt es sofort beim
  Öffnen); `FocusRequester` für Auto-Fokus.
- **Chrome-Ownership / TopBar-DRY:** Eine Sub-View, die in einem Tab lebt, **unterdrückt die
  Host-TopBar** und liefert **dieselbe** `TopAppBar` selbst (Titel zentriert via `textAlign = Center`).
  Nie zwei Bars übereinander.
- **Staged-Auswahl → eine Transaktion:** Mehrfachauswahl beim Bestätigen in **einer** Coroutine
  anwenden (`forEach { repo… }`), nie pro Item ein `viewModelScope.launch` — ein read-modify-write-
  State (z. B. `addMember`) überschriebe sich sonst (nur das letzte bliebe markiert-aber-gespeichert).
- **i18n-Reichweite:** auch **Snackbars/Events/Error-States** und inline-Strings (`"Quelle $id"`)
  über `Strings`-Keys — nicht nur sichtbare Labels. Hartkodiertes Deutsch dort ist ein häufiges Leck.

## Checkliste pro UI-Stück

1. **Beide Achsen beantwortet?** Bewegung über `allowsMotion`/`LocalEinkMode`, Farbe über
   `allowsAccentColor` — getrennt, nie gekoppelt. Funktioniert es auf mono E-Ink **und** Kaleido **und** LCD?
2. Token aus der Tabelle — keine Magic-dp/-Farben inline; **eine** Linienstärke (`hairline`), **ein**
   Gitter-Gap (`tileGap`) — auch über zwei verschiedene Grids gleich.
3. **Erst geppt, dann gebaut:** für jedes Chrome-Stück die geteilte `Eink*`-Komponente aus der
   Ersatztabelle nutzen (Suche → `EinkSearchBar`, TopBar → `HomeScreen`-`TopAppBar`, …) — nie roh,
   nie Eigenbau neben Bestehendem. Fehlt eine, in `ui/components/` ergänzen (nicht lokal duplizieren).
4. Icon über `AppIcons.<Zweck>` — nie Material-Icons, nie `LucideIcons.*` direkt. Gefülltes Glyph nur
   als Aktiv-Zustand (`lucideFilled`).
5. Modal? → `EinkModal` (schwarzer Rand; leerer Titel = kompakt ohne Kopf).
6. Neuer sichtbarer Text → `Strings`-Key in **DE + EN**, echte Umlaute/ß — **auch** Snackbars/Errors/inline.
7. Teil-Update statt Voll-Reload (siehe Anti-Pattern) — kein `Loading`-Flash bei kleinen Änderungen.
8. **Öffnet in Endgröße?** Modal/Liste mit fester Höhe + Rahmen-Platzhalter — kein Nachwachsen beim
   Laden (E-Ink-Layout-Stabilität).
9. **Ein Auswahl-Signal:** jede Selektion über denselben `accent`-Token.
10. Auf echter Boox/`eink_test` (1264×1680@300) verifizieren — „sieht man am LCD" reicht nicht.

## Anti-Pattern: Voll-Reload bei Teil-Update

Eine kleine Zustandsänderung darf **nie** die ganze Seite neu laden. Auf E-Ink ist der `Loading`-Durchlauf
ein sichtbarer Flash + Full-Refresh (Ghosting) — unnötig, weil sich nur ein Detail änderte.
- **Falsch:** Mark-as-read / Typ-Zuweisung / Favorit-Toggle löst `refreshTrigger` aus →
  `Loading → Content` (ganze Liste neu).
- **Richtig:** Geladenen `Content` behalten, betroffenen Teil **optimistisch** patchen — separater
  `MutableStateFlow` (`_readOverrides`/`_typeOverride`) via `combine(baseState, …)`. Bei Server-Fehler
  zurücknehmen. **Voll-Reload nur** bei echtem Kontextwechsel. Referenz: `SeriesDetailViewModel`.

## Anti-Pattern (sofort ablehnen)

- **Roh statt geteilt.** Ein rohes Material-Control (`OutlinedTextField`-Suche, `Checkbox`,
  `FilterChip`, `Button`) **oder** ein Chrome-Eigenbau (eigene TopBar, eigenes Suchfeld) neben einer
  bestehenden geteilten Komponente — auch wenn es lokal „funktioniert". Erst die Ersatztabelle
  (oben) greppen, dieselbe Komponente wie der Rest der App nutzen. Symptom: „sieht anders aus als der
  Rest der App" / „zwei TopBars".
- **Geräte-Annahme binär / Farbe an Bewegung gekoppelt.** `if (LocalEinkMode) monochrom else farbig`
  koppelt Farbe an die *Bewegungs*-Achse — Farb-E-Ink (Kaleido: kein Motion, **aber** Farbe) fiele
  fälschlich auf monochrom. Farbe **immer** über `allowsAccentColor`, Bewegung über `allowsMotion`. Ein
  neues `isEink`/Boolean als modus-sensitives Gate einzuführen zementiert das binäre `EINK/SMARTPHONE`
  als Endzustand — erklärter **Nogo** (`big-picture-and-goals.md`). Neue modus-sensitive Stelle liest
  `LocalDisplayBehavior`, nie ein frisches Boolean.
- **Akzentfarbe unkonditional / global.** Akzent ohne `allowsAccentColor`-Gate bricht mono E-Ink
  (Pflicht: monochrom). Farbe gehört hinter das Gate **und** als zentrales Theme-Token (nicht inline).
- **Zu dünne / zu blasse Linien.** Auf E-Ink verschwindet ein 1px-Strich und ein heller Grauton
  (`#CCCCCC`). Rahmen **≥ 1.5 dp**, Farbe ≥ mittelgrau (`outlineVariant`), Betonung `outline` (schwarz).
  **Nie nackte Material-Controls:** Buttons über **`EinkOutlinedButton`** (`EinkButtons.kt`, Rand =
  `EinkTokens.hairline`), Divider mit `thickness = EinkTokens.hairline`. **Alle** gleich-dünnen Linien
  teilen **eine** Stärke — keine gemischten 1 dp/1.5 dp im selben Screen.
- **Zu dünne Schrift.** Material-Default ist `FontWeight.Normal` (400) — auf E-Ink bei kleiner Schrift
  zu blass. **Nicht** pro `Text` ein `fontWeight`, sondern **zentral** über `EinkTypography` (`Theme.kt`):
  Body → Medium (500), Labels + kleine Titel → SemiBold (600); große Überschriften bleiben.
- **Disabled-Text zu blass.** Material dämpft disabled-Content auf Grau (alpha 0.38, auf E-Ink kaum
  lesbar) — und **überschreibt dabei eine explizite Textfarbe im Button** (`EinkOutlinedButton` reicht
  `disabledContentColor` nicht durch). Wichtiger, lesbar-pflichtiger Text (z. B. Download-Fortschritt
  „Lädt… NN %") gehört darum **nicht** in einen `enabled = false`-Button: stattdessen während der Arbeit
  eine **eigene volle-Kontrast-Zeile** (`Row` + `Text(color = onSurface, fontWeight = Bold)` + ggf.
  `AnimatedAppIcon`) rendern, den klickbaren Button nur im Ruhezustand zeigen. Referenz: `UpdateSection`
  in `SettingsContent.kt`.
- **Magic-dp/-Farben inline statt Token.** Stock-Material-Controls (Slider, kontinuierlich) auf E-Ink.
- **Asymmetrie bei Geschwister-Elementen.** Gleiche Zeile / gleiche Rolle → **geteilte Maß-Konstante**:
  Button neben Eingabefeld = gleich hoch; flankierende Slots links/rechts = gleich breit; Aktions-Buttons
  gleich groß. Auf E-Ink fällt ein 4dp-Versatz sofort auf.
- **Button-Reihenfolge inkonsistent.** Sekundär (Abbrechen/Löschen) **immer links**, primär (Speichern/OK)
  **rechts** — über alle Dialoge/Footer gleich.

## Bezug

Gehört zu [[project-komga-eink-reader]]. Domain-Skills: [[komga-viewer-type-resolution]],
[[komga-guided-comic-reader]], [[komga-eink-color-filter]]. Regeln: `eink-design-language.md`
(Pflicht-Kurzfassung), `animation-gating.md`, `big-picture-and-goals.md` (Geräteklassen/Nogos).
LCD-Craft: `frontend-design`. Kotlin/Compose-Handwerk (Design-Patterns + Code-Konventionen,
**E-Ink schlägt sie**): `android-development`, `chrisbanes-skills:*` — siehe Sektion oben.
