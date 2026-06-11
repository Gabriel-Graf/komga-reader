# Modularer Home-Header + plugin-domain-Skill

**Datum:** 2026-06-12
**Status:** Design (genehmigt, vor Implementierungsplan)

## Leitidee: „UI neu, Kernlogik gleich"

Modulare UI heißt: der **ganze** Header (und perspektivisch das ganze Chrome) ist von der
Community austausch-/neu-ordbar — **aber alle Fähigkeiten, die der Header bietet** (Library-Suche,
Plugin-Suche + Sync, Filter-Optionen, Refresh, „neu"-Aktionen) bleiben **Core-besessen**. Ein
UI-Pack *ordnet, restyled und versteckt* diese Fähigkeiten, **implementiert sie nie neu**. Die
Kernlogik (Suche, Sync, Filter) lebt genau einmal im Core; nur die Präsentation ist plugbar.

Konkret: der Host baut eine **Capability-Surface** (`HomeHeaderState`) — einen benannten Satz von
Fähigkeiten + Callbacks. Der `HomeHeaderSlot` (Pack) bekommt diese Surface und arrangiert sie. Wenn
die UI-Pack-**ABI** (Phase 4 Soll) gebaut wird, ist **dieser Capability-Satz** das, was dort
registriert wird. Heute in-tree, aber ABI-fähig designt.

Dieses Prinzip ist nicht header-spezifisch — es ist das allgemeine Rezept, ein Subsystem plugbar zu
machen. Deshalb gehört es zuerst in einen **`plugin-domain`-Skill**, und der Header ist sein erstes
Vorzeige-Beispiel.

## Reihenfolge (iterativ, Skill als Spec)

1. **`plugin-domain`-Skill schreiben** — Philosophie + Plugin-Bau + Pluggable-Subsystem-Rezept.
2. **Header bauen** strikt nach dem Skill (Capability-Surface + `HomeHeaderSlot`).
3. **Validierungs-Schleife mit `skill-writer`** — prüfen, ob der Header dem Skill folgt; Skill **und**
   Header iterativ angleichen, bis beide stimmen. Der Skill ist die Spec, der Header sein Beweis.

---

## Deliverable A — `plugin-domain`-Skill

**Ort:** `.claude/skills/plugin-domain/SKILL.md` (+ Sub-Skills bei Bedarf). Einstiegs-/Index-Skill für
jede Arbeit an Plugins **und** an plugbaren Subsystemen.

**Inhalt (drei Säulen):**

1. **Plugin-Philosophie** (verdichtet aus den bestehenden Rules, nicht dupliziert — verweist):
   - Core bleibt · Chrome + Capabilities austauschbar (die Trennlinie aus `big-picture-and-goals.md`).
   - Variabilität hinter Nähten (`architecture-seams.md`), Gemeinsames vor der N-ten Variante
     (`shared-structure-before-variants.md`).
   - **Deklarativ, nicht arbiträrer Compose-Code** — Pack/Plugin liefert eine *Beschreibung*, der Host
     rendert + erzwingt E-Ink-Invarianten.
   - ABI-Gate als zwei Ints; neue Capability = neues optionales Interface (additiv, kein Bump).
   - Die drei Plugin-Typen + Reihenfolge: Color-Preset (data-only) → Quelle → UI/Capability (zuletzt,
     riskant).
   - TOFU-Signatur-Pinning, `plugin-sdk` als einziges `compileOnly`-Artefakt.
   - **„UI neu, Kernlogik gleich"**: Pack ordnet Capabilities, implementiert sie nie neu.

2. **Wie ein Plugin gebaut wird:** `plugin-sdk` `compileOnly` linken, Manifest-Metadata
   (`ENTRY_CLASS`/`ABI_VERSION`/Asset-Namen), Discovery via `PluginHost`, Signatur/Fingerprint,
   Repo-Index-Eintrag. Verweist auf das Kavita-Plugin + Color-Preset-Plugin als Referenzen.

3. **Rezept: ein neues Subsystem plugbar machen** (der allgemeine Kern):
   1. **Capability-Vertrag definieren** — welche Fähigkeiten exponiert das Subsystem? Als benannter,
      stabiler Satz (Daten + Callbacks), nicht als arbiträrer Code.
   2. **Host besitzt die Logik** — die Fähigkeit lebt einmal im Core; der Host baut die Surface.
   3. **Naht/Slot mit Default** — In-Tree-Slot (wie `UiSlots`), Default-Pack = mitgeliefertes Verhalten,
      fehlender Slot → Default (nie null, analog `StubSource`).
   4. **ABI-fähig designen** — der Capability-Satz ist so geschnitten, dass er später in die UI-Pack-ABI
      eingefroren werden kann; E-Ink-Invarianten bleiben **host-erzwungen**, nie vom Pack.
   5. **Pack/Plugin ordnet/liefert** — die externe Seite arrangiert/restyled, implementiert nie die Logik.

**Stil:** Wie die bestehenden Skills/Rules (deutsch, echte Umlaute, `[[wikilink]]`-Bezüge,
Soll/Ist getrennt, docs-match-code). Kein Phantom-Typ, den `grep` nicht findet.

---

## Deliverable B — Modularer Home-Header (erstes Beispiel des Rezepts)

### B.1 Capability-Surface `HomeHeaderState`

Host-gebauter Halter (neue Datei `app/ui/home/HomeHeader.kt`), benannte Fähigkeiten:

```kotlin
/** Die Capability-Surface des Home-Headers: Fähigkeiten + Callbacks, die der Core bereitstellt
 *  und ein HomeHeaderSlot (Pack) arrangiert — nie neu implementiert. ABI-fähig geschnitten. */
data class HomeHeaderState(
    val status: @Composable () -> Unit,                       // Default: StatusCluster
    val search: HomeHeaderSearch,
    val filter: HomeHeaderFilter?,                            // null = kein Filter auf diesem Tab
    val menu: @Composable () -> Unit,                         // offener Filter-Popup (anchored) oder {}
    val actions: @Composable RowScope.() -> Unit,            // Tab-spezifische Rechts-Aktionen
)

data class HomeHeaderSearch(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val placeholder: String,
    val clearLabel: String?,
    val onClear: (() -> Unit)?,
    val leading: (@Composable RowScope.() -> Unit)?,        // Library-Filter-Chips o. Ä.
)

/** Generischer Filter-Icon-Slot — Library UND Plugins teilen ihn (DRY). */
data class HomeHeaderFilter(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val onAnchor: (IntOffset) -> Unit,                       // onGloballyPositioned → Anchor
)
```

E-Ink-Invarianten sind **nicht** Teil der Surface — sie bleiben host-erzwungen über
`LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`. Ein Pack liefert nur Struktur/Anordnung.

### B.2 `HomeHeaderSlot` in der `UiSlots`-Naht (zweite Region nach `header`)

In `app/ui/slots/UiSlots.kt` analog zum bestehenden `header`:

```kotlin
typealias HomeHeaderSlot = @Composable (state: HomeHeaderState) -> Unit

data class UiSlotPack(
    val header: HeaderSlot? = null,
    val homeHeader: HomeHeaderSlot? = null,                  // NEU
)
data class ResolvedSlots(
    val header: HeaderSlot,
    val homeHeader: HomeHeaderSlot,                          // NEU, garantiert non-null
)
object UiSlots {
    fun resolve(pack: UiSlotPack) = ResolvedSlots(
        header = pack.header ?: DefaultSlots.header,
        homeHeader = pack.homeHeader ?: DefaultSlots.homeHeader,   // NEU
    )
}
object DefaultSlots {
    val homeHeader: HomeHeaderSlot = { state -> DefaultHomeHeader(state) }   // NEU
}
```

### B.3 `DefaultHomeHeader` (das heutige Layout extrahiert)

`app/ui/home/HomeHeader.kt`: rendert die Surface im heutigen Onyx-Layout — StatusCluster links ·
zentrierte `EinkSearchBar` (mit `search.leading`) · 40dp-Filter-Icon-Slot (gefüllt wenn
`state.filter != null`) · `state.actions` rechts · `state.menu()`. Verhaltensgleich zum heutigen
direkten `TopAppBar`-Aufruf (kein visueller Unterschied für das Default-Pack).

### B.4 `HomeScreen` schrumpft

`HomeScreen` baut pro Tab eine `HomeHeaderState` aus seinen States/VMs und ruft
`LocalResolvedSlots.current.homeHeader(state)` im `Scaffold(topBar = …)`. Die 282-Zeilen-Funktion
verliert den inline-TopAppBar-Block; die Tab-Logik (welche Aktionen, welcher Filter) bleibt im Host.

### B.5 Plugins-Filter → Icon (DRY mit Library) — die kleine Ergänzung neben dem Refactor

Der rotierende `PluginFilterChip` **entfällt**. Der gemeinsame 40dp-Filter-Icon-Slot (heute nur
Library) wird über `HomeHeaderFilter` **generisch** — **Library und Plugins teilen Icon + Anchor +
Popup-Mechanik**. Für TAB_PLUGINS liefert der Host ein `HomeHeaderFilter` (Icon `AppIcons.Filter`)
und als `menu` ein **neues `PluginFilterMenu`** (Einfach-Auswahl Alle/Quellen/Presets, **Häkchen auf
aktiv**) im `AnchoredMenuPopup`, **analog `TypeFilterMenu`**. Library liefert weiter sein
`TypeFilterMenu`. Damit ist die Filter-Icon-Mechanik genau **einmal** im `DefaultHomeHeader`, nicht
pro Tab dupliziert.

### B.6 Swap-Beweis & Tests

- **Swap-Beweis:** `@Preview` (Debug-only) mit einem Alternativ-`HomeHeaderSlot` (z. B. Suche oben,
  Aktionen darunter) — zeigt, dass das ganze Layout über ein Pack ersetzbar ist, ohne Tab-Logik
  anzufassen. **Keine** Nutzer-Einstellung (Pack-Lader bleibt Phase-4-Soll).
- **Unit-Test:** `UiSlots.resolve` — `homeHeader`-Fallback auf `DefaultSlots.homeHeader` wenn Pack
  keinen liefert (nie null), und Durchreichen wenn vorhanden.
- **E2E (`eink_test` 1264×1680@300):** Library-Filter unverändert (Icon → Menü → Filter wirkt);
  Plugins-Filter jetzt **Icon + Menü** statt Chip (Alle/Quellen/Presets, Häkchen); Header rendert auf
  allen 5 Tabs korrekt (Status/Suche/Aktionen/Menüs); Plugins-Suche + Reload + Repo-Settings
  weiterhin funktional.

---

## Deliverable C — Validierungs-Schleife (Skill ↔ Header)

Nach dem Header-Bau: `skill-writer` (writing-skills) prüft den Header gegen den `plugin-domain`-Skill —
folgt die Capability-Surface dem Rezept? Ist „UI neu, Kernlogik gleich" eingehalten (keine Logik im
Slot)? Lücken → **Skill ergänzen** (Beispiel/Anti-Pattern) **und** **Header verbessern**, bis beide
deckungsgleich sind. Der Header wird zum referenzierten Beispiel im Skill (`[[modular-home-header]]`).

---

## Modulgrenzen / Invarianten (unverhandelbar)

- **E-Ink host-erzwungen:** Bewegung über `allowsMotion`, Akzent über `allowsAccentColor` — nie im Slot.
- **Quellen-agnostisch:** der Header kennt keine konkrete Quelle; Plugin-Filter operiert über
  `PluginTypeFilter` (data), nicht über Plugin-Host-Typen in der UI.
- **Default nie null:** fehlender Slot → `DefaultSlots` (analog `StubSource`).
- **docs-match-code:** `architecture-seams.md` (UI-Slot-Naht: zweite Region `homeHeader`) +
  `big-picture-and-goals.md` (HomeScreen-Ausnahme aufgehoben) im selben Commit nachziehen.

## Nicht-Ziele (YAGNI)

- **Kein** externer UI-Pack-Lader / kein eigenes `ui-api`-Modul / kein ABI-Freeze (alles Phase-4-Soll).
  Der Slot-Vertrag bleibt bewusst in-tree, additiv erweiterbar.
- **Keine** der anderen Chrome-Regionen (overlay/tiles/nav/settings/dialog) — nur `homeHeader`.
- **Kein** Umbau der Tab-Logik / der ViewModels über das Nötige hinaus.

## Betroffene/neue Dateien (Richtwert)

**Neu:** `.claude/skills/plugin-domain/SKILL.md`; `app/ui/home/HomeHeader.kt` (`HomeHeaderState`,
`HomeHeaderSearch`, `HomeHeaderFilter`, `DefaultHomeHeader`); `app/ui/components/PluginFilterMenu.kt`;
Tests (`UiSlots.resolve`-Erweiterung).
**Geändert:** `app/ui/slots/UiSlots.kt` (homeHeader-Region); `app/ui/home/HomeScreen.kt` (baut Surface,
ruft Slot, Plugins-Chip → Filter raus); `.claude/rules/architecture-seams.md`,
`.claude/rules/big-picture-and-goals.md` (docs-match-code).

## Bezug

`big-picture-and-goals.md` (ui-modularity, Plugins, Geräteklassen), `architecture-seams.md`
(UI-Slot-Naht), `source-extensibility.md` + `source-agnostic-integration.md` (Naht-A-Rezept als
Vorbild fürs Capability-Rezept), `shared-structure-before-variants.md` (DRY der Filter-Mechanik).
Gehört zu [[project-komga-eink-reader]].
