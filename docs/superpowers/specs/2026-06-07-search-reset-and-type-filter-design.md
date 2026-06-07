# Design: Suche zurücksetzen + Werk-Typ-Filter beim Stöbern

Datum: 2026-06-07
Status: Entwurf zur Review

## Ziel

Drei kleine UI/UX-Erweiterungen rund um die persistente Suchzeile in der TopBar:

1. **Clear-Button (✕) im Suchfeld** — setzt Sucheingabe **und** Ergebnisse zurück.
2. **Werk-Typ-Filter beim Stöbern** — ein Filter-Icon rechts außerhalb der Suchleiste
   (aber in der TopBar), nur im Browse-/Bibliothek-Tab. Filtert die Werke nach Typ
   (Manga, Comic, Webtoon, Roman) ohne aktive Texteingabe. Aktive Filter erscheinen als
   entfernbare Chips **im** Suchfeld. Die Filter-Auswahl ist ein nicht-modales
   Kontextmenü-Overlay direkt am Icon (Mehrfachauswahl).
3. **Aussagekräftiger Platzhalter**, wenn ein Filter aktiv ist, der Server verbunden ist,
   aber kein Werk zum Filter passt — erklärt ausführlich, wie man Werk-Typen festlegt.
   Ohne Server bleibt der bestehende „Server verbinden"-Platzhalter.

## Kontext (Ist-Zustand)

- Suchzeile: `EinkSearchBar` (`app/ui/components/EinkSearchBar.kt`) — eigener `BasicTextField`,
  Platzhalter links, Lupe rechts. Enter/Lupe lösen `onSubmit` aus.
- Such-State lebt in `HomeScreen` (`query`, `submitted`); `submitted` geht an `LibraryScreen`.
  Tab-Wechsel setzt `query`/`submitted` zurück.
- Browse: `LibraryScreen` → `BrowseTab` rendert ein 3-Spalten-Grid aus `source.browse()`.
  Die Liste ist **flach** über alle Regale; jede `Series` trägt nur `contentTypeOverride`
  (der Shelf-Default `defaultContentType` greift hier nicht). **Daher: „unbekannter Typ"
  beim Stöbern = `contentTypeOverride == null`.**
- `ContentType { MANGA, COMIC, NOVEL, WEBTOON }`; `Strings.localizedContentType(type)`
  liefert lokalisierte Labels inkl. `typeUnknown`.
- Nicht-modale Popups: `AnchoredMenuPopup` + `AnchorPositionProvider` existieren **privat**
  in `SeriesDetailScreen.kt` (Burger-Typ-Menü). Flach, Hairline-Border, kein Material-Dropdown.

## Designentscheidungen (vom Nutzer bestätigt)

- **Mehrfachauswahl** der Werk-Typen (mehrere gleichzeitig aktiv).
- **Immer Erklär-Platzhalter**, sobald ein Filter aktiv ist und das gefilterte Ergebnis leer
  ist (egal ob alle Werke unbekannt sind oder getypte existieren, die nicht passen).

## Komponenten & Änderungen

### A. `EinkSearchBar` — Clear-Button + Filter-Chips

Neue Parameter:
- `chips: @Composable (RowScope.() -> Unit) = {}` — führender Slot **vor** dem Textfeld
  für die Filter-Chips. Leer = nichts.
- `onClear: (() -> Unit)? = null` — wenn gesetzt **und** `query` nicht leer: ein ✕-Icon-Button
  **links der Lupe**. Klick ruft `onClear`.

Layout in der `decorationBox`-Row (von links):
`[chips…] [Box weight 1f: placeholder + inner] [✕ falls query≠leer] [Lupe]`

Die Chips liegen in einer horizontal scrollbaren Row (bei 1–2 Typen unkritisch; verhindert
Layout-Bruch bei mehreren). Jeder Chip: kompakt, Hairline-Border (E-Ink), kurzer Typ-Label
+ kleines ✕ zum Einzel-Entfernen.

### B. Geteiltes `AnchoredMenuPopup` (DRY-Refactor)

`AnchoredMenuPopup` + `AnchorPositionProvider` aus `SeriesDetailScreen.kt` nach
`app/ui/components/AnchoredMenuPopup.kt` extrahieren (unverändertes Verhalten, jetzt `internal`).
`SeriesDetailScreen` importiert die geteilte Version (privates Duplikat entfernt). Das neue
Filter-Menü nutzt dieselbe Basis → keine Popup-Duplikation.

### C. Werk-Typ-Filter-Menü

Neues Composable (`app/ui/components/TypeFilterMenu.kt`): nutzt `AnchoredMenuPopup`,
zeigt die 4 `ContentType`-Optionen als **Multi-Select-Toggle** (Häkchen bei aktiven).
**Kein** „Auto"-Eintrag (das ist ein Filter, keine Zuweisung). Tippen toggelt einen Typ,
das Menü bleibt offen (Mehrfachauswahl); Hardware-Back / Außenklick schließt.

### D. `HomeScreen` — State & Verdrahtung

- Neuer State: `typeFilter: Set<ContentType>` (rememberSaveable via ordinals-Saver).
- Tab-Wechsel setzt `typeFilter` mit zurück (wie `query`/`submitted`).
- **Filter-Icon** (`Icons.Outlined.FilterList`) im `CenterEnd`-Cluster, **nur** `TAB_LIBRARY`,
  links neben dem bestehenden Sync-Icon. Position via `onGloballyPositioned` für den Popup-Anker.
- Klick öffnet `TypeFilterMenu`. Auswahl mutiert `typeFilter`.
- `EinkSearchBar` bekommt `onClear = { query=""; submitted="" }` und `chips = {}` mit je einem
  Chip pro aktivem Typ (Label via `localizedContentType`), ✕ entfernt den Typ aus `typeFilter`.
  Chips nur im Browse-Tab anzeigen.
- `typeFilter` an `LibraryScreen` durchreichen.

### E. `LibraryScreen` / `BrowseTab` — Filter-Logik & Platzhalter

Neuer Param `typeFilter: Set<ContentType>`.

**Reine Filterfunktion** (testbar, ausgelagert):
```
fun filterSeries(series: List<Series>, query: String, types: Set<ContentType>): List<Series> =
    series.filter { s ->
        (query.isBlank() || s.title.contains(query, ignoreCase = true)) &&
        (types.isEmpty() || s.contentTypeOverride in types)
    }
```

Empty-State-Logik im `Content`-Zweig, wenn `shown` leer:
- `typeFilter` **nicht** leer → ausführlicher Erklär-Platzhalter (`filterTypePlaceholder`).
- sonst `query` nicht leer → bestehendes `searchNoResults`.

`NoServer` bleibt unverändert (`libraryEmpty` = „Server verbinden"), auch bei aktivem Filter.

### F. i18n (`Strings.kt`, DE + EN)

Neue Keys:
- `clearSearch` — ContentDescription des ✕-Buttons („Suche zurücksetzen" / „Clear search").
- `filterByType` — ContentDescription/Label des Filter-Icons & Menü-Titel
  („Nach Werk-Typ filtern" / „Filter by type").
- `filterTypePlaceholder` — der ausführliche Erklärtext (mehrzeilig, ohne Doku-Bedarf), sinngemäß:

  > Keine Werke mit dem gewählten Typ gefunden.
  >
  > Die App muss wissen, welcher Lesemodus zu welchem Werk gehört (Manga, Comic, Webtoon, Roman).
  > Lege den Typ entweder gesammelt im Tab **Bibliotheken** fest (Bibliothek bearbeiten →
  > Werk-Typ wählen — gilt für alle Werke darin) oder einzeln in den Serien-Details über das
  > **Drei-Punkte-Menü** oben rechts → „Typ zuweisen".

  EN analog.

## Tests

- **Unit (pure):** `filterSeries` — leerer Filter (alle), Einzeltyp, Mehrfachtyp,
  alle-unbekannt→leer, getypt-aber-kein-Match→leer, Titel+Typ kombiniert.
- **E2E:** Emulator `eink_test` gegen lokale Test-Komga: Filter setzen → Grid filtert;
  Filter auf Typ ohne Treffer → Erklär-Platzhalter; ✕ im Suchfeld → Reset; Filter-Chip-✕ → Typ weg.
  Screenshot als Beweis.

## Nicht im Scope (YAGNI)

- Filter nach Status/Genre/Sprache.
- Filter im Einstellungs- oder Bibliotheken-Tab.
- Persistenz des Filters über App-Neustart hinaus (nur über `rememberSaveable`/Tab-Leben).

## Invarianten-Check

- Quellen-agnostisch: Filter nutzt nur das Domain-Feld `contentTypeOverride`, kein Komga-Wissen.
- E-Ink: flaches Popup, Hairline-Chips, keine Animationen, `AnchoredMenuPopup` statt Material-Dropdown.
- Kein neuer Cross-Modul-Import; alles in `app` + reine Filterfunktion.
