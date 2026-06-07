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

## Anti-Pattern: Voll-Reload bei Teil-Update

Eine kleine Zustandsänderung darf **nie** die ganze Seite neu laden. Auf E-Ink ist der
`Loading`-Durchlauf ein sichtbarer Flash + Full-Refresh (Ghosting) — und unnötig, weil sich
nur ein Detail geändert hat.

- **Falsch:** Mark-as-read / Typ-Zuweisung / Favorit-Toggle löst einen `refreshTrigger` aus,
  der den `state`-Flow neu durch `Loading → Content` schickt (komplette Liste neu rendern).
- **Richtig:** Den geladenen `Content` behalten und nur den betroffenen Teil **optimistisch**
  patchen — über einen separaten `MutableStateFlow` (z. B. `_readOverrides`, `_typeOverride`),
  der via `combine(baseState, …)` in den `Content` einfließt. Bei Server-Fehler das optimistische
  Update zurücknehmen. Server-Call läuft im Hintergrund, ohne den State auf `Loading` zu werfen.
- **Voll-Reload nur** bei echtem Seiten-/Kontextwechsel (anderer Server, andere Serie, Pull-to-
  Refresh). Referenz-Implementierung: `SeriesDetailViewModel` (`_readOverrides`/`_typeOverride`
  → `state`-`combine`, `baseState` lädt nur bei `servers.config`).

## Muster: lange Beschreibung neben dem Cover (Truncate + „Mehr lesen")

Beschreibungstexte (Serie/Kapitel) stehen **neben** dem Cover, unter den Tags, und füllen den
Platz bis zur **Cover-Unterkante** (rechte Spalte `height(HERO_COVER_HEIGHT)`, Beschreibung mit
`weight(1f)`). Passt der Text nicht in die verfügbare Höhe:

- mit **„…"** kürzen (`TextOverflow.Ellipsis`; `maxLines` aus Box-Höhe / Zeilenhöhe via
  `BoxWithConstraints` berechnet, Überlauf über `onTextLayout { hasVisualOverflow }` erkannt —
  bei Überlauf eine Zeile für den Button reservieren),
- eine **„Mehr lesen"**-Zeile einblenden,
- die den **vollständigen Text in einem Readonly-Modal** zeigt: `EinkInfoDialog` (Titel +
  **nur X** oben, kein Footer), Inhalt scrollbar (`verticalScroll` + `heightIn(max=…)`).

So behält die Hero-Karte **immer** ihre Form — egal wie lang die Beschreibung ist. Fehlt eine
Beschreibung, steht dort der Platzhalter (`noDescription`, gedämpft `onSurfaceVariant`).
Referenz: `TruncatedDescription` / `DescriptionModal` in `SeriesDetailScreen.kt`.

## Checkliste pro UI-Stück

1. Token aus der Tabelle nehmen — keine Magic-dp/Farben inline.
2. Existierende `ui/components/`-Composable nutzen; fehlt eine, dort ergänzen (nicht lokal duplizieren).
3. Outlined-Icon aus dem Mapping.
4. Modal? → `EinkModal` (schwarzer Rand).
5. Neuer sichtbarer Text → `Strings`-Key in **DE + EN**, echte Umlaute.
6. Keine Animation/Schatten/Verläufe (E-Ink).
7. Teil-Update statt Voll-Reload (siehe Anti-Pattern oben) — kein `Loading`-Flash bei kleinen Änderungen.

## Anti-Pattern (sofort ablehnen)

- **Zu dünne / zu blasse Linien.** Auf E-Ink verschwindet ein 1px-Strich und ein zu heller
  Grauton (z. B. `#CCCCCC`) komplett — Rahmen, Divider und Card-Kanten werden unsichtbar.
  Regel: Rahmen **≥ 1.5 dp**, Farbe mindestens mittelgrau (`outlineVariant` = `#777777`/`#8A8A8A`),
  für Betonung `outline` (schwarz). Wer einen Divider/Rahmen setzt, prüft ihn auf echter
  E-Ink-Hardware (oder Emulator `eink_test`) — „sieht man am LCD" reicht nicht.
  **Einheitliche Linienstärke (Pflicht):** Material-Stock-Controls bringen zu dünne Ränder mit —
  ein nackter `OutlinedButton` rendert ~1 dp, ein `HorizontalDivider` 1 dp; auf E-Ink kaum
  sichtbar. Daher **nie nackt**: Buttons über den Wrapper **`EinkOutlinedButton`**
  (`ui/components/EinkButtons.kt`, Rand = `EinkTokens.hairline` = 1.5 dp/`outline`), Divider mit
  `thickness = EinkTokens.hairline`. **Alle** gleich-dünnen Linien teilen sich **eine** Stärke
  (`EinkTokens.hairline`) — keine gemischten 1 dp/1.5 dp-Ränder im selben Screen.
- **Zu dünne Schrift.** Material-Default-Text ist `FontWeight.Normal` (400) — auf E-Ink (kein
  Sub-Pixel-Smoothing) bei **kleiner** Schrift zu blass/dünn (Such-Placeholder, Kapitel-Untertitel,
  „Lädt…", leere-Tab-Platzhalter). **Nicht** pro `Text` ein `fontWeight` setzen, sondern **zentral**
  über die `EinkTypography` (`Theme.kt`, an `MaterialTheme(typography=…)` übergeben): Body → Medium
  (500), Labels + kleine Titel → SemiBold (600); große Überschriften bleiben (Größe trägt den
  Kontrast). Neue kleine/sekundäre Texte erben das automatisch — eine Quelle der Wahrheit.
- Magic-dp/-Farben inline statt Token. Stock-Material-Controls (Slider, kontinuierlich) auf E-Ink.
- **Asymmetrie bei Geschwister-Elementen.** Elemente in **derselben Zeile** oder mit **gleicher Rolle**
  müssen sich Maße teilen: ein Button neben einem Eingabefeld/Dropdown ist **gleich hoch** (gemeinsame
  `height`-Konstante, nicht zwei verschiedene Größen), flankierende Icons/Slots links+rechts sind
  **gleich breit** (sonst verschiebt sich das mittige Element), Aktions-Buttons sind gleich groß.
  Auf E-Ink fällt ein 4dp-Höhenversatz sofort auf. Faustregel: gleiche Zeile/gleiche Rolle → geteilte Maß-Konstante.
- **Button-Reihenfolge inkonsistent.** Sekundäre Aktion (Abbrechen/Löschen) **immer links**, primäre
  (Speichern/OK) **rechts** — über alle Dialoge/Footer hinweg gleich.
