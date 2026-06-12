# D1: Detail-Routen modular — `detail`-Region-Slot — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt D1** der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`

> **Self-contained:** Frische Session ohne Gesprächskontext kann das umsetzen. Vor dem Bauen lesen:
> `komga-plugins`-Skill (Capability-Rezept, Säule 3), `architecture-seams.md` (UI-Slot-Naht),
> `shared-structure-before-variants.md` (D1 extrahiert geteiltes Gerüst — genau diese Regel).
> **Direktes Vorbild:** die sechs gebauten Region-Slots in
> `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt` (zuletzt `overlay`, `git show 7f0b654`).
> D1 ist dasselbe Slot-Muster für eine **siebte** Region — diesmal das Vollbild-Detail-**Gerüst**.

## 1. Ziel

Das **geteilte Vollbild-Detail-Gerüst** der Detail-Routen (`Scaffold` + Header über den `header`-Slot +
optionaler Snackbar + scrollender Body) hinter eine benannte, adressierbare **`detail`-Region** legen.
Heute baut **jede** Detail-Route dieses Gerüst selbst (Duplikat); D1 zentralisiert es **und** macht es
austauschbar. Damit ist nicht nur der Header (schon Slot), sondern die **Detail-Seiten-Anordnung**
pack-tauschbar — der erste Schritt zum späteren `DetailShell` (Roadmap: »Region-komponiert, erweiterbar
zu DetailShell«).

## 2. Scope (User-Entscheidung 2026-06-12): zwei Routen sauber, CollectionDetail später

- **In D1:** `SeriesDetailScreen` + `GroupBrowseRoute`. Beide nutzen heute schon den `header`-Slot mit der
  Standard-Signatur `(title, onBack, actions)` → sauberer, risikoarmer Schnitt.
- **NICHT in D1 → Folge-Task D1.1:** `CollectionDetailScreen`. Es bricht das Muster (eigener `TopAppBar`
  **mit Such-Feld im Header** statt `header`-Slot; eigene `MemberTile` statt `tiles`-Slot). Sein
  Such-im-Header ist eine eigene UX-Entscheidung und wird separat behandelt. **D1 fasst CollectionDetail
  nicht an.**

## 3. Der Schnitt — das Gerüst, nicht der Body-Inhalt

**`detail` slot-ifiziert das Scaffold-Gerüst, nicht den Body-Inhalt.**

- **Pack-gewählt (austauschbar):** *wie* die Detail-Seite gerahmt ist — Scaffold-Struktur, wo der Header
  sitzt, wie Snackbar/Body angeordnet sind. (Ein Tablet-Pack könnte später Master-Detail bauen.)
- **Host-/Route-gebaut (bleibt):** der konkrete Body (Hero-Karte, Kapitel-/Serien-Grid, viewMode-Toggle,
  Dialoge, ViewModel/State, Navigation). Diese unterscheiden sich echt je Route und bleiben im jeweiligen
  Screen. Der Body kommt als **host-gebautes** `@Composable (PaddingValues) -> Unit` in die Surface (Pack
  platziert es, baut es nie neu — »UI neu, Kernlogik gleich«).

> **Hero/Grid-Anordnung bleibt im Body (bewusst, YAGNI):** Hero als eigenständiges Surface-Stück + Grid-
> Ownership wäre die `DetailShell`-Ausbaustufe. D1 macht zuerst das geteilte *Gerüst* modular — das ist das
> real Duplizierte. SeriesDetails Hero bleibt ein Full-Span-Grid-Item *im* Body.

## 4. Ausgangslage (Ist, verifiziert)

- **`app/.../ui/series/SeriesDetailScreen.kt`** (`fun SeriesDetailScreen(onBack, onOpenBook, viewModel,
  collectionsVm)`, Z. 89): baut
  ```kotlin
  Scaffold(
      topBar = { LocalResolvedSlots.current.header(title, onBack) { /* Bookmark + Home */ } },
      snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding -> when (state) { Loading/NoServer/Error/Content -> … SeriesDetailContent(…, Modifier.fillMaxSize().padding(padding)) } }
  ```
  Plus `TypeMenu`/`AddToCollectionSheet`-Overlays **nach** dem Scaffold (bleiben im Screen). `title`
  hängt vom State ab.
- **`app/.../ui/groups/GroupBrowseRoute.kt`** (`fun GroupBrowseRoute(shelfId, onBack, onOpenSeries,
  viewModel)`, Z. 32): baut
  ```kotlin
  Scaffold(topBar = { LocalResolvedSlots.current.header(title, onBack) { IconButton(Refresh) } }) { padding ->
      when (state) { Loading/NoServer/Error/Content -> LazyVerticalGrid(Fixed(3), …padding…) { items { SeriesTile(...) } } }
  }
  ```
  Kein Snackbar.
- **`UiSlots.kt`** — trägt header+homeHeader+dialog+settings+tiles+overlay; importiert schon aus mehreren
  ui-Paketen → Import aus `ui.series`/einem neuen Ort regulär. Muster: `UiSlotPack`/`ResolvedSlots`/
  `DefaultSlots`/pure `UiSlots.resolve`/`LocalResolvedSlots`.
- **`SlotFallbackTest.kt`** — pure Resolver-Tests (`assertSame`), je 2 pro Region, **echte Umlaute**.

## 5. Design

### 5.1 Capability-Surface `DetailScaffoldState`

Neue Datei `app/src/main/kotlin/com/komgareader/app/ui/detail/DetailScaffold.kt` (neues Paket `ui.detail`).

```kotlin
/** Capability-Surface des Vollbild-Detail-Gerüsts: Titel + Zurück + Header-Aktionen (→ header-Slot),
 *  optionaler Snackbar-Host, und der host-gebaute Body. Ein [DetailSlot]-Pack rahmt diese Stücke; den
 *  Body (Hero/Grid/Dialoge/State) baut der Host, das Pack platziert ihn nur. E-Ink-Invarianten + der
 *  Header-Look sind über header-Slot/Host erzwungen, nicht Teil hiervon. */
data class DetailScaffoldState(
    val title: String,
    val onBack: () -> Unit,
    val actions: @Composable RowScope.() -> Unit = {},
    val snackbarHost: @Composable () -> Unit = {},
    val content: @Composable (padding: PaddingValues) -> Unit,
)
```

### 5.2 `DefaultDetailScaffold` — der verbatim extrahierte Renderer

```kotlin
@Composable
fun DefaultDetailScaffold(state: DetailScaffoldState) {
    Scaffold(
        topBar = { LocalResolvedSlots.current.header(state.title, state.onBack, state.actions) },
        snackbarHost = state.snackbarHost,
    ) { padding -> state.content(padding) }
}
```
Das ist exakt das heute in beiden Routen duplizierte Gerüst (Scaffold + header-Slot + Snackbar + padding-
durchgereichter Body). **Verhaltensgleich.**

> **`header`-Signatur prüfen:** der `header`-Slot ist `(title, onBack, actions: @Composable RowScope.() ->
> Unit)`. `DefaultDetailScaffold` reicht `state.actions` als drittes Argument durch. (In den heutigen
> Routen steht das Actions-Lambda als Trailing-Lambda — semantisch identisch.)

### 5.3 Slot-Vertrag in `UiSlots.kt` (additiv)

```kotlin
typealias DetailSlot = @Composable (state: DetailScaffoldState) -> Unit
```
- `UiSlotPack(header, homeHeader, dialog, settings, tiles, overlay, detail: DetailSlot? = null)`
- `ResolvedSlots(…, detail: DetailSlot)`
- `UiSlots.resolve`: `detail = pack.detail ?: DefaultSlots.detail`
- `DefaultSlots.detail: DetailSlot = { state -> DefaultDetailScaffold(state) }` (Imports `DetailScaffoldState`,
  `DefaultDetailScaffold` aus `ui.detail`)
- Klassen-KDoc oben: `detail` als siebte Region ergänzen (die ersten sechs sind Chrome-Regionen; `detail`
  ist das Vollbild-Detail-Gerüst — kurz einordnen, dass es über den `header`-Slot komponiert).

### 5.4 Routen umstellen

**SeriesDetailScreen:** das `Scaffold(topBar=…header…, snackbarHost=…){ padding -> when(state){…} }`
ersetzen durch:
```kotlin
val title = (state as? SeriesDetailUiState.Content)?.seriesTitle ?: "Serie"
LocalResolvedSlots.current.detail(
    DetailScaffoldState(
        title = title,
        onBack = onBack,
        actions = { /* Bookmark-IconButton (wie heute) + Home-IconButton (LocalOnHome) */ },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { padding -> when (val current = state) { Loading/NoServer/Error/Content -> … } },
    ),
)
```
Die `TypeMenu`/`AddToCollectionSheet`-Overlays bleiben **unverändert nach** dem `detail`-Aufruf. Die
Actions-Logik (inAnyCollection-Berechnung etc.) wandert 1:1 ins `actions`-Lambda.

**GroupBrowseRoute:** analog — `actions = { IconButton(onClick={viewModel.refresh()}){Icon(Refresh)} }`,
`content = { padding -> when(state){ … LazyVerticalGrid(...) } }`, kein `snackbarHost`.

> **`title`-Strings:** Bei SeriesDetail wird `"Serie"` bzw. bei Group der Shelf-Name verwendet — **exakt
> die heutigen Werte** übernehmen (kein neuer i18n-Key, kein Verhaltenswechsel).

### 5.5 E-Ink-Invarianten (host-erzwungen)

`detail` rendert nur Struktur; Header-Look/Bewegung bleiben am `header`-Slot bzw. `LocalEinkMode`/
`LocalDesignTokens` — host-erzwungen. Keine Animation im Gerüst.

## 6. Swap-Beweis (Debug-Preview, keine Nutzer-Einstellung)

`app/src/debug/kotlin/com/komgareader/app/ui/detail/DetailSlotPreview.kt`: ein `AlternativeDetailScaffold`
(`DetailSlot`) mit **anderer Rahmung** — z. B. Body **ohne** Material-Scaffold, eigener schlanker
Titelbalken statt header-Slot, oder Snackbar weggelassen — plus `@Preview` mit
`LocalResolvedSlots provides UiSlots.resolve(UiSlotPack(detail = AlternativeDetailScaffold))` über einen
`DefaultDetailScaffold`-Aufruf mit Fake-`DetailScaffoldState`. Weggelassene Fähigkeiten im KDoc
dokumentieren (R1–R4-Lehre). Vorlage: `app/src/debug/.../ui/reader/OverlaySlotPreview.kt`.

## 7. Tests

- **Pure (`SlotFallbackTest.kt` erweitern):** zwei Tests analog overlay — fehlender `detail`-Slot fällt auf
  `DefaultSlots.detail` zurück (`assertSame`); gelieferter überschreibt. **Echte Umlaute** (`fällt`/`zurück`/
  `überschreibt`). KDoc-Kopf nachziehen (sieben Regionen).
- **E2E (Emulator `eink_test`, 1264×1680, Test-Komga verbunden):**
  - Bibliothek → Serie öffnen: SeriesDetail erscheint **unverändert** (Header mit Titel/Bookmark/Home,
    Hero-Karte, Kapitel-Grid, Lesen/Download). Bookmark + Home funktionieren.
  - Eine Gruppe öffnen (Bibliotheken-Tab/Gruppen): GroupBrowse zeigt **unverändert** Header (Titel +
    Refresh) + 3-Spalten-Serien-Grid. Refresh funktioniert.

## 8. Akzeptanz

- `DetailScaffoldState` + `DefaultDetailScaffold` in `ui.detail`; `detail`-Region additiv in
  `UiSlots.kt` (sechs bestehende Regionen unberührt).
- `SeriesDetailScreen` + `GroupBrowseRoute` bauen das Gerüst **nicht mehr selbst**, sondern über
  `LocalResolvedSlots.current.detail(...)`; Body/Hero/Grid/Overlays/State unverändert. Signaturen der
  beiden Screens unverändert.
- `DefaultDetailScaffold` verhaltens-/pixelgleich (E2E gezeigt). Pure Fallback-Tests grün. Swap-Preview
  kompiliert. **CollectionDetailScreen unangetastet.**
- `architecture-seams.md` (UI-Slot-Naht: siebte Region `detail`) + `big-picture-and-goals.md` Roadmap
  (D1 teil-erledigt, D1.1 = CollectionDetail offen) + Memory-Roadmap im selben Commit nachgezogen.

## 9. Nicht in D1 (YAGNI)

CollectionDetail (→ D1.1). Kein `DetailShell`-Pack. Kein Hero-als-eigenes-Surface-Stück, keine Grid-
Ownership im Scaffold (→ spätere DetailShell-Stufe). Kein User-Override, kein `ui-api`-Modul. Keine
Änderung an `SeriesDetailContent`/Hero/`ChapterTile`/`SeriesTile`/ViewModels.

## Bezug

Roadmap `2026-06-12-complete-ui-modularity-roadmap.md` · `architecture-seams.md` (UI-Slot-Naht) ·
`shared-structure-before-variants.md` · `komga-plugins`-Skill · Vorbild-Bauten R1–R4 (zuletzt `overlay`,
7f0b654). Folge-Task: **D1.1** (CollectionDetail: Such-Header + MemberTile in die Naht). Spätere Ausbaustufe:
**DetailShell** (Hero/Grid als arrangierbare Stücke, Master-Detail auf Tablet).
