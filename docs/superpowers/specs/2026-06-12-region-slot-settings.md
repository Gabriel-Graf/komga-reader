# Region-Slot R2: `settings` (SettingsScreen-Skelett) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt R2** der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`

> **Self-contained:** Frische Session ohne Gesprächskontext kann das umsetzen. Vor dem Bauen lesen:
> `komga-plugins`-Skill (Capability-Rezept, Säule 3) und `architecture-seams.md` (UI-Slot-Naht).
> **Direktes Vorbild im Code:** R1 `dialog` (gerade gebaut) + `homeHeader` — beide in
> `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt`. R2 ist exakt dasselbe Muster für die
> vierte Region. Lies vor dem Bauen den R1-Build (`git show ad8f0cc`) als Referenz.

## 1. Ziel

Das **Settings-Skelett** auswechselbar machen: die Master-Detail/Accordion-Anordnung (Sidebar-Nav +
Content-Host) wird hinter eine benannte, adressierbare **`settings`-Region** gelegt. Ein UI-Pack kann
die Settings völlig anders anordnen (z. B. flache Einzelliste statt Sidebar), ohne dass die einzelnen
Sektions-**Inhalte** sich ändern. Vierte Region nach **header · homeHeader · dialog**.

## 2. Der Schnitt — was der Slot tauscht, was er NICHT tauscht

**Der Slot tauscht das *Layout-Skelett*, nicht die Sektions-Inhalte.** Das ist der entscheidende, an
die schon gebauten Slots angelehnte Schnitt:

- **Host-gebaut (Core, bleibt):** die Liste der `SettingsSection` (jede trägt `id`/`icon`/`title`/
  `searchTerms` als **Daten** + `content: @Composable (query) -> Unit` als **host-gebautes** Composable,
  das seine eigenen Modals/VMs besitzt). Diese baut weiter `buildSettingsSections(strings, viewModel)`.
  Der **Pack rendert die Sektions-Inhalte nie neu** — er platziert `section.content(query)` (》UI neu,
  Kernlogik gleich《).
- **Pack-gewählt (austauschbar):** *wie* die Sektionen angeordnet/navigiert werden — Sidebar-Master-
  Detail vs. Accordion vs. flache Liste, die Filter-/Empty-State-Darstellung, die Navigations-State
  (welche Sektion aktiv/aufgeklappt). Das ist **layout-spezifisch** und gehört darum in die Slot-Impl,
  **nicht** in die Capability-Surface.

> **Warum `selectedId`/`openId` NICHT in die Surface:** ein Pack mit einer flachen Einzel-Scroll-Liste
> hat gar keine „aktive Sektion". Navigations-State ist Eigentum der jeweiligen Layout-Impl (wie die
> `selectedId`-`rememberSaveable` heute *in* `SettingsMasterDetail` lebt, nicht im `SettingsScreen`).
> Die Surface bleibt darum minimal: nur die Sektions-Daten + der Such-Query.

## 3. Ausgangslage (Ist, verifiziert)

- **`app/.../ui/settings/SettingsScreen.kt`** — `SettingsScreen(query, modifier, viewModel)` (Z. 73-96)
  ist der Host: baut `sections = buildSettingsSections(s, viewModel)`, filtert `visible` per `query`,
  zeigt Empty-State, und verzweigt in `BoxWithConstraints(modifier.fillMaxSize())` nach Breite:
  `< 600.dp → SettingsAccordion`, sonst `SettingsMasterDetail` (`>= 900.dp` größere Sizing). Die
  privaten Helfer `SettingsMasterDetail`/`SettingsSidebar`/`SettingsAccordion` + `SettingsSizing`/
  `SizingMedium`/`SizingExpanded` leben alle in dieser Datei. Die Accordion-Animation ist bereits über
  `LocalEinkMode` gegatet (host-erzwungene E-Ink-Invariante — bleibt im Default).
- **`app/.../ui/settings/SettingsSections.kt`** — `SettingsSectionId`-Enum, die `SettingsSection`-
  data-class (id/icon/title/searchTerms/scrollable/content) und `buildSettingsSections(s, viewModel)`.
  **Bleibt unverändert** — sie ist schon die saubere host-gebaute „benannte Stücke"-Liste.
- **Zwei Call-Sites** von `SettingsScreen`:
  - `HomeScreen.kt:322` → `SettingsScreen(query = …)` (kein `modifier`).
  - `SettingsRoute.kt:38` → `SettingsScreen(query = "", modifier = Modifier.fillMaxSize().padding(padding))`
    — **übergibt `modifier`** (Scaffold-Padding). ⇒ der `modifier`-Param **bleibt** am Host (anders als
    bei R1, wo keine Call-Site ihn nutzte und er entfiel).
- **`UiSlots.kt`** — trägt heute `header`+`homeHeader`+`dialog`: `UiSlotPack`, `ResolvedSlots`,
  `DefaultSlots`, pure `UiSlots.resolve`, `LocalResolvedSlots`.
- **`SlotFallbackTest.kt`** — pure Resolver-Tests (`assertSame`), je 2 pro Region.

## 4. Design

### 4.1 Capability-Surface `SettingsState`

Minimal — die host-gebauten Sektionen + der Query. Lebt in `SettingsScreen.kt` (wie `DialogState` in
`EinkModal.kt`).

```kotlin
/** Capability-Surface der Settings-Region: die host-gebauten Sektionen + der Such-Query. Ein
 *  [SettingsSlot]-Pack ordnet sie an (Sidebar/Accordion/flach) und besitzt den Navigations-State
 *  selbst. Sektions-Inhalte (`section.content`) sind host-gebaut — das Pack platziert sie nur, baut
 *  sie nie neu. E-Ink-Invarianten host-erzwungen, nicht Teil hiervon. */
data class SettingsState(
    val sections: List<SettingsSection>,
    val query: String,
)
```

> **Scoped-Lambda-Hinweis:** `section.content` ist `@Composable (query: String) -> Unit` (kein
> Receiver) — schlicht `section.content(state.query)` aufrufen.

### 4.2 Host-Delegation — `SettingsScreen` wird dünner Wrapper

`SettingsScreen` baut die Surface und delegiert an den Slot. Die zwei Call-Sites bleiben **unverändert**.
Der `modifier` ist Host-Layout (Route-Padding), **nicht** Teil der Surface → als Box-Wrapper um den
Slot-Call. Der heutige Render-Body (Filter + Empty + adaptive Verzweigung) wandert **verbatim** in
`DefaultSettings`.

```kotlin
@Composable
fun SettingsScreen(
    query: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val sections = buildSettingsSections(s, viewModel)
    val state = SettingsState(sections, query)
    Box(modifier) {
        LocalResolvedSlots.current.settings(state)
    }
}
```

### 4.3 `DefaultSettings` — der verbatim extrahierte Onyx-Renderer

`@Composable fun DefaultSettings(state: SettingsState)` (in `SettingsScreen.kt`). Body = der **heutige**
`SettingsScreen`-Rumpf ab `val visible = …`, aber:
- liest `state.sections`/`state.query` statt der Parameter
- `BoxWithConstraints(Modifier.fillMaxSize())` (der externe `modifier` liegt jetzt am Host-Box-Wrapper,
  hier nur noch `fillMaxSize` — verhaltensgleich, weil die Call-Site-`modifier` das Box außen einfasst)
- die privaten Helfer `SettingsMasterDetail`/`SettingsSidebar`/`SettingsAccordion` + die `SettingsSizing`-
  Konstanten bleiben **unverändert private** in der Datei und werden von `DefaultSettings` genutzt.

### 4.4 Slot-Vertrag in `UiSlots.kt` (additiv)

```kotlin
typealias SettingsSlot = @Composable (state: SettingsState) -> Unit
```
- `UiSlotPack(header, homeHeader, dialog, settings: SettingsSlot? = null)`
- `ResolvedSlots(header, homeHeader, dialog, settings: SettingsSlot)`
- `UiSlots.resolve`: `settings = pack.settings ?: DefaultSlots.settings`
- `DefaultSlots.settings: SettingsSlot = { state -> DefaultSettings(state) }` (Import `DefaultSettings`,
  `SettingsState`)
- Klassen-KDoc oben auf „header + homeHeader + dialog + settings gebaut" nachziehen, Soll-Regionen
  (overlay/tiles/nav) unberührt lassen.

### 4.5 E-Ink-Invarianten (host-erzwungen)

Die Accordion-Auf/Zu-Animation bleibt **über `LocalEinkMode` gegatet** (heutiges Verhalten, im
`DefaultSettings`-Pfad). Bewegung/Akzent über `LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`,
nie im Pack. Der Slot liefert nur Anordnung/Struktur.

### 4.6 Swap-Beweis (Debug-Preview, keine Nutzer-Einstellung)

`app/src/debug/kotlin/com/komgareader/app/ui/settings/SettingsSlotPreview.kt`: ein `AlternativeSettings`
(`SettingsSlot`) mit **anderer Anordnung** — z. B. eine **flache Einzel-Scroll-Liste** aller Sektionen
(Titel-Header + `section.content(query)` untereinander, ohne Sidebar/Accordion) — plus ein `@Preview`,
das `LocalResolvedSlots provides UiSlots.resolve(UiSlotPack(settings = AlternativeSettings))` über einen
`SettingsScreen`-Aufruf zeigt. Beweist: dieselbe Surface, anderes Skelett, Call-Site unverändert.
Analog `DialogSlotPreview.kt`/`HomeHeaderSlotPreview.kt`. (Preview kann mit einer kleinen
Fake-`SettingsState` aus 1-2 Dummy-`SettingsSection`s gespeist werden, falls `hiltViewModel()` im
Preview nicht verfügbar ist.)

## 5. Tests

- **Pure (`SlotFallbackTest.kt` erweitern):** zwei Tests analog dialog — fehlender `settings`-Slot fällt
  auf `DefaultSlots.settings` zurück (`assertSame`); gelieferter überschreibt. Echte Umlaute in den
  Testnamen (`fällt`/`zurück`/`überschreibt` — **nicht** ae/oe/ue/ss; R1-Review-Lehre). KDoc-Kopf auf
  „header + homeHeader + dialog + settings" nachziehen.
- **E2E (Emulator `eink_test`, 1264×1680):** Settings-Tab öffnen → der Default-Master-Detail-Settings-
  Screen erscheint **unverändert** (Sidebar links mit Sektions-Icons, Content rechts, Akzent-Balken an
  aktiver Sektion). Eine Sektion wechseln, prüfen dass Inhalt rendert. Verhaltens-/pixelgleich zu vorher.

## 6. Akzeptanz

- `SettingsScreen` rendert über `LocalResolvedSlots.current.settings(...)`; **beide** Call-Sites
  (HomeScreen-Tab + SettingsRoute) unverändert, `modifier` weiter respektiert.
- `UiSlotPack`/`ResolvedSlots`/`DefaultSlots`/`UiSlots.resolve` tragen die `settings`-Region additiv;
  header/homeHeader/dialog unberührt.
- `DefaultSettings` verhaltens-/pixelgleich (E2E gezeigt). `SettingsSections.kt` unverändert. Pure
  Fallback-Tests grün. Swap-Preview kompiliert.
- `architecture-seams.md` (UI-Slot-Naht: „vier Regionen gebaut") + `big-picture-and-goals.md` Roadmap
  im selben Commit nachgezogen (docs-match-code). Memory-Roadmap-Pointer aktualisiert.

## 7. Nicht in R2 (YAGNI)

Kein User-Settings-Override, kein `ui-api`-Modul, keine Touch an overlay/tiles/nav-Slots, keine
Änderung an den Sektions-**Inhalten** (`SettingsContent.kt`, `ColorFilterSettingsContent.kt`,
`PluginConfigForm.kt`) oder an `SettingsSections.kt`/`SettingsViewModel`. Nur das Settings-Skelett
hinter die `settings`-Region. Die geteilten Settings-Zeilen (`SettingsRow`/`SwitchRow`/… in
`EinkComponents.kt`) bleiben unberührt.

## Bezug

Roadmap `2026-06-12-complete-ui-modularity-roadmap.md` · `architecture-seams.md` (UI-Slot-Naht) ·
`komga-plugins`-Skill (Capability-Rezept Säule 3) · Vorbild-Bauten R1 `dialog`
(`2026-06-12-region-slot-dialog.md`, Commit ad8f0cc) + `homeHeader` (`2026-06-12-modular-home-header`).
