# D1.1: CollectionDetail in die `detail`-Naht + Header-Such-Capability — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt D1.1** (Folge von D1) der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`

> **Self-contained:** Frische Session ohne Gesprächskontext kann das umsetzen. Vor dem Bauen lesen:
> `komga-plugins`-Skill (Capability-Rezept), `architecture-seams.md` (UI-Slot-Naht), die **D1-Spec**
> `2026-06-12-detail-routes-modular-D1.md` (der `detail`-Slot, Commit 086f612) und `HomeHeader.kt`
> (`HomeHeaderSearch` als Vorbild für die Such-Capability). Direktes Vorbild für das Slot-Muster: die
> sieben gebauten Regionen in `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt`.

## 1. Ziel & User-Entscheidungen (2026-06-12)

`CollectionDetailScreen` ist die letzte Detail-Route, die das Muster bricht: eigener `TopAppBar` (statt
`header`-Slot) mit **Titel↔Such-Feld-Umschaltung** (Lupe-Toggle) + Aktionen (Add/Sync/Delete), und eine
eigene `MemberTile`. D1.1 bringt sie in die `detail`-Naht.

**User-Entscheidung:** Die `header`-Region wird zu einer **Capability-Surface `HeaderState` mit optionaler
Such-Capability** ausgebaut — **abwärtskompatibel**: die bestehenden 4 Produktiv-Call-Sites von
`current.header(title, onBack) { actions }` bleiben über einen **dünnen Kompat-Pfad (Extension)**
**unverändert**. CollectionDetail nutzt dann die Such-Capability (Titel↔Suchfeld wie heute) **über** den
`header`-Slot, eingebettet im `detail`-Slot.

## 2. Scope

- **In D1.1:** (a) `header`-Region → `HeaderState`-Surface mit optionaler `HeaderSearch` (abwärtskompatibel);
  (b) `detail`-Gerüst kann die Suche durchreichen; (c) `CollectionDetailScreen` auf `detail`+`header`-Slot.
- **NICHT in D1.1:** `MemberTile` bleibt eine eigene Kachel (Collection-Member = Serie **oder** Buch, mit
  Entfernen-Button — kein `SeriesTile`). Eine `member`-tiles-Region ist ein späteres, eigenes Sub-Projekt.
  Kein `DetailShell`, kein User-Override, kein `ui-api`-Modul.

## 3. Ausgangslage (Ist, verifiziert)

- **`UiSlots.kt`** — `HeaderSlot = @Composable (title: String, onBack: (()->Unit)?, actions: @Composable
  RowScope.() -> Unit) -> Unit` (3-arg). `DefaultSlots.header` ruft `StandardTopAppBar(title, onBack,
  actions)`. `ResolvedSlots(val header: HeaderSlot, …)`. Pack-Feld `UiSlotPack(val header: HeaderSlot? =
  null, …)`. `UiSlots.resolve`: `header = pack.header ?: DefaultSlots.header`.
- **4 Produktiv-Call-Sites** von `current.header(...)` (alle 3-arg-Form, **müssen unverändert bleiben**):
  `EinkComponents.kt:381` (SubPageScaffold), `SettingsRoute.kt:36`, `DetailScaffold.kt:32`
  (das `detail`-Gerüst), `HeaderSlotPreview.kt:51` (debug). Plus die D1-`DefaultDetailScaffold`.
- **`StandardTopAppBar`** (`app/.../ui/components/StandardTopAppBar.kt:23`): `(title: String, onBack:
  (()->Unit)? = null, actions: @Composable RowScope.() -> Unit = {})` → `TopAppBar{ Text(title);
  navIcon=Back; actions }`.
- **`CollectionDetailScreen.kt`** (`fun CollectionDetailScreen(collectionId, onBack, onOpenSeries,
  viewModel, libraryVm)`, Z. 80): `Column { CollectionDetailHeader(...); LazyVerticalGrid(Fixed(3)){ items
  { MemberTile(...) } } }`. `CollectionDetailHeader` (privat, Z. 215) ist ein `TopAppBar`, der bei
  `searchActive` ein Suchfeld statt Titel zeigt (`onOpenSearch`/`onCloseSearch`/`query`/`onQueryChange`),
  links Back, rechts Aktionen (Add nur bei `isSeries`, Sync mit Anchor, Delete). Popups `showSyncPanel`/
  `showAdd`/`showDelete` + `collection==null`-Early-Return bleiben.
- **`DetailScaffoldState`** (`app/.../ui/detail/DetailScaffold.kt`, aus D1): `(title, onBack, actions={},
  snackbarHost={}, content)`. `DefaultDetailScaffold` ruft `current.header(state.title, state.onBack,
  state.actions)`.
- **`HomeHeaderSearch`** (`HomeHeader.kt:38`) — Vorbild-Surface (query/onQueryChange/onSubmit/placeholder/…).
- **`SlotFallbackTest.kt`** — pure Resolver-Tests, je 2 pro Region; header-Tests prüfen `resolved.header`.

## 4. Design

### 4.1 Such-Capability + Header-Surface (in `UiSlots.kt`)

```kotlin
/** Optionale Such-Fähigkeit eines Headers: Lupe-Toggle (onOpen/onClose) + Suchfeld-Zustand. `active`
 *  → Header zeigt das Suchfeld statt des Titels; sonst eine Lupe vor den [HeaderState.actions]. */
data class HeaderSearch(
    val active: Boolean,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val placeholder: String? = null,
)

/** Capability-Surface der Header-Region: Titel + optionaler Zurück + Aktionen + optionale Suche. Ein
 *  Pack rendert daraus die Top-Leiste. Der Look/E-Ink bleibt host-erzwungen. */
data class HeaderState(
    val title: String,
    val onBack: (() -> Unit)?,
    val actions: @Composable RowScope.() -> Unit = {},
    val search: HeaderSearch? = null,
)
```

### 4.2 Slot-Signatur wird HeaderState-basiert — abwärtskompatibel

```kotlin
typealias HeaderSlot = @Composable (state: HeaderState) -> Unit
```
- `UiSlotPack(val header: HeaderSlot? = null, …)` — **Pack-Feldname bleibt `header`** (ein Pack liefert
  einen HeaderState-Renderer).
- `ResolvedSlots(val headerSlot: HeaderSlot, …)` — die resolved-Renderer-Property wird **`headerSlot`**
  genannt (nicht `header`), damit der **Kompat-Extension** `header(title, onBack, actions)` ohne
  Property-Kollision greift. Die übrigen sechs Felder (homeHeader/dialog/settings/tiles/overlay/detail)
  unverändert.
- `UiSlots.resolve`: `headerSlot = pack.header ?: DefaultSlots.header`.
- `DefaultSlots.header: HeaderSlot = { state -> DefaultHeader(state) }`.

### 4.3 Dünner Kompat-Pfad (Extension) — hält die 4 Call-Sites verbatim

```kotlin
/** Kompat-Form für Header ohne Suche: baut die [HeaderState]-Surface und ruft den Slot. Hält die
 *  bestehenden `current.header(title, onBack){ actions }`-Aufrufe unverändert. */
@Composable
fun ResolvedSlots.header(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) = headerSlot(HeaderState(title, onBack, actions))
```
Damit lösen `current.header("…", onBack) {}` und `current.header(t, b, a)` auf diese Extension auf —
**keine Textänderung** an den 4 Call-Sites + Preview. Der search-fähige Pfad ruft `current.headerSlot(
HeaderState(…, search = …))` direkt (oder über das `detail`-Gerüst, §4.5).

### 4.4 `DefaultHeader` — Such-fähiger Onyx-Renderer (in `UiSlots.kt` oder `StandardTopAppBar.kt`)

```kotlin
@Composable
fun DefaultHeader(state: HeaderState) {
    val search = state.search
    if (search != null && search.active) {
        // TopAppBar: Back links, Suchfeld (TextField, autofocus, onQueryChange) statt Titel,
        // Schließen-Aktion (search.onClose) — analog dem heutigen CollectionDetailHeader-Such-Modus.
    } else {
        StandardTopAppBar(
            title = state.title,
            onBack = state.onBack,
            actions = {
                if (search != null) IconButton(onClick = search.onOpen) { Icon(AppIcons.Search, …) }
                state.actions()
            },
        )
    }
}
```
> Den Such-Feld-Block aus dem heutigen `CollectionDetailHeader` (Z. ~237 ff., `searchActive`-Zweig:
> `FocusRequester`, „schließt erst bei erneutem Fokusverlust") **verbatim übernehmen** — gleiches
> Verhalten, nur jetzt zentral im `DefaultHeader`. Kein neuer i18n-Key (vorhandene Such-Strings nutzen).

### 4.5 `detail`-Gerüst reicht die Suche durch

`DetailScaffoldState` bekommt ein optionales `search`-Feld:
```kotlin
data class DetailScaffoldState(
    val title: String,
    val onBack: () -> Unit,
    val actions: @Composable RowScope.() -> Unit = {},
    val search: HeaderSearch? = null,            // NEU
    val snackbarHost: @Composable () -> Unit = {},
    val content: @Composable (padding: PaddingValues) -> Unit,
)
```
`DefaultDetailScaffold`: `topBar = { LocalResolvedSlots.current.headerSlot(HeaderState(state.title,
state.onBack, state.actions, state.search)) }` (statt der 3-arg-Kompat-Form — so fließt `search` mit).
SeriesDetail/GroupBrowse lassen `search = null` (Verhalten unverändert).

### 4.6 `CollectionDetailScreen` auf `detail`+`header`

Das `Column { CollectionDetailHeader(...); LazyVerticalGrid(...) }` ersetzen durch:
```kotlin
LocalResolvedSlots.current.detail(
    DetailScaffoldState(
        title = collection.name,
        onBack = onBack,
        actions = { /* Add (nur isSeries) · Sync (mit Anchor) · Delete — 1:1 aus CollectionDetailHeader */ },
        search = HeaderSearch(
            active = searchActive, query = query, onQueryChange = { query = it },
            onOpen = { searchActive = true }, onClose = { searchActive = false; query = "" },
            placeholder = /* heutiger Such-Placeholder */,
        ),
        content = { padding -> LazyVerticalGrid(Fixed(3), …padding…) { items(members){ MemberTile(...) } } },
    ),
)
```
Das private `CollectionDetailHeader` **entfällt** (sein Titel/Back/Such-Verhalten lebt jetzt im
`DefaultHeader`; seine Aktionen wandern ins `actions`-Lambda). Popups (`showSyncPanel`/`showAdd`/
`showDelete`) + `collection==null`-Early-Return + `members`-Filterung bleiben **unverändert** nach bzw. vor
dem `detail`-Aufruf. `MemberTile` unverändert. Screen-Signatur unverändert.

> **Sync-Anchor:** der Sync-Popup hängt an der Position des Sync-Icons (`onSyncAnchor: (IntOffset)`). Das
> Icon liegt jetzt im `actions`-Lambda — der `onGloballyPositioned`/Anchor-Mechanismus zieht 1:1 mit um.

### 4.7 E-Ink-Invarianten (host-erzwungen)

Kein neuer Bewegungs-/Akzent-Pfad. Das Suchfeld erscheint instant (kein Fade); `DefaultHeader` nutzt die
bestehenden Tokens. Host-erzwungen, nicht Pack-Sache.

## 5. Swap-Beweis / Preview

`HeaderSlotPreview.kt` (existiert) auf die neue `HeaderState`-Form ziehen: der Alternativ-Header nimmt jetzt
`HeaderState`. **Zusätzlich** einen Preview-Fall mit `search = HeaderSearch(active=true,…)` zeigen (beweist
die Such-Capability). Nur Debug. (Falls einfacher: bestehende Preview minimal anpassen + einen zweiten
`@Preview` für den Such-Zustand.)

## 6. Tests

- **Pure (`SlotFallbackTest.kt`):** die zwei header-Tests auf `resolved.headerSlot` umstellen (statt
  `resolved.header`), `assertSame(DefaultSlots.header, UiSlots.resolve(UiSlotPack()).headerSlot)` + Override.
  Echte Umlaute. Die anderen sechs Regionen unberührt. (Optional: ein Test, dass die Kompat-Extension nicht
  nötig ist für assertSame — nicht erzwingen.)
- **E2E (Emulator `eink_test`, Test-Komga verbunden):**
  - Eine Sammlung (Sammlungen-Tab) öffnen: Header = Standard-Leiste (Titel/Back/Add/Sync/Delete) + Lupe;
    Member-Grid darunter **unverändert**. Lupe tippen → Suchfeld erscheint, Tippen filtert die Member,
    Schließen kehrt zum Titel zurück. Add/Sync/Delete funktionieren.
  - Eine Serie + eine Gruppe öffnen: SeriesDetail/GroupBrowse **unverändert** (search=null, Kompat-Pfad).

## 7. Akzeptanz

- `HeaderState`/`HeaderSearch` + `DefaultHeader` (such-fähig); `HeaderSlot` HeaderState-basiert; Kompat-
  Extension `ResolvedSlots.header(title,onBack,actions)`. Die 4 Produktiv-Call-Sites + Preview **textlich
  unverändert**.
- `DetailScaffoldState.search` durchgereicht; `DefaultDetailScaffold` ruft `headerSlot(HeaderState(...))`.
  SeriesDetail/GroupBrowse verhaltensgleich.
- `CollectionDetailScreen` nutzt `detail`+`header`-Slot; `CollectionDetailHeader` entfällt; Popups/Filter/
  MemberTile unverändert; Such-Verhalten (Titel↔Feld) verhaltensgleich. Screen-Signatur unverändert.
- Pure Tests grün. Compile grün (alle header-Consumer). E2E gezeigt.
- `architecture-seams.md` (header-Region jetzt HeaderState-Surface mit optionaler Suche; `detail` reicht
  Suche durch) + `big-picture-and-goals.md` (D1 **vollständig**: alle drei Detail-Routen modular) + Memory-
  Roadmap im selben Commit nachgezogen.

## 8. Nicht in D1.1 (YAGNI)

`MemberTile` → eigene spätere `member`-tiles-Region. Kein `DetailShell`, kein Hero-als-Surface, kein User-
Override, kein `ui-api`-Modul. Keine Änderung an HomeHeader/HomeHeaderSearch (eigene Region).

## Bezug

Roadmap `…complete-ui-modularity-roadmap.md` · D1-Spec `…detail-routes-modular-D1.md` (086f612) ·
`architecture-seams.md` · `komga-plugins`-Skill · `HomeHeader.kt` (`HomeHeaderSearch`-Vorbild). Danach: **C1**
(Reader-Chrome komplett modular).
