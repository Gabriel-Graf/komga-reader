---
name: eink-ui
description: Use when building or refactoring UI in the Komga E-Ink Reader (Jetpack Compose, Onyx Boox Go Color 7 Gen2). Captures the device-native "Onyx-look" design language — flat high-contrast e-ink surfaces, bottom menubar, labelled action icons, tile-landing settings, hairline cards, black-bordered modals, outlined Material Symbols icon mapping. Load at the start of any visual/UI task in this app.
---

# E-Ink UI (Onyx-Boox-Look)

Design-Sprache, extrahiert aus der **Stock-Firmware der Onyx Boox Go Color 7 Gen2**
(Bibliothek/Shop/Notizen/Speicher/Apps/Einstellungen-Launcher). Ziel: die App fühlt
sich **nativ zum Gerät** an. Die exakten Onyx-Icon-Assets sind proprietär — wir treffen
ihre Form-Sprache mit **Material Symbols (Outlined, dünner Strich)**, kopieren aber keine
Assets.

> Grundprinzip bleibt das bestehende E-Ink-Theme: maximaler Kontrast, **keine** Verläufe/
> Schatten/Animationen (Ghosting-arm), Tiefe über Rahmen statt Elevation.

## Wann diesen Skill nutzen

Jede UI-Arbeit in `app/` dieses Projekts: neuer Screen, Komponente, Dialog, Theming.
Erst hier die passenden Bausteine + Tokens nachschlagen, dann mit den vorhandenen
`ui/components/`-Composables bauen — keine Material-Stock-Controls roh verwenden, wenn
ein themed Pendant existiert.

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

Hairline (`outlineVariant`) für ruhige Flächen (Tiles, Listenzeilen), **schwarzer**
`outline` nur für Betonung und **immer** für Modals.

## Baustein-Katalog (`ui/components/`)

### Bottom-Menubar — `EinkBottomBar`
Die Haupt-Navigation. Flach, gleich breite Items, **Icon über Label**, Outlined-Icon
(28 dp) + ~11 sp Label. Aktives Item: **kurzer schwarzer Akzent-Balken (3 dp) über dem
Icon** + Label fett. Reihenfolge fix: primärer Tab ganz **links**, Einstellungen ganz
**rechts**. Platz für weitere Tabs (Plugins, Gruppen) ohne Umbau.

Aktueller Satz: `Bibliothek · Gruppen · Plugins(bald) · Einstellungen`.

### Top-Action-Leiste
Nur Icons (kein Text) rechts in der `TopAppBar`, Outlined, 24 dp. Nur **funktionierende**
Aktionen zeigen — keine Dead-Buttons. Links optional View-Toggle-Cluster.

### Tile (Kachel) — `SettingsTile` / `Tile`
Rechteck mit **Hairline-Rahmen** (`outlineVariant`), Radius 10 dp, großzügiges Padding
(14–16 dp). Layout: **Icon links** (24 dp) · Titel (titleSmall) + Summary (bodySmall,
`onSurfaceVariant`) · Chevron-rechts bei Drill-in. Zwei-Spalten-Grid auf dem Landing
(`GridCells.Fixed(2)`, `tileGap`). Summary zeigt den **aktuellen Wert** ("komga.intern",
"E-Ink · Hell", "Deutsch").

### Listenzeile / Auswahl — `ChoiceRow`
Volle Breite, `selectable`, Label links + **Häkchen rechts wenn gewählt** (statt
RadioButton-Kreis — ruhiger auf E-Ink). Hairline-Divider zwischen Zeilen. Für
Theme/Sprache/Modus-Auswahl.

### Sub-Page-Gerüst — `SubPageScaffold`
`Scaffold` + `TopAppBar` mit Titel + Zurück-Pfeil (`AutoMirrored.Outlined.ArrowBack`),
scrollender Body mit `screenPadding`. Jede Settings-Unterseite nutzt es.

### Modal — `EinkModal`
**Immer schwarzer Rand** (`strongBorder`, `outline`), weiße Surface, Radius `large`.
`Dialog` + bordered `Surface`, **nicht** das nackte Material `AlertDialog`. Sticky-Titel
oben, Aktionen unten (Bestätigen rechts, Abbrechen links). Genau **ein** Modal gleichzeitig.

### Empty-State
Zentrierte Line-Art-Illustration/Icon + Label + darunter gestapelte **full-width
Outlined-Buttons** für Primäraktionen (vgl. „Keine Bücher" → Buch hinzufügen / …).

## Icon-Mapping (Material Symbols Outlined)

| Zweck | Icon |
|-------|------|
| Bibliothek | `Outlined.LibraryBooks` |
| Gruppen | `Outlined.Dashboard` |
| Plugins | `Outlined.Extension` (Puzzle ≈ Onyx-Hexagon-Apps) |
| Einstellungen | `Outlined.Settings` |
| Verbindung/Server | `Outlined.Cloud` |
| Darstellung/Theme | `Outlined.Contrast` |
| Reader | `Outlined.ChromeReaderMode` |
| Downloads | `Outlined.Download` |
| Sprache | `Outlined.Language` |
| Über | `Outlined.Info` |
| Sync/Refresh | `Outlined.Sync` |
| Suche | `Outlined.Search` |
| Zurück | `AutoMirrored.Outlined.ArrowBack` |

Immer **Outlined**-Variante (dünner Strich = Onyx-Look), nie `Filled`.

## Settings-Architektur

**Adaptives Master-Detail.** Eine Section-Registry (`SettingsSection`: id, Icon, Titel,
`searchTerms`, `content(query)` in `SettingsSections.kt`) treibt drei Layouts über
`BoxWithConstraints` (`SettingsScreen.kt`) — **keine** eigenen NavHost-Seiten pro Sektion mehr:

- **< 600 dp (Phone):** Accordion — eine Scroll-Liste, Sektionskopf tappbar, Inhalt klappt
  inline auf (`AnimatedVisibility`, einziges erlaubtes Motion).
- **600–900 dp (Tablet):** Sidebar links (Icon 28 dp + `titleMedium`, aktiv = schwarzer
  Akzent-Balken links + fett) + scrollendes Detail rechts.
- **≥ 900 dp (großes E-Ink):** wie Tablet, größer (Icon 32 dp, größeres Label, mehr Padding).

Sektionen (Reihenfolge fix): **Verbindung** (Server-Formular, Status, Verbinden/Trennen) ·
**Darstellung** (Theme Hell/Dunkel/System via `ChoiceRow`) · **Reader** (Webtoon-Überlappung
`StepperRow` + Anzeige-Modus E-Ink/Smartphone) · **Downloads** (SAF-Ordner-Picker) ·
**Sprache** (DE/EN) · **Über** (App-Name, `BuildConfig.VERSION_NAME`, Geräte-Hinweis).
Die Inhalte sind scaffold-freie Content-Composables (`SettingsContent.kt`), gehostet inline.

**Such-Highlight.** Die TopBar-Suche reicht auf dem Settings-Tab **live** `query` an
`SettingsScreen`. Reine Funktionen `matchRanges`/`sectionMatches` (`SettingsSearch.kt`,
unit-getestet) filtern auf Treffer-Sektionen und springen zur ersten; `HighlightText`
markiert den gematchten Text **fett + `outlineVariant`-Hintergrund** (monochrom, keine
Akzentfarbe) — bis in `ChoiceRow`-Labels hinein. So sieht der Nutzer, *warum* etwas
gefunden wurde.

## Checkliste pro UI-Stück

1. Token aus der Tabelle nehmen — keine Magic-dp/Farben inline.
2. Existierende `ui/components/`-Composable nutzen; fehlt eine, dort ergänzen (nicht lokal duplizieren).
3. Outlined-Icon aus dem Mapping.
4. Modal? → `EinkModal` (schwarzer Rand).
5. Neuer sichtbarer Text → `Strings`-Key in **DE + EN**, echte Umlaute.
6. Keine Animation/Schatten/Verläufe (E-Ink).

## Anti-Pattern (sofort ablehnen)

- **Zu dünne / zu blasse Linien.** Auf E-Ink verschwindet ein 1px-Strich und ein zu heller
  Grauton (z. B. `#CCCCCC`) komplett — Rahmen, Divider und Card-Kanten werden unsichtbar.
  Regel: Rahmen **≥ 1.5 dp**, Farbe mindestens mittelgrau (`outlineVariant` = `#777777`/`#8A8A8A`),
  für Betonung `outline` (schwarz). Wer einen Divider/Rahmen setzt, prüft ihn auf echter
  E-Ink-Hardware (oder Emulator `eink_test`) — „sieht man am LCD" reicht nicht.
- Magic-dp/-Farben inline statt Token. Stock-Material-Controls (Slider, kontinuierlich) auf E-Ink.
