# Retro: UI-Arbeit (Settings/Tabs + Collections-Detail)

Datum: 2026-06-10 · Branch `feat/reader-ui-polish`
Zweck: festhalten, **was** wir gebaut/korrigiert haben und **welche Pattern/Anti-Pattern** dabei
sichtbar wurden — als Grundlage, um die Lücken im Skill `komga-eink-ui-polish` zu schließen, damit
solche UI **beim ersten Mal** richtig herauskommt statt in vielen kleinen Korrekturschritten.

---

## 1. Was wir getan haben (aus der Git-Historie)

| Commit | Inhalt |
|---|---|
| `c469747` | Settings/Tabs-Feinschliff: Quelltyp→`SegmentedChoiceRow`, ChoiceRow-Häkchen→Akzent, „Über"-Zeilen; Material-`Checkbox`/`FilterChip`→`ChoiceRow`, Library-Empty/Error→i18n+`EinkOutlinedButton`, `TypeChip` 1dp→hairline, Grid-Gutter→`tileGap`, `TileTitleBand` extrahiert |
| `beb2b52` | Merge `feat/ui-platform-skins` (Collections-Feature); Home-Button fertig verdrahtet (`LocalOnHome` — skins referenzierte ihn + `s.navHome`, ohne sie zu definieren → kompilierte selbst nicht) |
| `b8a2f66` | Novel-Margins 20/50/100 → 12/25/50 (WIDE kollabierte still auf crengine-Default 8) |
| `d6c43ec` | Bookmark gefüllt bei Collection-Mitgliedschaft (`lucideFilled`); `AddToCollectionSheet` staged + Bestätigen/„+"; Novel-Overlay-Reihenfolge + Page-Header/Footer |
| `a4b6b6c` | 3-Ansichts-Toggle (Liste/Kachel/große Kachel) + DE „Sammlung"; `CollageTile`/`CompositeCover`/`EntityListRow` als geteilte Bausteine extrahiert |
| `b4c057a` | Toggle als rotierender TopBar-Button + „+"; Sammlungs-CRUD (umbenennen/löschen); Detail-Werke als Cover-Grid |
| `fe2aeee` | Sammlungs-Detail eigene TopBar (Zurück/Titel/Lupe/+/Sync/Löschen) + „Werke hinzufügen"-Modal + Sync-Kontextfeld |
| `cd7e136` | Detail-Politur: TopBar→`TopAppBar`-DRY, **Multi-Add-Race-Fix** (`addMembers`), **Such-Auf-Bug**, Sync-Panel-Politur, 4er-Grid + feste Höhe |
| `988e2f9` | Suchfelder im Detail → geteilte `EinkSearchBar` (sahen vorher anders aus als der Rest der App) |
| `d2ddb03` | Immer-sichtbares X im Header-Suchfeld schließt die Suche |
| `b969e57` | „Werke hinzufügen"-Modal kompakter: Titelzeile weg, Placeholder trägt die Aussage |

(Davor, aus dem gemergten `ui-platform-skins`-Strang: das ganze Collections-Backend — `source-api`-
Capability, Room v14, `CollectionSyncManager`, Sync gegen echte Komga, etc.)

---

## 2. Anti-Pattern, die wir wiederholt getroffen haben (ehrlich)

Jedes hätte der Skill verhindern sollen; tat er nicht.

**A — Erst dupliziert, dann extrahiert.** `TileTitleBand`, `CollageTile`/`CompositeCover`,
`EntityListRow` wurden **nachträglich** aus Duplikaten gezogen. Such-Feld und TopBar baute ich als
**rohe Eigenbauten** (`OutlinedTextField`, `Column`+`Box`+Hairline) — obwohl `EinkSearchBar` und die
`TopAppBar` des Hauptscreens schon existierten. Symptom direkt vom Nutzer: „die Suchfelder sehen
anders aus als im Rest der App", „die TopBar hat einen Unterstrich, soll DRY zur Main-TopBar sein".

**B — Rohe Material-Controls** trotz „nie nackte Material-Controls": `Checkbox`, `FilterChip`,
`Button`, `OutlinedTextField`-als-Suche.

**C — i18n-Lecks außerhalb sichtbarer Labels:** Snackbar-Strings, „Wiederholen"-Button, „Quelle $id"
inline hartkodiert.

**D — Gemischte Maße:** `TypeChip`-Rand 1.0 dp vs. Hairline 1.5 dp; Grid-Gutter 4 dp (Library) vs.
8 dp (Groups).

**E — Uneinheitliches Auswahl-Signal:** `ChoiceRow`-Häkchen schwarz (`onSurface`), während
Sidebar-Balken + Segmente bereits den Akzent-Token nutzten.

**F — E-Ink-Layout-Stabilität ignoriert:** Das „Werke hinzufügen"-Modal **wuchs beim Öffnen** (Liste
wächst, Cover laden nach) → sichtbare Bewegung/Flash auf E-Ink. Erst feste Gitterhöhe + Rahmen-
Platzhalter machten es ruhig. Nutzer: „beim Laden des Modals gibt es zu viel Animation/Bewegung".

**G — Chrome-Ownership unklar (zwei TopBars):** Das Detail lief im Home-Tab → Home-TopBar **und**
eigene `SubPageScaffold`-TopBar gleichzeitig. Erst Unterdrücken der Host-TopBar löste es.

**H — Fokus-/Such-Overlay-Gotchas:** Der Blur-Close feuerte beim **initialen** Nicht-Fokus → Suche
schloss sofort beim Öffnen. Und: kein immer-sichtbares Schließen-X (EinkSearchBars Clear-X erscheint
nur bei Eingabe).

**I — Concurrency in einer UI-Aktion:** Multi-Select „Werke hinzufügen" rief pro Werk
`viewModelScope.launch { addMember() }` → `addMember` ist read-modify-write → Lost-Update, nur das
letzte blieb. Visuell waren alle markiert. Fix: `addMembers()` in **einer** Coroutine sequentiell.

**J — Zu absolute Skill-Regel:** „eine Variante pro Zweck (Outline), kein Filled/Outlined-Mix" steht
dem berechtigten Wunsch entgegen, ein **gefülltes** Glyph als **Aktiv-Zustand** zu nutzen (Bookmark
gesetzt). Wir brauchten `lucideFilled` — die Regel verbietet es pauschal.

---

## 3. Die Pattern, auf die wir konvergiert sind (Soll-Zustand)

1. **Geteilte Chrome-Bausteine zuerst** — und zwar dieselben wie der Rest der App:
   `EinkSearchBar`, die `TopAppBar` des Hauptscreens (gleiches Muster), `CollageTile`,
   `EntityListRow`, `ChoiceRow`, `SegmentedChoiceRow`, `EinkModal`, `AnchoredMenuPopup`.
2. **Auswahl-Signal immer `LocalDesignTokens.current.accent`** (Häkchen, Balken, Segment, +/✓-Overlay).
3. **Eine Linienstärke (`hairline`), ein Gitter-Gap (`tileGap`)** — auch über zwei verschiedene Grids gleich.
4. **E-Ink-Layout-Stabilität:** Modal/Liste öffnet in Endgröße (feste Höhe), Cover als feste-Rahmen-
   Platzhalter (kein Titel im Picker), `crossfade(false)` — kein Reflow beim Nachladen.
5. **Staged-Auswahl → in einer Transaktion anwenden** (nicht pro Item `launch`en).
6. **Such-als-Overlay:** Lupe ersetzt den Titel durch `EinkSearchBar`; X (immer sichtbar) schließt;
   Blur **mit leerem Feld** schließt (Guard: erst nach erstem Fokus); Auto-Fokus.
7. **Sub-View im Tab:** Host-TopBar unterdrücken, **dieselbe** `TopAppBar` selbst liefern; Titel
   zentriert (`textAlign = Center`).
8. **Status-Kontextfeld** via `AnchoredMenuPopup` (Outside-Tap = schließen).
9. **Kompakte Modals:** Titelzeile optional — bei leerem Titel entfällt der Kopf; Placeholder trägt
   die Aussage.
10. **Gefülltes Glyph = legitimer Aktiv-Zustand** (`lucideFilled`), kein verbotener Stil-Mix.

---

## 4. Lücken im Skill `komga-eink-ui-polish` (was fehlt / zu ergänzen ist)

Mapping Korrektur → fehlende Regel:

1. **„Erst Katalog durchsuchen, nie roh bauen" als harte Regel + Material→Eink-Ersatztabelle.**
   Der Skill listet einen Baustein-Katalog, sagt aber nicht „suche zuerst, baue nie roh", und nennt
   `Checkbox`/`FilterChip`/Such-`OutlinedTextField` gar nicht. → Anti-Pattern A, B.

   | Material/roh | Eink-Ersatz |
   |---|---|
   | `Checkbox` | `ChoiceRow` (Häkchen) |
   | `FilterChip` / `RadioButton` | `SegmentedChoiceRow` bzw. `ChoiceRow` |
   | `OutlinedTextField` als **Suche** | `EinkSearchBar` |
   | `Button` | `EinkOutlinedButton` |
   | eigene TopBar | dieselbe `TopAppBar` wie `HomeScreen` |
   | `AlertDialog` | `EinkModal` |
   | Status-Dropdown | `AnchoredMenuPopup` |

2. **Sektion „E-Ink-Layout-Stabilität" (fehlt komplett).** Modals/Listen in Endgröße öffnen; feste-
   Rahmen-Platzhalter statt nachwachsendem Content; kein Reflow durch ladende Bilder (= Ghosting/
   Flash). Das Pendant existiert im Web-Skill `polished-ui-integration`, im Eink-Skill nicht. → F.

3. **„Ein Auswahl-Signal".** ALLE Selektions-Indikatoren lesen denselben Akzent-Token. Der Skill hat
   die 2-Achsen-Regel, aber nicht „Häkchen = Balken = Segment, ein Token". → E.

4. **i18n-Reichweite erweitern:** explizit Snackbars/Events/Error-States/inline-Labels („Quelle $id")
   einschließen, nicht nur sichtbare Beschriftungen. → C.

5. **Token-Disziplin prüfbar machen:** eine Linienstärke, ein Gitter-Gap; „beide Cover-Grids teilen
   `tileGap`". → D.

6. **Muster „Such-als-Overlay"** dokumentieren (Lupe↔Titel, Always-Close-X, Blur-leer-Guard,
   Auto-Fokus). → H.

7. **Muster „Chrome-Ownership / TopBar-DRY"**: Sub-View im Tab unterdrückt die Host-TopBar und liefert
   dieselbe `TopAppBar`; Titel zentriert. → A, G.

8. **Ausnahme zur Filled/Outline-Regel:** gefülltes Glyph als **Aktiv-Zustand** erlaubt (`lucideFilled`).
   → J.

9. **Muster „Staged-Auswahl → eine Transaktion"** (UI-Aktion-Concurrency: kein per-Item-`launch` bei
   read-modify-write). → I.

10. **Modal-Titel optional** (kompakte Modals; Placeholder kann die Aussage tragen). → b969e57.

---

## 5. Ehrliche Eigenkritik (nicht nur Skill)

Unabhängig von Skill-Lücken: ich habe mehrfach **gebaut, bevor ich den Katalog konsultiert** habe
(rohe Suche/TopBar), **dupliziert statt zuerst extrahiert**, die **Race** eingebaut und die
**Reflow-Unruhe** auf E-Ink nicht antizipiert. Die Skill-Ergänzungen oben sind das Sicherheitsnetz;
die Disziplin „erst geteilten Baustein suchen, dann bauen" ist die Verhaltensänderung.
