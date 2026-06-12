# C1: Reader-Chrome komplett modular — `readerChrome`-Region-Slot — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt C1** der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`

> **Self-contained:** Frische Session ohne Gesprächskontext kann das umsetzen. Vor dem Bauen lesen:
> `komga-plugins`-Skill (Capability-Rezept), `architecture-seams.md` (UI-Slot-Naht **+ Naht B / `Viewer`**),
> `shared-structure-before-variants.md`, `animation-gating.md`/`eink-design-language.md`. **Direktes
> Vorbild:** der `detail`-Slot (`DetailScaffoldState`/`DefaultDetailScaffold`, `git show 086f612`) und der
> `overlay`-Slot (R4, `7f0b654`) in `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt`. C1 ist
> dasselbe Slot-Muster für das **ganze Reader-Gerüst** (`ReaderScaffold`).

## 1. Ziel

Das **gesamte Reader-Chrome** (`ReaderScaffold` — schwarzer Vollbild-Hintergrund, Tap-Zonen, Tap-Hints,
Status-Fuß, persistentBars, Start-Hinweis und der schon slot-ifizierte Overlay) hinter eine benannte,
adressierbare **`readerChrome`-Region** legen. Ein UI-Pack kann das ganze Lese-Chrome neu arrangieren
(andere Tap-Zonen-Anordnung, anderer Footer, anderes Gerüst), **ohne** die Reader-Engines oder die
`Viewer`-Naht (Naht B) anzufassen. Achte/letzte der „komplette UI"-Kernregionen; die **deklarative**
Form (Tap-Zone→Aktion-Deskriptor) + **externer** Pack-Lader kommen **später** (A1/L1/L2).

## 2. Der Schnitt — das ganze Scaffold als EINE Region; Naht B bleibt draußen

**`readerChrome` slot-ifiziert das ganze `ReaderScaffold`** (Variante a). Konsistent mit `detail` (ganzes
Gerüst als eine Region). Der Default-Renderer = der heutige `ReaderScaffold`-Body verbatim.

**Der entscheidende Schnitt — `Viewer` (Naht B) gehört NICHT in die Pack-Surface.** `ReaderScaffold`
benutzt `chrome: Viewer` heute **ausschließlich** für (1) `chrome.chromeVisible.collectAsState()` und
(2) `chrome.toggleChrome()` (Mitte-Tap). `refreshScheduler`/`navigateTo`/`onPageSettled` werden in
`ReaderScaffold` **nicht** angefasst (die leben in den Readern/`EinkReaderEffect`). Darum trägt die
Capability-Surface die **abgeleiteten** Stücke `chromeVisible: Boolean` + `onToggleChrome: () -> Unit` —
**nicht** den `Viewer`. So bleibt Naht B vollständig aus der austauschbaren Surface (ein Chrome-Pack kann
weder den Refresh-Scheduler noch die Engine-Navigation berühren — host-/Core-erzwungen).

- **Host-erzwungen (bleibt, NICHT in der Surface):** `Viewer`/`RefreshScheduler` (Naht B), die
  E-Ink-Scrim-Policy (`readerOverlayScrim`/`LocalEinkMode`), die Animation-Gating-Entscheidungen.
- **Pack-gewählt (austauschbar, in der Surface):** Anordnung/Look des Chrome aus den benannten Stücken.

## 3. Ausgangslage (Ist, verifiziert)

- **`app/.../ui/reader/ReaderScaffold.kt`** (Z. 42-126): 
  ```kotlin
  @Composable fun ReaderScaffold(
      chrome: Viewer, title: String, onBack/onHome/onSettings/onPrev/onNext: () -> Unit,
      modifier: Modifier = Modifier, background: Color = Color.Black,
      actions: @Composable RowScope.() -> Unit = {}, tapModifier: Modifier? = null,
      footer: (@Composable BoxScope.() -> Unit)? = null, persistentBars: (@Composable BoxScope.() -> Unit)? = null,
      showTapZoneHints: Boolean = tapModifier == null, content: @Composable () -> Unit,
  )
  ```
  Body: `val chromeVisible by chrome.chromeVisible.collectAsState()`; `Box(modifier.fillMaxSize().background(
  background)) { content(); Tap-Zonen(default Drittel: onPrev/onNext/chrome.toggleChrome ODER tapModifier);
  persistentBars?.invoke(this); if(chromeVisible&&showTapZoneHints) ReaderTapZoneHints(); if(chromeVisible){
  with(this){ LocalResolvedSlots.current.overlay(ReaderOverlayState(title,onBack,onHome,onSettings,actions)) } };
  if(footer!=null&&chromeVisible) footer(); ReaderStartHint(visible = startHintVisible && !chromeVisible) }`.
  **`chrome` wird NUR für `chromeVisible` + `toggleChrome` benutzt** (per `grep` verifizieren — kein
  `refreshScheduler`/`navigateTo`/`onPageSettled` im Scaffold).
- **`Viewer`** (`app/.../ui/reader/Viewer.kt`): `interface Viewer { val chromeVisible: StateFlow<Boolean>;
  val refreshScheduler: RefreshScheduler; fun toggleChrome(); fun navigateTo(page); fun onPageSettled(page) }`.
  **Naht B — C1 fasst ihn NICHT an.**
- **5 Reader rufen `ReaderScaffold(chrome=…, title=…, …)`** (`PagedReaderScreen`/`WebtoonReaderScreen`/
  `ComicReaderScreen`/`NovelReaderScreen`/`EpubReaderScreen`, je via `ReaderRoute.kt`). Unterschiede:
  Paged/Epub default Tap-Zonen + `footer=ReaderStatusBar`; Webtoon `tapModifier`=Frame-Sprung + footer;
  Comic `tapModifier`=Modifier (leer, Panel-Hit-Test innen im content) + Panel-Toggle-`actions`; Novel
  `background=White` + `persistentBars`=Page-Header/Footer + TOC/Such/Typo-`actions`.
- **`ReaderChrome.kt`-Helfer** (`ReaderStatusBar`/`ReaderTapZoneHints`/`ReaderStartHint`/`ReaderInfoBar`/
  `readerOverlayScrim`/`DefaultReaderOverlay`): bleiben **unverändert** (vom Default genutzt bzw. als
  reader-spezifische Lambdas durchgereicht).
- **`UiSlots.kt`** — sieben Regionen (header/homeHeader/dialog/settings/tiles/overlay/detail).
- **`SlotFallbackTest.kt`** — pure Resolver-Tests, je 2 pro Region, echte Umlaute.

## 4. Design

### 4.1 Capability-Surface `ReaderScaffoldState` (in `ReaderScaffold.kt`)

```kotlin
/** Capability-Surface des ganzen Reader-Chrome: die host-gebauten Stücke, die ein [ReaderChromeSlot]-Pack
 *  arrangiert. Trägt NICHT den [Viewer] (Naht B) — nur die abgeleiteten `chromeVisible`/`onToggleChrome`.
 *  Refresh/Engine-Navigation + E-Ink-Scrim bleiben host-/Core-erzwungen, nicht Teil hiervon. */
data class ReaderScaffoldState(
    val chromeVisible: Boolean,
    val onToggleChrome: () -> Unit,
    val title: String,
    val onBack: () -> Unit,
    val onHome: () -> Unit,
    val onSettings: () -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val background: Color = Color.Black,
    val actions: @Composable RowScope.() -> Unit = {},
    val tapModifier: Modifier? = null,
    val footer: (@Composable BoxScope.() -> Unit)? = null,
    val persistentBars: (@Composable BoxScope.() -> Unit)? = null,
    val showTapZoneHints: Boolean = tapModifier == null,
    val content: @Composable () -> Unit,
)
```

### 4.2 `ReaderScaffold` wird dünner Host-Wrapper (Signatur unverändert → 5 Reader unangetastet)

```kotlin
@Composable
fun ReaderScaffold(
    chrome: Viewer, title: String, onBack: () -> Unit, onHome: () -> Unit, onSettings: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier, background: Color = Color.Black,
    actions: @Composable RowScope.() -> Unit = {}, tapModifier: Modifier? = null,
    footer: (@Composable BoxScope.() -> Unit)? = null, persistentBars: (@Composable BoxScope.() -> Unit)? = null,
    showTapZoneHints: Boolean = tapModifier == null, content: @Composable () -> Unit,
) {
    val chromeVisible by chrome.chromeVisible.collectAsState()
    val state = ReaderScaffoldState(
        chromeVisible = chromeVisible, onToggleChrome = chrome::toggleChrome,
        title = title, onBack = onBack, onHome = onHome, onSettings = onSettings,
        onPrev = onPrev, onNext = onNext, background = background, actions = actions,
        tapModifier = tapModifier, footer = footer, persistentBars = persistentBars,
        showTapZoneHints = showTapZoneHints, content = content,
    )
    // `modifier` ist Host-Layout (Reader-Route-Padding o. ä.) — als Box-Wrapper um den Slot, nicht in der Surface.
    Box(modifier) { LocalResolvedSlots.current.readerChrome(state) }
}
```
> **`modifier`-Behandlung:** Heute liegt `modifier` am äußeren `Box(modifier.fillMaxSize()...)`. Im Default
> übernimmt der Renderer das `fillMaxSize().background(...)`. Prüfe per `grep`, ob eine Reader-Call-Site
> einen nicht-trivialen `modifier` übergibt; falls keine, kann der Box-Wrapper entfallen und der Default
> macht `fillMaxSize` selbst (wie bei `settings`/`detail`). Pragmatik: kleinste verhaltensgleiche Form.

### 4.3 `DefaultReaderScaffold(state)` — der verbatim extrahierte Renderer (in `ReaderScaffold.kt`)

Der **verbatim** heutige `ReaderScaffold`-Body, der aus `state.*` liest statt aus Parametern, und
`chrome.chromeVisible`/`chrome.toggleChrome()` durch `state.chromeVisible`/`state.onToggleChrome()`
ersetzt. Der innere Overlay-Aufruf bleibt `with(this) { LocalResolvedSlots.current.overlay(
ReaderOverlayState(state.title, state.onBack, state.onHome, state.onSettings, state.actions)) }` —
**der `overlay`-Slot bleibt von C1 genutzt** (Komposition: `readerChrome`-Default verwendet die
`overlay`-Region; ein voller Chrome-Pack arrangiert das selbst). Start-Hinweis/Tap-Zonen/Footer/
persistentBars/Hints **identisch**.

### 4.4 Slot-Vertrag in `UiSlots.kt` (additiv)

```kotlin
typealias ReaderChromeSlot = @Composable (state: ReaderScaffoldState) -> Unit
```
- `UiSlotPack(header, homeHeader, dialog, settings, tiles, overlay, detail, readerChrome: ReaderChromeSlot? = null)`
- `ResolvedSlots(…, readerChrome: ReaderChromeSlot)`
- `UiSlots.resolve`: `readerChrome = pack.readerChrome ?: DefaultSlots.readerChrome`
- `DefaultSlots.readerChrome: ReaderChromeSlot = { state -> DefaultReaderScaffold(state) }` (Imports
  `ReaderScaffoldState`, `DefaultReaderScaffold` aus `ui.reader`)
- Klassen-KDoc oben: `readerChrome` als achte Region einordnen (das ganze Reader-Gerüst; Engines/`Viewer`
  bleiben Core, draußen).

### 4.5 E-Ink-Invarianten (host-erzwungen)

`readerOverlayScrim` (E-Ink deckend) + die Animation-Gating-Pfade (`ReaderStartHint` etc.) bleiben **im
Default + host-erzwungen**. Der Slot liefert nur Anordnung. Refresh (`RefreshScheduler`) ist gar nicht in
der Surface → ein Pack kann ihn nicht umgehen.

## 5. Swap-Beweis (Debug-Preview, keine Nutzer-Einstellung)

`app/src/debug/kotlin/com/komgareader/app/ui/reader/ReaderChromeSlotPreview.kt`: ein
`AlternativeReaderChrome` (`ReaderChromeSlot`) mit **anderer Anordnung** — z. B. Footer oben statt unten,
oder Tap-Hints weggelassen, oder ein minimaler Rahmen — plus `@Preview` mit `LocalResolvedSlots provides
UiSlots.resolve(UiSlotPack(readerChrome = AlternativeReaderChrome))` über einen `DefaultReaderScaffold`-
Aufruf mit Fake-`ReaderScaffoldState` (chromeVisible=true, Dummy-content). Weggelassene Fähigkeiten im
KDoc dokumentieren. Vorlage: `app/src/debug/.../ui/reader/OverlaySlotPreview.kt`.

## 6. Tests

- **Pure (`SlotFallbackTest.kt`):** zwei Tests analog detail — fehlender `readerChrome`-Slot fällt auf
  `DefaultSlots.readerChrome` zurück (`assertSame`); gelieferter überschreibt. **Echte Umlaute**. KDoc-Kopf
  nachziehen (acht Regionen).
- **E2E (Emulator `eink_test`, Test-Komga verbunden):** Reader öffnen (Serie → Lesen) — die Seite rendert
  **unverändert**; Mitte-Tap → Chrome (Overlay oben + Footer „X/N") erscheint **unverändert**; Links/Rechts-
  Tap blättert. Bei einem zweiten Reader-Typ (z. B. Novel: persistentBars-Header/Footer; oder Webtoon:
  Frame-Sprung-Tap) gegenprüfen, dass der reader-spezifische Pfad (persistentBars/tapModifier) **unverändert**
  funktioniert. Verhaltens-/pixelgleich.

## 7. Akzeptanz

- `ReaderScaffoldState` + `DefaultReaderScaffold` in `ReaderScaffold.kt`; `ReaderScaffold(chrome, …)` ist
  dünner Wrapper → `LocalResolvedSlots.current.readerChrome(state)`. **Alle 5 Reader-Call-Sites
  unverändert.** `Viewer` **nicht** in der Surface (nur `chromeVisible`/`onToggleChrome`); `refreshScheduler`
  nirgends in der Surface.
- `UiSlotPack`/`ResolvedSlots`/`DefaultSlots`/`UiSlots.resolve` tragen `readerChrome` additiv; die sieben
  bestehenden Regionen unberührt; der `overlay`-Slot wird vom Default weiter genutzt.
- `DefaultReaderScaffold` verhaltens-/pixelgleich (E2E gezeigt, ≥2 Reader-Typen). `Viewer.kt`/
  `RefreshScheduler`/Reader-Engines/`ReaderChrome.kt`-Helfer unberührt. Pure Fallback-Tests grün. Swap-
  Preview kompiliert.
- `architecture-seams.md` (achte Region `readerChrome`; Naht B bleibt draußen) + `big-picture-and-goals.md`
  Roadmap (C1 gebaut; deklarative Tap-Zone-Form + externer Lader bleiben Soll A1/L1/L2) + Memory-Roadmap im
  selben Commit nachgezogen.

## 8. Nicht in C1 (YAGNI)

**Kein** deklarativer Tap-Zone→Aktion-Deskriptor (bleibt bespoke `tapModifier` pro Reader — kommt mit
A1/Deskriptor + L1/L2-Lader). **Kein** externer APK-Pack-Lader, **kein** `ui-api`-Modul (Vertrag bleibt
in-tree). **Keine** Änderung an `Viewer`/`RefreshScheduler`/den Reader-Engines/`ReaderRoute`-Dispatch/den
`ReaderChrome.kt`-Helfern. Keine Vereinheitlichung der bespoke Tap-Modifier.

## Bezug

Roadmap `…complete-ui-modularity-roadmap.md` · `architecture-seams.md` (UI-Slot-Naht + Naht B/`Viewer`) ·
`komga-plugins`-Skill · Vorbild-Bauten `detail` (086f612) + `overlay` (7f0b654). Spätere Ausbaustufe:
**A1** (`ui-api` einfrieren) → deklarativer Tap-Zone-Deskriptor → **L1/L2** (externer Pack-Lader).
