# Region-Slot R4: `overlay` (Reader-Chrome-Menüleiste) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt R4** der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`

> **Self-contained:** Frische Session ohne Gesprächskontext kann das umsetzen. Vor dem Bauen lesen:
> `komga-plugins`-Skill (Capability-Rezept, Säule 3), `architecture-seams.md` (UI-Slot-Naht **+** Naht B/
> `Viewer`-Vertrag), `animation-gating.md` + `eink-design-language.md` (Overlay ist E-Ink-kritisch).
> **Direktes Vorbild:** die schon gebauten Regionen **tiles** (R3, `git show 93f631a`) + **settings** (R2)
> in `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt`. R4 ist dasselbe Muster für die
> sechste und **letzte** Region-Slot — mit **einem Compose-Knackpunkt** (BoxScope-Receiver, §5.2).

## 1. Ziel

Die **togglebare Reader-Chrome-Menüleiste** (`ReaderChromeOverlay` — die oben schwebende Leiste mit
Zurück · Titel · reader-spezifische Aktionen · Home · Einstellungen) hinter eine benannte, adressierbare
**`overlay`-Region** legen. Ein UI-Pack kann diese Leiste anders rendern (anderes Layout, Burger-Menü,
andere Anordnung der Shortcuts), ohne dass die Reader oder das `ReaderScaffold`-Gerüst sich ändern.
Sechste Region nach **header · homeHeader · dialog · settings · tiles** — die letzte der Region-Slot-Reihe.

## 2. Der Schnitt — die Chrome-Menüleiste, nicht das ganze Scaffold

**R4 slot-ifiziert genau die Top-Chrome-Menüleiste (`ReaderChromeOverlay`).** Bewusst die **schmale
Variante** — konsistent mit den Vorgänger-Regionen (je *eine* Komponente) und als erster, isolierter
Schritt Richtung C1 (》Reader-Chrome modular《), das später das **ganze** Gerüst + Tap-Zonen + Footer
deklarativ macht.

- **R4 (jetzt):** die togglebare Top-Menüleiste = `overlay`-Region.
- **NICHT in R4 (→ C1):** die Tap-Zonen (`tapModifier`, Drittel-Navigation), der Status-Fuß
  (`ReaderStatusBar`/`footer`-Lambda, reader-spezifisch), die `persistentBars` (Roman-Leisten), die
  Tap-Zonen-Hints, der Start-Hinweis. Diese bleiben im `ReaderScaffold` host-kontrolliert.
- **Reader-Engines + `Viewer`-Naht unberührt:** `chrome.chromeVisible`/`toggleChrome`/`navigateTo`/
  `refreshScheduler` (Naht B) bleiben exakt wie sie sind. Der Slot betrifft nur das *Aussehen* der Leiste.

## 3. Ausgangslage (Ist, verifiziert)

- **`app/.../ui/reader/ReaderChrome.kt`** — `ReaderChromeOverlay` (Z. 64-113) ist eine
  **`BoxScope`-Extension-Composable**:
  ```kotlin
  @Composable
  fun BoxScope.ReaderChromeOverlay(
      visible: Boolean,
      title: String,
      onBack: () -> Unit,
      onHome: () -> Unit,
      onSettings: () -> Unit,
      actions: @Composable RowScope.() -> Unit = {},
  ) {
      if (!visible) return
      // Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(readerOverlayScrim(Black,0.45f))
      //     .displayCutoutPadding().padding(12,10)) { Back · Titel(weight 1f) · actions() · Home · Settings }
  }
  ```
  Sie richtet sich per `Modifier.align(Alignment.TopCenter)` **im Eltern-`Box`** aus → daher die
  `BoxScope`-Bindung. Hintergrund über `readerOverlayScrim(Color.Black, 0.45f)` (E-Ink deckend,
  Smartphone halbtransparent — **host-erzwungene E-Ink-Invariante, bleibt**). Die Shortcuts Home+Settings
  liegen hier an *einer* Stelle (kein Reader baut sie selbst).
  - **`readerOverlayScrim`** (Z. 50-52), **`ReaderStatusBar`** (Z. 122-135) und die übrigen Helfer in der
    Datei bleiben **unangetastet**.
- **`app/.../ui/reader/ReaderScaffold.kt`** — `ReaderScaffold(...)` (Z. 39-113) ruft im äußeren
  `Box(modifier.fillMaxSize().background(...))` (BoxScope) u. a.:
  ```kotlin
  ReaderChromeOverlay(visible = chromeVisible, title = title, onBack = onBack, onHome = onHome,
                      onSettings = onSettings, actions = actions)
  ```
  `chromeVisible` kommt aus `chrome.chromeVisible.collectAsState()`. Alle 5 Reader-Screens bauen auf
  `ReaderScaffold` (zentralisiert, keine Parallel-Linien). **Dies ist die einzige Call-Site von
  `ReaderChromeOverlay`** (per `grep` verifizieren).
- **`UiSlots.kt`** — trägt header+homeHeader+dialog+settings+tiles; importiert bereits aus `ui.home`,
  `ui.settings`, `ui.components` → Import aus `ui.reader` ist regulär.
- **`SlotFallbackTest.kt`** — pure Resolver-Tests (`assertSame`), je 2 pro Region, **echte Umlaute**.

## 4. Design

### 4.1 Capability-Surface `ReaderOverlayState` (in `ReaderChrome.kt`)

```kotlin
/** Capability-Surface der Reader-Chrome-Menüleiste: Titel + Navigations-/Shortcut-Callbacks + die
 *  reader-spezifischen Aktionen. Ein [OverlaySlot]-Pack arrangiert daraus die Leiste. Sichtbarkeit
 *  (chromeVisible) + E-Ink-Scrim sind host-erzwungen, nicht Teil hiervon. */
data class ReaderOverlayState(
    val title: String,
    val onBack: () -> Unit,
    val onHome: () -> Unit,
    val onSettings: () -> Unit,
    val actions: @Composable RowScope.() -> Unit,
)
```

> **Kein `visible` in der Surface:** die Sichtbarkeit gehört dem Host (`ReaderScaffold` rendert die Leiste
> nur bei `chromeVisible` — s. §5.3), genau wie der Footer schon heute host-gegated ist. Das hält die
> Surface sauber und konsistent mit settings/tiles (kein Layout-/Zustands-Flag in der Surface).

### 4.2 `DefaultReaderOverlay` — der verbatim extrahierte Onyx-Renderer (in `ReaderChrome.kt`)

```kotlin
@Composable
fun BoxScope.DefaultReaderOverlay(state: ReaderOverlayState) { … }
```
Body = der **verbatim** aus dem heutigen `ReaderChromeOverlay` extrahierte `Row`-Block (liest `state.title`/
`state.onBack`/…/`state.actions`), **ohne** den `visible`-Parameter und **ohne** das `if (!visible) return`
(die Sichtbarkeit gatet jetzt der Host). Bleibt eine **`BoxScope`-Extension** (wegen
`Modifier.align(Alignment.TopCenter)`). `readerOverlayScrim`/`displayCutoutPadding`/Padding/Shortcuts
unverändert.

> **`ReaderChromeOverlay` entfällt** und wird durch `DefaultReaderOverlay` ersetzt — es gibt nur die eine
> Call-Site (Scaffold), die auf den Slot umgestellt wird.

### 4.3 Slot-Vertrag in `UiSlots.kt` (additiv)

```kotlin
/** Vertrag der Reader-Chrome-Menüleiste. BoxScope-Extension, weil die Leiste sich im Reader-Box per
 *  align(TopCenter) positioniert. Ein Pack rendert die Leiste aus [ReaderOverlayState]. */
typealias OverlaySlot = @Composable BoxScope.(state: ReaderOverlayState) -> Unit
```
- `UiSlotPack(header, homeHeader, dialog, settings, tiles, overlay: OverlaySlot? = null)`
- `ResolvedSlots(header, homeHeader, dialog, settings, tiles, overlay: OverlaySlot)`
- `UiSlots.resolve`: `overlay = pack.overlay ?: DefaultSlots.overlay`
- `DefaultSlots.overlay: OverlaySlot = { state -> DefaultReaderOverlay(state) }` — der Lambda-Receiver ist
  `BoxScope`, daher ruft `DefaultReaderOverlay(state)` die BoxScope-Extension mit implizitem Receiver auf
  (Imports: `androidx.compose.foundation.layout.BoxScope`, `ReaderOverlayState`, `DefaultReaderOverlay`).
- Klassen-KDoc oben auf „header + homeHeader + dialog + settings + tiles + overlay = **alle sechs**
  Region-Slots gebaut" nachziehen (die konzeptionelle Liste war header·overlay·tiles·nav·settings·dialog;
  `nav` blieb außen vor / ist Teil des Shell-Packs — das im KDoc klarstellen, nicht als offen behaupten,
  was woanders gelöst ist).

### 4.4 E-Ink-Invarianten (host-erzwungen)

`readerOverlayScrim` (E-Ink deckend / Smartphone halbtransparent) + die Tatsache, dass die Leiste **keine
eigene Animation** trägt (sie erscheint/verschwindet instant mit `chromeVisible` — Animation-Gating liegt
im Scaffold/`AnimatedVisibility`-Pfaden, nicht in der Leiste) bleiben **im Default + host-erzwungen**. Der
Slot liefert nur Struktur/Anordnung. Ein Pack kann die Bewegungs-/Scrim-Policy nicht umgehen.

## 5. Umbau `ReaderScaffold`

### 5.1 Call-Site umstellen
Den `ReaderChromeOverlay(visible = chromeVisible, …)`-Aufruf (Z. 95-102) ersetzen durch eine
**host-gegatete** Slot-Auflösung im selben `Box` (BoxScope):
```kotlin
if (chromeVisible) {
    LocalResolvedSlots.current.overlay(
        ReaderOverlayState(title = title, onBack = onBack, onHome = onHome,
                           onSettings = onSettings, actions = actions),
    )
}
```
Import `com.komgareader.app.ui.slots.LocalResolvedSlots` + `ReaderOverlayState` ergänzen.

### 5.2 ⚠️ BoxScope-Receiver-Knackpunkt
`overlay` ist `BoxScope.(ReaderOverlayState) -> Unit`. Der Aufruf steht **innerhalb** des Scaffold-`Box {}`
(dessen Content-Lambda `this: BoxScope` hat), daher ist der nötige `BoxScope`-Receiver als implizites
`this` vorhanden — `LocalResolvedSlots.current.overlay(state)` sollte direkt kompilieren. **Falls der
Kotlin-Compiler den impliziten Receiver für den Funktionswert nicht zieht**, explizit machen:
```kotlin
val slots = LocalResolvedSlots.current
with(slots) { this@<BoxLabel>.overlay(state) }   // oder: Box-Content-Receiver via run { }
```
Per `./gradlew :app:compileDebugKotlin` verifizieren — der Bau ist der Schiedsrichter. **Nicht** das Design
ändern (Surface/Typealias bleiben), nur die Aufruf-Syntax anpassen, bis es kompiliert.

### 5.3 Verhalten bleibt gleich
Vorher: `ReaderChromeOverlay(visible=chromeVisible)` → intern `if(!visible) return`. Nachher:
`if (chromeVisible) overlay(state)`. **Identisch** — Leiste sichtbar gdw. `chromeVisible`. Footer/Hints/
Tap-Zonen unverändert host-gegated.

## 6. Swap-Beweis (Debug-Preview, keine Nutzer-Einstellung)

`app/src/debug/kotlin/com/komgareader/app/ui/reader/OverlaySlotPreview.kt`: ein `AlternativeReaderOverlay`
(`OverlaySlot`, BoxScope-Extension) mit **anderer Anordnung** — z. B. Titel zentriert, Shortcuts links —
plus `@Preview`, das `LocalResolvedSlots provides UiSlots.resolve(UiSlotPack(overlay = AlternativeReaderOverlay))`
in einem `Box { … }` zeigt (BoxScope für die Extension). Fake-`ReaderOverlayState`. Analog
`TileSlotPreview.kt`/`SettingsSlotPreview.kt` (eine davon als Vorlage lesen). Weggelassene Fähigkeiten im
KDoc dokumentieren (R1-R3-Lehre).

## 7. Tests

- **Pure (`SlotFallbackTest.kt` erweitern):** zwei Tests analog tiles — fehlender `overlay`-Slot fällt auf
  `DefaultSlots.overlay` zurück (`assertSame`); gelieferter überschreibt. **Echte Umlaute** (`fällt`/
  `zurück`/`überschreibt`). KDoc-Kopf nachziehen (alle sechs Regionen).
  > Hinweis: `OverlaySlot`/`DefaultSlots.overlay` sind `BoxScope`-Extension-Funktionswerte — `assertSame`
  > auf die Referenz funktioniert wie bei den anderen Slots (kein Compose-Aufruf nötig).
- **E2E (Emulator `eink_test`, 1264×1680, Test-Komga verbunden):** einen Reader öffnen (Serie → Lesen),
  Mitte tippen → die Chrome-Menüleiste erscheint **unverändert** (schwarze Leiste oben: Zurück · Titel ·
  Home · Einstellungen). Erneut Mitte tippen → verschwindet. Verhaltens-/pixelgleich zu vorher.

## 8. Akzeptanz

- `ReaderChromeOverlay` → `DefaultReaderOverlay(state)` (verbatim, ohne `visible`); `ReaderScaffold` rendert
  die Leiste über `LocalResolvedSlots.current.overlay(...)`, host-gegated mit `chromeVisible`.
- `UiSlotPack`/`ResolvedSlots`/`DefaultSlots`/`UiSlots.resolve` tragen `overlay` additiv; die fünf
  bestehenden Regionen unberührt. **Alle sechs Region-Slots damit gebaut.**
- `readerOverlayScrim`/`ReaderStatusBar`/Tap-Zonen/`persistentBars`/Hints unangetastet. Reader-Engines +
  `Viewer`-Naht unberührt. Pure Fallback-Tests grün. Swap-Preview kompiliert. E2E pixelgleich.
- `architecture-seams.md` (UI-Slot-Naht: „sechs/alle Regionen gebaut") + `big-picture-and-goals.md` Roadmap
  (Region-Slots abgeschlossen, nächstes Sub-Projekt = D1/C1) + Memory-Roadmap im selben Commit nachgezogen.

## 9. Nicht in R4 (YAGNI)

Kein User-Overlay-Override, kein `ui-api`-Modul. **Keine** Slot-ifizierung von Tap-Zonen, Footer
(`ReaderStatusBar`), `persistentBars`, Hints — das ist C1 (Reader-Chrome komplett modular, deklarative
UI-Plugin-Form). Keine Änderung an den Reader-Screens, am `Viewer`-Vertrag oder am `RefreshScheduler`.

## Bezug

Roadmap `2026-06-12-complete-ui-modularity-roadmap.md` · `architecture-seams.md` (UI-Slot-Naht + Naht B/
`Viewer`) · `komga-plugins`-Skill (Capability-Rezept) · `animation-gating.md`/`eink-design-language.md` ·
Vorbild-Bauten R3 `tiles` (93f631a) + R2 `settings`. Nachfolger: **C1** (Reader-Chrome komplett modular).
