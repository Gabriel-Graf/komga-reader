# Settings Master-Detail + Such-Highlight — Design

**Datum:** 2026-06-07
**Modul:** `app` (UI), keine Domain-/Source-Änderungen
**Status:** Genehmigt (Brainstorming), bereit für Plan

## Ziel

Die Einstellungen vom heutigen **Kachel-Landing + Drill-in auf eigene NavHost-Seiten**
auf ein **adaptives Master-Detail** umstellen:

- **Tablet / großes E-Ink:** vertikale Sektions-Sidebar links, Inhalt der gewählten
  Sektion rechts (kein Seitenwechsel mehr).
- **Smartphone (schmal):** eine durchgehende Scroll-Seite mit **Accordion-Sektionen**
  (Kopf tappbar, Inhalt klappt inline auf).
- **Suche** filtert auf Treffer-Sektionen, springt automatisch zur ersten und
  **markiert den gematchten Text** bis in die einzelne Einstellung — der Nutzer sieht,
  *warum* eine Einstellung gefunden wurde.
- **Tab-Icons + Text größer** als heute; alles skaliert mit der Bildschirmgröße.

Bleibt vollständig innerhalb der E-Ink-Designsprache (flach, monochrom, Hairline-Rahmen,
keine Animation, keine Akzentfarbe) — siehe `.claude/rules/eink-design-language.md` und
Skill `eink-ui`.

## Ist-Zustand (vor dem Umbau)

- `HomeScreen` hält Bottom-Bar + TopBar-Suche. Settings-Tab rendert `SettingsLandingScreen`
  (2-Spalten-`SettingsTile`-Grid). Tap → `onOpenSettingsPage(page)` → NavHost-Route
  `settings/<page>`.
- 6 Einzel-Screens (`ConnectionSettingsScreen`, `AppearanceSettingsScreen`,
  `ReaderSettingsScreen`, `DownloadsSettingsScreen`, `LanguageSettingsScreen`,
  `AboutScreen`), jeder mit `SubPageScaffold` (eigene TopBar + Zurück-Pfeil), alle über
  die geteilte `SettingsViewModel` (`hiltViewModel`).
- Suche: TopBar gibt `submitted` an `SettingsLandingScreen.query` → filtert ganze Kacheln
  über einen konkatenierten `keywords`-Blob. **Kein** Text-Highlight, **keine** live-Suche.
- Routing in `MainActivity` über `settingsRoute(page)` + 6 `composable("settings/...")`.
- Kein externer Deep-Link auf eine Settings-Seite (nur `HomeScreen`/`MainActivity`
  referenzieren `SettingsPage`).

## Architektur

### Section-Registry (Single Source of Truth)

Jede Sektion beschreibt sich als Daten statt als eigener Screen:

```kotlin
enum class SettingsSectionId { CONNECTION, APPEARANCE, READER, DOWNLOADS, LANGUAGE, ABOUT }

data class SettingsSection(
    val id: SettingsSectionId,
    val icon: ImageVector,                 // Outlined, aus eink-ui Icon-Mapping
    val title: String,                     // lokalisiert
    val searchTerms: List<String>,         // Sidebar-Filter + "warum gefunden"
    val content: @Composable (query: String) -> Unit,  // die Settings-Zeilen
)
```

Eine `buildSettingsSections(strings, viewModel)`-Funktion (im `settings`-Paket) baut die
Liste. `searchTerms` sind echte i18n-Strings (Titel + Zeilen-Labels + Helper-Texte), keine
Komga-spezifischen Rohwerte.

### Content-Composables (aus den alten Screens extrahiert)

Die Bodies der 6 Screens werden zu Content-Composables **ohne** `SubPageScaffold` —
der Master-Detail-Host bzw. die Accordion-Sektion liefert den Rahmen. Jedes nimmt die
geteilte `SettingsViewModel` und den aktiven `query` (für Highlight):

- `ConnectionSettingsContent(vm, query)` — Server-Formular (Name/URL/API-Key — oder —
  Benutzer/Passwort), Status, Verbinden/Trennen, Autofill bleibt erhalten.
- `AppearanceSettingsContent(vm, query)` — Theme `ChoiceRow`s.
- `ReaderSettingsContent(vm, query)` — Webtoon-`StepperRow` + Display-Mode `ChoiceRow`s.
- `DownloadsSettingsContent(vm, query)` — SAF-Ordner-Picker.
- `LanguageSettingsContent(vm, query)` — DE/EN `ChoiceRow`s.
- `AboutContent(query)` — App-Name, Version, Geräte-Hinweis.

Die alten `*SettingsScreen.kt` / `SettingsLandingScreen.kt` entfallen (durch die Contents
ersetzt). `SubPageScaffold` bleibt als Komponente erhalten (andere Screens nutzen es evtl.),
wird hier aber nicht mehr verwendet.

### Host: `SettingsScreen`

Ersetzt `SettingsLandingScreen` im Settings-Tab von `HomeScreen`. Signatur:

```kotlin
@Composable
fun SettingsScreen(query: String, modifier: Modifier = Modifier,
                   viewModel: SettingsViewModel = hiltViewModel())
```

`BoxWithConstraints` wählt das Layout nach `maxWidth`:

| Breite | Layout | Sizing-Klasse |
|---|---|---|
| `< 600.dp` | **Accordion** (`SettingsAccordion`) | `Compact` |
| `600.dp ..< 900.dp` | **Master-Detail** (`SettingsMasterDetail`) | `Medium` |
| `>= 900.dp` | **Master-Detail** | `Expanded` (größer) |

Sizing-Klasse ist ein kleines `data class SettingsSizing(sidebarWidth, iconSize,
labelStyle, rowPadding)` — keine Magic-dp verstreut.

- **Sidebar (`SettingsSidebar`):** vertikale Liste der (sichtbaren) Sektionen. Item =
  Icon (28 dp Medium / 32 dp Expanded) **über/neben** Label (`titleMedium`/`titleLarge`).
  Aktives Item: **schwarzer Akzent-Balken links (3 dp)** + Label fett (vertikale Variante
  der `EinkBottomBar`-Sprache). Hairline-Trenner zwischen Items, kein Schatten.
- **Detail:** rendert `section.content(query)` der gewählten Sektion mit `screenPadding`,
  scrollbar.
- **Accordion (`SettingsAccordion`):** `LazyColumn`; pro Sektion ein tappbarer Kopf
  (Icon + Label + Chevron `▸/▾`), darunter `AnimatedVisibility` (simples Auf-/Zuklappen
  ist laut E-Ink-Regel erlaubt) mit `section.content(query)`.

### Suche + Highlight

Reiner, testbarer Kern in `app` (`ui/settings/SettingsSearch.kt`), Compose-frei:

```kotlin
/** Alle Vorkommen von query in text, case-insensitive. Leere query/Treffer → []. */
fun matchRanges(text: String, query: String): List<IntRange>

/** Sektion matcht, wenn irgendein searchTerm die (getrimmte) query enthält. */
fun sectionMatches(section: SettingsSection, query: String): Boolean
```

Verhalten (Auswahl des Nutzers: *Filtern + auto-springen + überall markieren*):

- `query.isBlank()` → alle Sektionen sichtbar, kein Highlight, kein Auto-Sprung.
- sonst → sichtbare Sektionen = `sections.filter { sectionMatches(it, query) }`.
  - **Master-Detail:** Sidebar zeigt nur sichtbare Sektionen. `LaunchedEffect(query)`:
    wenn die gewählte Sektion nicht (mehr) sichtbar ist, auf die erste sichtbare wechseln.
  - **Accordion:** nur sichtbare Sektionen, diese **auto-aufgeklappt**.
  - Keine sichtbare Sektion → zentriertes `searchNoResults` (bestehender Key).

Highlight-Composable (`app`, dünn über `matchRanges`):

```kotlin
@Composable
fun HighlightText(text: String, query: String, style: TextStyle,
                  color: Color = LocalContentColor.current, modifier: Modifier = Modifier)
```

Baut `AnnotatedString`: gematchte Bereiche bekommen `SpanStyle(fontWeight = Bold,
background = colorScheme.outlineVariant)` — **monochrom, E-Ink-konform, keine Akzentfarbe**.
Eingesetzt in: Sidebar-Label, Accordion-Kopf, `SectionHeader`, Helper-/Status-/Wert-Texte
und in `ChoiceRow` (bekommt optionalen `query: String = ""`-Param → highlightet sein Label).
So reicht das Highlight bis zur einzelnen Einstellung (z. B. „Dunkel", „Webtoon-Überlappung").

`ChoiceRow` ist die einzige geteilte Komponente, die einen neuen optionalen Param bekommt;
bestehende Aufrufer bleiben unverändert (Default `""`).

### Live-Suche auf dem Settings-Tab

In `HomeScreen` wird auf dem Settings-Tab der **live** `query` (nicht `submitted`) an
`SettingsScreen` durchgereicht → Highlight beim Tippen. Medien-Suche bleibt submit-basiert.
Kleiner TopBar-Fix: Such-Pille nutzt adaptive Breite (Cap statt fixe `360.dp`), damit sie
auf schmalen Screens nicht überläuft.

### Migration / Aufräumen in `MainActivity`

- 6 `composable("settings/...")`-Routen, `settingsRoute(page)` und der
  `onOpenSettingsPage`-Parameter von `HomeScreen` entfallen.
- Imports der alten Settings-Screens raus; `SettingsScreen` ist self-contained.

### Skill-Nachzug

`eink-ui`-Skill, Abschnitt „Settings-Architektur": von „Kachel-Landing → Unterseiten" auf
„adaptives Master-Detail (Tablet/E-Ink) bzw. Accordion (Phone) + Such-Highlight" aktualisieren.
Der Skill ist die verbindliche Kurzform der Spec und muss konsistent bleiben.

## Datenfluss

```
HomeScreen (Tab=Settings)
  └─ query (live) ─▶ SettingsScreen(query)
       BoxWithConstraints → Layout + Sizing
       sections = buildSettingsSections(strings, vm)
       visible  = if (query.blank) sections else sections.filter { sectionMatches(it,query) }
         ├─ Master-Detail: Sidebar(visible, selected) | Detail(selected.content(query))
         └─ Accordion: visible.forEach { Header + (expanded||query) → content(query) }
                content(query) ─▶ HighlightText / ChoiceRow(query=…) markiert Treffer
```

## Fehlerbehandlung / Randfälle

- Leere Suche: voller Funktionsumfang, kein Highlight.
- Kein Treffer: `searchNoResults`.
- Master-Detail Auto-Sprung greift nur, wenn die aktuelle Auswahl ausgefiltert ist
  (sonst Auswahl beibehalten — keine ungewollten Sprünge beim Weitertippen).
- `matchRanges` mit überlappenden/leeren Mustern: leere query → leere Liste (kein
  Endlos-Match).

## Tests

**Unit (pure, TDD zuerst):** `SettingsSearchTest`
- `matchRanges`: kein Vorkommen → []; einzelnes; mehrfaches (zwei Bereiche);
  case-insensitive; query leer → []; query länger als text → [].
- `sectionMatches`: Term enthält query (true); kein Term (false); leere query
  (Konvention: false → Aufrufer behandelt blank separat); case-insensitive.

**E2E / Emulator (Beweis pflicht):**
- `eink_test` (1264×1680): Master-Detail sichtbar, Sidebar links + Detail rechts,
  Sektionswechsel ohne Seitenwechsel. Screenshot.
- Schmales AVD / Resize: Accordion, Kopf auf-/zuklappen. Screenshot.
- Suche „dunkel" (o. ä.): Filter + Auto-Sprung + sichtbarer Marker auf dem Label.
  Screenshot.
- Build grün (`./gradlew :app:assembleDebug` + Unit-Tests).

## Nicht im Scope (YAGNI)

- Keine Domain-/Source-/Daten-Änderungen.
- Keine neuen Settings-Inhalte/Optionen.
- Kein Deep-Link-Mechanismus auf einzelne Sektionen (kein bestehender Aufrufer braucht ihn).
- Keine neuen i18n-Keys (vorhandene reichen).
