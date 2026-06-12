# Region-Slot R1: `dialog` (EinkModal) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt R1** der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`

> **Self-contained:** Diese Spec ist so geschrieben, dass eine **frische Session ohne diesen
> Gesprächskontext** sie umsetzen kann. Vor dem Bauen lesen: den `komga-plugins`-Skill (Capability-
> Rezept, Säule 3) und `architecture-seams.md` (UI-Slot-Naht). Vorbilder im Code: die schon gebauten
> Regionen **header** + **homeHeader** in `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt`
> — R1 ist exakt dasselbe Muster für eine dritte Region.

## 1. Ziel

Den **Dialog-Look** auswechselbar machen: der eine Onyx-Dialog-Rahmen (`EinkModal`) wird hinter eine
benannte, adressierbare **`dialog`-Region** gelegt. Ein UI-Pack kann den Dialog anders rendern, ohne
dass die ~9 Aufrufstellen sich ändern. Erste der vier übrigen Region-Slots (overlay/tiles/settings/
**dialog**), bewusst die **isolierteste, risikoärmste** zuerst.

## 2. Ausgangslage (Ist)

- **`app/src/main/kotlin/com/komgareader/app/ui/components/EinkModal.kt`** — der eine Dialog-Baustein
  (was `eink-design-language.md` konzeptionell „BaseDialog" nennt). Signatur **heute**:
  ```kotlin
  @Composable
  fun EinkModal(
      title: String,
      onDismiss: () -> Unit,
      confirmLabel: String,
      onConfirm: () -> Unit,
      dismissLabel: String,
      modifier: Modifier = Modifier,
      confirmEnabled: Boolean = true,
      headerAction: (@Composable () -> Unit)? = null,
      content: @Composable ColumnScope.() -> Unit,
  )
  ```
  Body: `Dialog { Surface(border=strongBorder, shape=large) { Column(padding=20) { Titel · Body
  (scrollend) · Aktionen (Abbrechen links / Bestätigen rechts) } } }`. Keine Animation (host-erzwungen).
  ~9 Aufrufstellen in `app/ui/**` rufen `EinkModal(...)` direkt.
- **`app/.../ui/slots/UiSlots.kt`** — die Slot-Naht. Trägt heute `header` + `homeHeader`:
  `UiSlotPack(header, homeHeader)`, `ResolvedSlots(header, homeHeader)`, `DefaultSlots` mit beiden
  Default-Impls, der **pure** Resolver `UiSlots.resolve(pack)` (fehlende Region → `DefaultSlots`, nie
  `null`), `LocalResolvedSlots` (im Host `KomgaReaderTheme` bereitgestellt).
- **`app/src/test/.../ui/slots/SlotFallbackTest.kt`** — pure Tests des Resolvers (Fallback + Override)
  über referenzielle Identität (`assertSame`).

## 3. Design

### 3.1 Capability-Surface `DialogState`

Ein **benannter Satz** der Dialog-Fähigkeiten — spiegelt die echten `EinkModal`-Parameter, **kein**
Funktionsverlust. Lebt in `EinkModal.kt` (wie `HomeHeaderState` in `HomeHeader.kt` lebt).

```kotlin
/** Capability-Surface des Dialogs: benannte Stücke, die ein [DialogSlot]-Pack arrangiert. Spiegelt die
 *  EinkModal-Parameter 1:1 (kein Funktionsverlust). E-Ink-Invarianten host-erzwungen, nicht Teil hiervon. */
data class DialogState(
    val title: String,
    val onDismiss: () -> Unit,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
    val dismissLabel: String,
    val confirmEnabled: Boolean = true,
    val headerAction: (@Composable () -> Unit)? = null,
    val content: @Composable ColumnScope.() -> Unit,
)
```

> **Scoped-Lambda-Gotcha (aus dem Rezept):** `content` ist `@Composable ColumnScope.() -> Unit`. Das
> Default-Layout muss es mit **explizitem Receiver** aufrufen — `state.content(this)` aus einem `Column`,
> **nicht** bloß `state.content()`.

### 3.2 Host-Delegation — die ~9 Call-Sites bleiben unverändert

`EinkModal(...)` wird zum **dünnen Host-Wrapper**: er baut `DialogState` aus seinen Parametern und ruft
`LocalResolvedSlots.current.dialog(state)`. Der heutige Render-Body wandert **verbatim** in das Default-
Slot-Composable. So ändert sich **keine** Aufrufstelle (DRY, wie `DefaultHomeHeader` zum `HomeHeaderState`).

```kotlin
@Composable
fun EinkModal(
    title: String, onDismiss: () -> Unit, confirmLabel: String, onConfirm: () -> Unit,
    dismissLabel: String, modifier: Modifier = Modifier, confirmEnabled: Boolean = true,
    headerAction: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    // Hinweis: das `modifier`-Argument war ein lokaler Layout-Parameter des Default-Modals. Es ist NICHT
    // Teil der Capability-Surface (ein alternativer Pack legt sein eigenes Layout fest). Das Default-Slot-
    // Composable behält es als optionalen Parameter mit dem heutigen Verhalten; der Host reicht es dorthin
    // durch ODER (einfacher) der Default liest Local... — siehe Umsetzungs-Hinweis im Plan.
    val state = DialogState(title, onDismiss, confirmLabel, onConfirm, dismissLabel, confirmEnabled, headerAction, content)
    LocalResolvedSlots.current.dialog(state)
}
```

> **`modifier`-Entscheidung:** Der bisherige `modifier`-Parameter ist Layout-Detail des Default-Modals,
> keine Capability. Pragmatik (Plan entscheidet konkret): entweder im `DefaultDialog` als optionaler
> Parameter behalten und vom Host nur an den Default reichen, **oder** — falls keine Call-Site einen
> nicht-trivialen `modifier` übergibt — ersatzlos in den Default ziehen. Per `grep` prüfen, welche
> Call-Sites `modifier =` an `EinkModal` übergeben; die Surface bleibt davon unberührt.

### 3.3 Slot-Vertrag in `UiSlots.kt`

```kotlin
/** Vertrag der Dialog-Region. Ein Pack rendert den Dialog aus der [DialogState]-Surface. */
typealias DialogSlot = @Composable (state: DialogState) -> Unit
```

Additiv erweitern (bestehende Felder unberührt):
- `UiSlotPack(header, homeHeader, dialog: DialogSlot? = null)`
- `ResolvedSlots(header, homeHeader, dialog: DialogSlot)`
- `UiSlots.resolve`: `dialog = pack.dialog ?: DefaultSlots.dialog`
- `DefaultSlots.dialog: DialogSlot = { state -> DefaultDialog(state) }` — wobei `DefaultDialog` der
  **verbatim** aus dem heutigen `EinkModal`-Body extrahierte Onyx-Renderer ist (in `EinkModal.kt`).
- `LocalResolvedSlots`-Default (`UiSlots.resolve(UiSlotPack())`) deckt die neue Region automatisch.

### 3.4 E-Ink-Invarianten (host-erzwungen)

Keine Animation (das `Dialog` erscheint instant — heutiges Verhalten beibehalten). Akzent/Bewegung über
`LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`, **nie** im Pack. Der Slot liefert nur
Inhalt/Struktur. „Genau ein Modal gleichzeitig" bleibt eine Host-/Aufruf-Eigenschaft, nicht Pack-Sache.

### 3.5 Swap-Beweis (Debug-Preview, keine Nutzer-Einstellung)

`app/src/debug/kotlin/com/komgareader/app/ui/components/DialogSlotPreview.kt`: ein `AlternativeDialog`
(`DialogSlot`) mit **anderer Anordnung** (z. B. Titel zentriert, Aktionen vertikal gestapelt) + ein
`@Preview`, das `LocalResolvedSlots provides UiSlots.resolve(UiSlotPack(dialog = AlternativeDialog))`
über einen `EinkModal`-Aufruf zeigt. Beweist: dieselbe Surface, anderer Dialog, Call-Site unverändert.
Analog `HomeHeaderSlotPreview.kt`/`HeaderSlotPreview.kt`.

## 4. Tests

- **Pure (`SlotFallbackTest.kt` erweitern):** zwei Tests analog header/homeHeader — fehlender `dialog`-Slot
  fällt auf `DefaultSlots.dialog` zurück (`assertSame`); gelieferter Slot überschreibt. Compose-frei.
- **E2E (Emulator `eink_test`):** eine bestehende Dialog-Aufrufstelle öffnen (z. B. „neue Sammlung"/
  „neue Bibliothek" `+`-Dialog) → der Default-Onyx-Dialog erscheint **unverändert** (schwarzer Rand,
  Titel/Body/Aktionen, Bestätigen/Abbrechen funktionieren). Verhaltens-/pixelgleich zu vorher.

## 5. Akzeptanz

- `EinkModal` rendert über `LocalResolvedSlots.current.dialog(...)`; **keine** der ~9 Call-Sites geändert.
- `UiSlotPack`/`ResolvedSlots`/`DefaultSlots`/`UiSlots.resolve` tragen die `dialog`-Region additiv.
- Default-Dialog verhaltens-/pixelgleich (E2E gezeigt). Pure Fallback-Tests grün. Swap-Preview kompiliert.
- `architecture-seams.md` (UI-Slot-Naht: „drei Regionen gebaut") + `big-picture` Roadmap im selben Commit
  nachgezogen (docs-match-code). Memory-Pointer optional.

## 6. Nicht in R1 (YAGNI)

Kein User-Dialog-Override, kein `ui-api`-Modul (Vertrag bleibt in-tree), keine Touch an overlay/tiles/
settings — die sind eigene Sub-Projekte (R2–R4). Nur die `dialog`-Region.

## Bezug

Roadmap `2026-06-12-complete-ui-modularity-roadmap.md` · `architecture-seams.md` (UI-Slot-Naht) ·
`komga-plugins`-Skill (Capability-Rezept Säule 3) · `eink-design-language.md` (BaseDialog/EinkModal) ·
Vorbild-Bauten `2026-06-12-modular-home-header` + `2026-06-12-modular-ui-shell-pack`.
