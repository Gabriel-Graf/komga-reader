---
name: komga-eink-ui-polish
description: Use at the START of any UI/visual task in the Komga-Reader app (Jetpack Compose, Onyx Boox Go Color 7 Gen2). Entry point + index for the Komga UI — the device-native "Onyx-look" design language, the three device classes (mono E-Ink / colour E-Ink Kaleido / LCD) on two orthogonal axes (motion ⟂ accent colour), component catalogue, tokens, Lucide/AppIcons, settings architecture, anti-patterns. Routes to the domain skills (viewer-type, guided-comic, colour-filter) and to frontend-design for LCD craft. Building or refactoring a screen, component, dialog, theme, indicator, list, reader chrome — start here.
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
| Farb-E-Ink (Kaleido) | **false** | **true** (gedämpft) | keine Bewegung, **aber** gedämpfte Akzentfarbe erlaubt |
| LCD-Phone/-Tablet | **true** | **true** | Bewegung + Farbe erlaubt (hier glänzt `frontend-design`) |

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
  *Bewegungs*-Achse — Kaleido bekäme sonst fälschlich Schwarz).

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
| Animation gaten (jede Bewegung braucht E-Ink-Pfad) | Regel `animation-gating.md` |
| Verbindliche Kurzfassung der Designsprache (Spec) | Regel `eink-design-language.md` |
| Warum nicht binär (Geräteklassen, Ziele, Nogos) | Regel `big-picture-and-goals.md` |
| Chrome/UI-Element austauschbar bauen (modulare UI, Community-Looks) | Regel `big-picture-and-goals.md` → „Modulare UI"/`ui-modularity` |

Die Geräteklassen-Disziplin (oben) gilt in **allen** Sub-Skills. **Ziel modulare UI:** jedes
Chrome-Element (Overlay/Header/Buttons/Navigation/Tiles/Settings) gehört hinter eine adressierbare,
austauschbare Grenze — nie hart in den Compose-Baum verdrahtet (sonst kann die Community nichts
ersetzen ohne Fork). Beim Bauen mitdenken.

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

### Bottom-Menubar — `EinkBottomBar`
Haupt-Navigation. Flach, gleich breite Items, **Icon über Label**, Outlined-Icon (28 dp) + ~11 sp Label.
Aktives Item: **kurzer Akzent-Balken (3 dp) über dem Icon** + Label fett. Reihenfolge fix: primärer Tab
**links**, Einstellungen **rechts**. Aktueller Satz: `Bibliothek · Gruppen · Plugins(bald) · Einstellungen`.
> **Akzent-Balken-Farbe = 2-Achsen-Fall:** heute `onSurface` (schwarz, monochrom). Sobald ein
> Akzent-Token existiert, die Aktiv-Farbe hinter `allowsAccentColor` verzweigen (mono → schwarz,
> Kaleido/LCD → gedämpfter Akzent). Die **Mechanik** (Balken, kein Material-Indikator, keine Animation)
> bleibt über alle Klassen gleich — nur die Farbe verzweigt.

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
immer `AppIcons.<Zweck>`. Eine Variante pro Zweck (Outline), kein Filled/Outlined-Mix.
(`androidx.compose.material3.Icon` — das Composable — bleibt.)

**Neues Icon:** Glyph auf [lucide.dev/icons](https://lucide.dev/icons) → `icon-set.mjs` ergänzen →
`cd tools/icons && npm run generate` → `AppIcons.kt` semantischen Eintrag setzen → Aufrufstelle nutzt
`AppIcons.<Zweck>`. **Stroke anpassen:** nur `STROKE` in `LucideIcons.kt`. Auf echter Boox/`eink_test`
verifizieren (zu dünn = blass auf E-Ink).

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

## Checkliste pro UI-Stück

1. **Beide Achsen beantwortet?** Bewegung über `allowsMotion`/`LocalEinkMode`, Farbe über
   `allowsAccentColor` — getrennt, nie gekoppelt. Funktioniert es auf mono E-Ink **und** Kaleido **und** LCD?
2. Token aus der Tabelle — keine Magic-dp/-Farben inline.
3. Existierende `ui/components/`-Composable nutzen; fehlt eine, dort ergänzen (nicht lokal duplizieren).
4. Icon über `AppIcons.<Zweck>` — nie Material-Icons, nie `LucideIcons.*` direkt.
5. Modal? → `EinkModal` (schwarzer Rand).
6. Neuer sichtbarer Text → `Strings`-Key in **DE + EN**, echte Umlaute/ß.
7. Teil-Update statt Voll-Reload (siehe Anti-Pattern) — kein `Loading`-Flash bei kleinen Änderungen.
8. Auf echter Boox/`eink_test` (1264×1680@300) verifizieren — „sieht man am LCD" reicht nicht.

## Anti-Pattern: Voll-Reload bei Teil-Update

Eine kleine Zustandsänderung darf **nie** die ganze Seite neu laden. Auf E-Ink ist der `Loading`-Durchlauf
ein sichtbarer Flash + Full-Refresh (Ghosting) — unnötig, weil sich nur ein Detail änderte.
- **Falsch:** Mark-as-read / Typ-Zuweisung / Favorit-Toggle löst `refreshTrigger` aus →
  `Loading → Content` (ganze Liste neu).
- **Richtig:** Geladenen `Content` behalten, betroffenen Teil **optimistisch** patchen — separater
  `MutableStateFlow` (`_readOverrides`/`_typeOverride`) via `combine(baseState, …)`. Bei Server-Fehler
  zurücknehmen. **Voll-Reload nur** bei echtem Kontextwechsel. Referenz: `SeriesDetailViewModel`.

## Anti-Pattern (sofort ablehnen)

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
- **Disabled-Text zu blass.** Material dämpft disabled-Content auf Grau (auf E-Ink kaum lesbar). Muss der
  Text lesbar bleiben (z. B. Fortschritt in nicht-klickbarem Button), Textfarbe **explizit**
  `colorScheme.onSurface` setzen.
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
LCD-Craft: `frontend-design`.
