# Modularer Home-Header + plugin-domain-Skill — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den ganzen Home-Header als austauschbaren `HomeHeaderSlot` über eine **Capability-Surface** (`HomeHeaderState`) modular machen — Pack ordnet, Core besitzt die Logik („UI neu, Kernlogik gleich") — und das Prinzip vorher in einem `plugin-domain`-Skill festhalten.

**Architecture:** Reihenfolge: (A) `plugin-domain`-Skill schreiben (Philosophie + Plugin-Bau + Pluggable-Subsystem-Rezept), (B) Header bauen — `HomeHeaderState`/`HomeHeaderSlot` in der bestehenden `UiSlots`-Naht, `DefaultHomeHeader` als heutiges Layout, `HomeScreen` baut die Surface pro Tab; Plugins-Filter Chip→Icon (DRY mit Library, geteiltes `FilterRow` + `PluginFilterMenu`), (C) Validierungs-Schleife `skill-writer`. E-Ink-Invarianten bleiben host-erzwungen.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, JUnit (pure `resolve`-Test), Emulator-E2E (`eink_test`).

**Spec:** `docs/superpowers/specs/2026-06-12-modular-home-header-plugin-domain-design.md`

---

## Dateistruktur

**Neu:**
- `.claude/skills/plugin-domain/SKILL.md` — Plugin-Philosophie + Bau + Pluggable-Subsystem-Rezept
- `app/src/main/kotlin/com/komgareader/app/ui/home/HomeHeader.kt` — `HomeHeaderState`/`HomeHeaderSearch`/`HomeHeaderFilter` + `DefaultHomeHeader`
- `app/src/main/kotlin/com/komgareader/app/ui/components/PluginFilterMenu.kt` — Plugins-Typ-Filter-Menü (Einfach-Auswahl)
- `app/src/debug/kotlin/com/komgareader/app/ui/home/HomeHeaderSlotPreview.kt` — Swap-Beweis (`@Preview`, debug-only)

**Geändert:**
- `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt` — zweite Region `homeHeader`
- `app/src/test/kotlin/com/komgareader/app/ui/slots/SlotFallbackTest.kt` — `homeHeader`-Fallback
- `app/src/main/kotlin/com/komgareader/app/ui/components/AnchoredMenuPopup.kt` — geteiltes `FilterRow`
- `app/src/main/kotlin/com/komgareader/app/ui/components/TypeFilterMenu.kt` — nutzt geteiltes `FilterRow`
- `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt` — baut `HomeHeaderState`, ruft Slot; Plugins-Chip→Icon; `PluginFilterChip` entfällt
- `.claude/rules/architecture-seams.md`, `.claude/rules/big-picture-and-goals.md` — docs-match-code

---

## Phase 0 — `plugin-domain`-Skill (Deliverable A, zuerst)

### Task 1: `plugin-domain`-Skill schreiben

**Files:**
- Create: `.claude/skills/plugin-domain/SKILL.md`

Folge der `writing-skills`-Disziplin (klare `name`/`description`-Frontmatter, Trigger im `description`,
knapp, verweist statt dupliziert). Deutsch, echte Umlaute, `[[wikilink]]`-Bezüge, Soll/Ist getrennt.

- [ ] **Step 1: SKILL.md schreiben** — mit dieser Struktur und Inhalt (kein Code, reine Doku):

Frontmatter:
```markdown
---
name: plugin-domain
description: Use when building or extending plugins (source/preset/UI), designing the plugin/UI-pack ABI, or making ANY subsystem pluggable in the Komga-Reader. Bündelt die Plugin-Philosophie, wie ein Plugin gebaut wird, und das Rezept für ein neues plugbares Subsystem (Capability-Surface, "UI neu, Kernlogik gleich").
---
```

Drei Säulen (Abschnitte):
1. **Plugin-Philosophie** — Core bleibt / Chrome + Capabilities austauschbar; Variabilität hinter Nähten;
   **deklarativ statt arbiträrer Compose-Code** (Pack/Plugin liefert *Beschreibung*, Host rendert +
   erzwingt E-Ink-Invarianten); ABI-Gate = zwei Ints (additiv, neue Capability = neues optionales
   Interface); die drei Plugin-Typen + Reihenfolge (Preset → Quelle → UI/Capability, zuletzt/riskant);
   TOFU-Signatur-Pinning; `plugin-sdk` als einziges `compileOnly`-Artefakt; **„UI neu, Kernlogik gleich"**
   (Pack ordnet Capabilities, implementiert sie nie neu).
2. **Wie ein Plugin gebaut wird** — `plugin-sdk` `compileOnly`, Manifest-Metadata, Discovery via
   `PluginHost`, Signatur/Fingerprint, Repo-Index. Referenzen: Kavita-Quelle + Color-Preset-Plugin.
3. **Rezept: ein neues Subsystem plugbar machen** (5 Schritte): (1) Capability-Vertrag definieren
   (benannter Satz Daten+Callbacks, kein arbiträrer Code); (2) Host besitzt die Logik (Surface bauen);
   (3) In-Tree-Slot mit Default (fehlt → Default, nie null, analog `StubSource`); (4) ABI-fähig schneiden
   (E-Ink-Invarianten host-erzwungen, nie Pack); (5) Pack/Plugin ordnet/liefert, implementiert nie die Logik.

Bezüge unten: `[[project-komga-eink-reader]]`, Rules `architecture-seams.md`, `source-extensibility.md`,
`source-agnostic-integration.md`, `big-picture-and-goals.md`, `shared-structure-before-variants.md`.
Platzhalter `[[modular-home-header]]` als künftiges Referenz-Beispiel (wird in Phase 5 real verlinkt).

- [ ] **Step 2: Gegen die `writing-skills`-Checkliste prüfen** — `description` beginnt mit „Use when…",
  enthält Trigger; Skill ist self-contained; keine Phantom-Typen (jeder genannte Typ existiert im Code:
  `PluginHost`/`plugin-sdk`/`SourcePlugin`/`ColorPresetSpec` per `grep` verifizieren).

Run: `grep -rn "class PluginHost\|SourcePlugin\|ColorPresetSpec" plugin-host plugin-api source-api 2>/dev/null | head`
Expected: Treffer für alle genannten Typen (sonst Namen im Skill korrigieren).

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/plugin-domain/SKILL.md
git commit -m "docs(skill): plugin-domain — Plugin-Philosophie + Pluggable-Subsystem-Rezept"
```

---

## Phase 1 — Capability-Surface + Slot-Vertrag

### Task 2: `HomeHeaderState` + `DefaultHomeHeader` (Capability-Surface)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeHeader.kt`

Die Surface ist host-gebaut; `DefaultHomeHeader` rendert sie im heutigen Onyx-Layout (verhaltensgleich
zum aktuellen inline-`TopAppBar`-Block). Den aktuellen Block aus `HomeScreen.kt` (Zeilen ~140–283) als
Vorlage nehmen, aber datengetrieben aus `HomeHeaderState`.

- [ ] **Step 1: `HomeHeader.kt` schreiben**

```kotlin
package com.komgareader.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.EinkSearchBar

/**
 * Die **Capability-Surface** des Home-Headers: ein benannter Satz Fähigkeiten + Callbacks, den der
 * Host (Core) baut und ein [com.komgareader.app.ui.slots.HomeHeaderSlot] (Pack) **arrangiert** — nie
 * neu implementiert („UI neu, Kernlogik gleich"). ABI-fähig geschnitten; E-Ink-Invarianten bleiben
 * host-erzwungen (`LocalDisplayBehavior`/`LocalDesignTokens`), sind NICHT Teil der Surface.
 */
data class HomeHeaderState(
    val status: @Composable () -> Unit,
    val search: HomeHeaderSearch,
    val filter: HomeHeaderFilter?,
    val menu: @Composable () -> Unit,
    val actions: @Composable RowScope.() -> Unit,
)

/** Such-Fähigkeit: Text + Callbacks + optionaler `leading`-Inhalt (z. B. Library-Filter-Chips). */
data class HomeHeaderSearch(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val placeholder: String,
    val actionLabel: String,
    val clearLabel: String?,
    val onClear: (() -> Unit)?,
    val leading: (@Composable RowScope.() -> Unit)?,
)

/** Generischer Filter-Icon-Slot — Library UND Plugins teilen ihn (DRY). Das zugehörige Menü liefert
 *  der Host über [HomeHeaderState.menu]; hier nur Icon + Anchor + Klick. */
data class HomeHeaderFilter(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val onAnchor: (IntOffset) -> Unit,
)

/**
 * Das mitgelieferte Default-Layout (Onyx-Look): StatusCluster links · zentrierte Suche (max 408dp) ·
 * 40dp-Filter-Icon-Slot · Rechts-Aktionen · Menü-Overlay. Verhaltensgleich zum bisherigen
 * inline-`TopAppBar`-Block in HomeScreen.
 */
@Composable
fun DefaultHomeHeader(state: HomeHeaderState) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart)) { state.status() }
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth(0.62f).widthIn(max = 408.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkSearchBar(
                query = state.search.query,
                onQueryChange = state.search.onQueryChange,
                onSubmit = state.search.onSubmit,
                placeholder = state.search.placeholder,
                actionLabel = state.search.actionLabel,
                clearLabel = state.search.clearLabel,
                onClear = state.search.onClear,
                leading = state.search.leading,
                modifier = Modifier.weight(1f),
            )
            // Filter-Slot: feste Breite auf ALLEN Tabs (Suchfeld überall gleich breit). Nur gefüllt,
            // wenn der Tab eine Filter-Capability liefert.
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                state.filter?.let { f ->
                    IconButton(
                        onClick = f.onClick,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            f.onAnchor(
                                IntOffset(
                                    (pos.x + coords.size.width).toInt(),
                                    (pos.y + coords.size.height).toInt(),
                                ),
                            )
                        },
                    ) {
                        Icon(f.icon, contentDescription = f.contentDescription)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            state.actions()
        }
        state.menu()
    }
}
```

- [ ] **Step 2: Build (noch ungenutzt)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (unused-Warnungen ok).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/home/HomeHeader.kt
git commit -m "feat(app): HomeHeaderState (Capability-Surface) + DefaultHomeHeader"
```

### Task 3: `UiSlots` um die `homeHeader`-Region erweitern (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/slots/SlotFallbackTest.kt`

- [ ] **Step 1: Failing test ergänzen** (an die bestehende `SlotFallbackTest` anhängen — erst die Datei
  lesen, das Muster spiegeln). Falls die Datei nicht existiert, neu anlegen:

```kotlin
package com.komgareader.app.ui.slots

import org.junit.Assert.assertSame
import org.junit.Test

class HomeHeaderSlotFallbackTest {

    private val customHomeHeader: HomeHeaderSlot = { /* no-op pack */ }

    @Test
    fun `resolve falls back to default homeHeader when pack provides none`() {
        val resolved = UiSlots.resolve(UiSlotPack())
        assertSame(DefaultSlots.homeHeader, resolved.homeHeader)
    }

    @Test
    fun `resolve passes through pack homeHeader when provided`() {
        val resolved = UiSlots.resolve(UiSlotPack(homeHeader = customHomeHeader))
        assertSame(customHomeHeader, resolved.homeHeader)
    }
}
```

- [ ] **Step 2: Rot** — `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.slots.HomeHeaderSlotFallbackTest"`
  Expected: FAIL (`HomeHeaderSlot`/`homeHeader` ungelöst).

- [ ] **Step 3: `UiSlots.kt` erweitern** — `HomeHeaderSlot`-typealias, `UiSlotPack.homeHeader`,
  `ResolvedSlots.homeHeader`, `UiSlots.resolve`, `DefaultSlots.homeHeader`:

```kotlin
import com.komgareader.app.ui.home.DefaultHomeHeader
import com.komgareader.app.ui.home.HomeHeaderState

/** Vertrag der Home-Header-Region: bekommt die [HomeHeaderState]-Capability-Surface und arrangiert
 *  sie. Breiter als [HeaderSlot] (der nur title/onBack/actions kann), weil der Home-Header Status +
 *  Suche + Filter + Tab-Aktionen trägt. Ein Pack ordnet/restyled, implementiert die Logik nie. */
typealias HomeHeaderSlot = @Composable (state: HomeHeaderState) -> Unit
```

`UiSlotPack` → `data class UiSlotPack(val header: HeaderSlot? = null, val homeHeader: HomeHeaderSlot? = null)`.
`ResolvedSlots` → `data class ResolvedSlots(val header: HeaderSlot, val homeHeader: HomeHeaderSlot)`.
`UiSlots.resolve` → `ResolvedSlots(header = pack.header ?: DefaultSlots.header, homeHeader = pack.homeHeader ?: DefaultSlots.homeHeader)`.
`DefaultSlots` → zusätzlich `val homeHeader: HomeHeaderSlot = { state -> DefaultHomeHeader(state) }`.

- [ ] **Step 4: Grün** — gleicher Test-Befehl → PASS (2 Tests). Zusätzlich bestehende `SlotFallbackTest`
  (header) muss weiter grün sein: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.slots.*"`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt app/src/test/kotlin/com/komgareader/app/ui/slots/
git commit -m "feat(app): UiSlots zweite Region homeHeader (Fallback-getestet)"
```

---

## Phase 2 — Geteiltes `FilterRow` + `PluginFilterMenu` (DRY)

### Task 4: `FilterRow` extrahieren + `PluginFilterMenu`

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/AnchoredMenuPopup.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/TypeFilterMenu.kt`
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/PluginFilterMenu.kt`

`FilterRow` ist heute **privat** in `TypeFilterMenu.kt` (Label links + Häkchen rechts). Plugins braucht
dieselbe Zeile → in `AnchoredMenuPopup.kt` als **öffentliches** `FilterRow` heben, beide Menüs nutzen es.

- [ ] **Step 1: `FilterRow` nach `AnchoredMenuPopup.kt` heben** — verbatim aus `TypeFilterMenu.kt`
  (die private `FilterRow`), aber `public`, ins Package `com.komgareader.app.ui.components`:

```kotlin
/** Eine Filter-Menü-Zeile: Label links + Häkchen rechts wenn aktiv (E-Ink-ruhig, kein Radio).
 *  Geteilt von [TypeFilterMenu] und [PluginFilterMenu]. */
@Composable
fun FilterRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
```
Nötige Imports in `AnchoredMenuPopup.kt` ergänzen (`clickable`, `fillMaxWidth`, `padding`, `size`, `Row`,
`Text`, `Icon`, `MaterialTheme`, `Alignment`, `AppIcons`).

- [ ] **Step 2: Die private `FilterRow` aus `TypeFilterMenu.kt` ENTFERNEN** (nutzt jetzt die geteilte —
  gleiches Package, kein Import nötig). Verifizieren, dass `TypeFilterMenu` weiter kompiliert.

- [ ] **Step 3: `PluginFilterMenu.kt` schreiben** (Einfach-Auswahl, Häkchen auf aktiv):

```kotlin
package com.komgareader.app.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.data.plugin.repo.PluginTypeFilter

/**
 * Filter-Menü des Plugins-Tabs: Einfach-Auswahl Alle/Quellen/Presets (Häkchen auf aktiv). Spiegelt
 * [TypeFilterMenu] (geteiltes [FilterRow] + [AnchoredMenuPopup]); klappt unter dem Filter-Icon auf.
 * Auswahl schließt das Menü (Einfach-Auswahl, kein Mehrfach-Toggle).
 */
@Composable
fun PluginFilterMenu(
    anchor: IntOffset,
    selected: PluginTypeFilter,
    onSelect: (PluginTypeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AnchoredMenuPopup(anchor = anchor, alignEnd = true, onDismiss = onDismiss) {
        FilterRow(label = s.pluginFilterAll, checked = selected == PluginTypeFilter.ALL) {
            onSelect(PluginTypeFilter.ALL); onDismiss()
        }
        HorizontalDivider()
        FilterRow(label = s.pluginFilterSources, checked = selected == PluginTypeFilter.SOURCES) {
            onSelect(PluginTypeFilter.SOURCES); onDismiss()
        }
        HorizontalDivider()
        FilterRow(label = s.pluginFilterPresets, checked = selected == PluginTypeFilter.PRESETS) {
            onSelect(PluginTypeFilter.PRESETS); onDismiss()
        }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, bestehende Tests grün.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/AnchoredMenuPopup.kt app/src/main/kotlin/com/komgareader/app/ui/components/TypeFilterMenu.kt app/src/main/kotlin/com/komgareader/app/ui/components/PluginFilterMenu.kt
git commit -m "refactor(app): geteiltes FilterRow + PluginFilterMenu (Einfach-Auswahl)"
```

---

## Phase 3 — `HomeScreen` an den Slot verdrahten + Plugins-Filter-Icon

### Task 5: `HomeScreen` baut `HomeHeaderState`, ruft den Slot; Plugins-Chip → Icon+Menü

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt`

Der inline-`TopAppBar`-Block (Zeilen ~138–285) wird ersetzt: pro Tab eine `HomeHeaderState` bauen und
`LocalResolvedSlots.current.homeHeader(state)` rendern. Der Plugins-`PluginFilterChip` entfällt zugunsten
eines `HomeHeaderFilter` + `PluginFilterMenu`.

- [ ] **Step 1: Neue States für das Plugins-Filtermenü** (bei den anderen Filter-States, ~Z. 93–94):

```kotlin
    var pluginFilterMenuOpen by remember { mutableStateOf(false) }
    var pluginFilterAnchor by remember { mutableStateOf(IntOffset.Zero) }
```

- [ ] **Step 2: `topBar`-Lambda ersetzen** — den gesamten `if (!collectionDetailOpen) TopAppBar(...)`-Block
  (Z. 138–285) durch Slot-Aufruf mit gebauter Surface ersetzen:

```kotlin
        topBar = {
            val collectionDetailOpen = selected == TAB_COLLECTIONS && openCollectionId != null
            if (!collectionDetailOpen) {
                val state = HomeHeaderState(
                    status = { StatusCluster() },
                    search = HomeHeaderSearch(
                        query = query,
                        onQueryChange = {
                            query = it
                            if (selected == TAB_PLUGINS) pluginsVm.setQuery(it)
                        },
                        onSubmit = { submitSearch() },
                        placeholder = if (onSettingsTab) s.searchSettingsHint
                            else if (selected == TAB_PLUGINS) s.pluginSearchHint
                            else s.searchMediaHint,
                        actionLabel = s.searchAction,
                        clearLabel = s.clearSearch,
                        onClear = {
                            query = ""
                            submitted = ""
                            if (selected == TAB_PLUGINS) pluginsVm.setQuery("")
                        },
                        leading = if (selected == TAB_LIBRARY && (typeFilter.value.isNotEmpty() || downloadedOnly)) {
                            {
                                typeFilter.value.forEach { type ->
                                    TypeFilterChip(
                                        label = s.localizedContentType(type),
                                        onRemove = { typeFilter.value = typeFilter.value - type },
                                    )
                                }
                                if (downloadedOnly) {
                                    TypeFilterChip(label = s.filterDownloaded, onRemove = { downloadedOnly = false })
                                }
                            }
                        } else null,
                    ),
                    filter = when (selected) {
                        TAB_LIBRARY -> HomeHeaderFilter(
                            icon = AppIcons.Filter,
                            contentDescription = s.filterByType,
                            onClick = { filterMenuOpen = true },
                            onAnchor = { filterAnchor = it },
                        )
                        TAB_PLUGINS -> HomeHeaderFilter(
                            icon = AppIcons.Filter,
                            contentDescription = s.filterByType,
                            onClick = { pluginFilterMenuOpen = true },
                            onAnchor = { pluginFilterAnchor = it },
                        )
                        else -> null
                    },
                    menu = {
                        if (filterMenuOpen && selected == TAB_LIBRARY) {
                            TypeFilterMenu(
                                anchor = filterAnchor,
                                selected = typeFilter.value,
                                onToggle = { type ->
                                    typeFilter.value =
                                        if (type in typeFilter.value) typeFilter.value - type
                                        else typeFilter.value + type
                                },
                                downloadedSelected = downloadedOnly,
                                onToggleDownloaded = { downloadedOnly = !downloadedOnly },
                                onDismiss = { filterMenuOpen = false },
                            )
                        }
                        if (pluginFilterMenuOpen && selected == TAB_PLUGINS) {
                            PluginFilterMenu(
                                anchor = pluginFilterAnchor,
                                selected = pluginTypeFilter,
                                onSelect = { pluginsVm.setTypeFilter(it) },
                                onDismiss = { pluginFilterMenuOpen = false },
                            )
                        }
                    },
                    actions = {
                        when (selected) {
                            TAB_LIBRARY -> IconButton(onClick = { libraryVm.refresh() }) {
                                Icon(AppIcons.Refresh, contentDescription = null)
                            }
                            TAB_PLUGINS -> {
                                IconButton(onClick = { showRepoMgmt = true }) {
                                    Icon(AppIcons.Settings, contentDescription = s.repoBrowserManage)
                                }
                                IconButton(onClick = { pluginsVm.reload() }) {
                                    Icon(AppIcons.Refresh, contentDescription = s.repoBrowserRefresh)
                                }
                            }
                            TAB_COLLECTIONS -> if (openCollectionId == null) {
                                IconButton(onClick = { showCreateCollection = true }) {
                                    Icon(AppIcons.Plus, contentDescription = s.newCollection)
                                }
                                RotatingViewModeButton(
                                    current = collectionsViewMode,
                                    onSelect = { collectionsVm.setViewMode(it.name) },
                                    listLabel = s.viewList,
                                    tileLabel = s.viewTile,
                                    largeTileLabel = s.viewLargeTile,
                                )
                                IconButton(onClick = { collectionsVm.syncNow() }) {
                                    Icon(AppIcons.Refresh, contentDescription = s.collectionSyncNow)
                                }
                            }
                            TAB_GROUPS -> {
                                IconButton(onClick = { showCreateGroup = true }) {
                                    Icon(AppIcons.Plus, contentDescription = s.newGroup)
                                }
                                RotatingViewModeButton(
                                    current = groupsViewMode,
                                    onSelect = { groupsVm.setViewMode(it.name) },
                                    listLabel = s.viewList,
                                    tileLabel = s.viewTile,
                                    largeTileLabel = s.viewLargeTile,
                                )
                            }
                        }
                    },
                )
                LocalResolvedSlots.current.homeHeader(state)
            }
        },
```

- [ ] **Step 3: `PluginFilterChip` löschen** (die private Composable, ~Z. 364–384) — wird nicht mehr genutzt.

- [ ] **Step 4: `pluginFilterMenuOpen` im `onSelect`-Reset** der `EinkBottomBar` ergänzen
  (bei `showRepoMgmt = false`): `pluginFilterMenuOpen = false`.

- [ ] **Step 5: Imports** — ergänzen: `com.komgareader.app.ui.slots.LocalResolvedSlots`,
  `com.komgareader.app.ui.components.PluginFilterMenu`. Prüfen, ob `TopAppBar`-Import jetzt ungenutzt ist
  (der Slot rendert keine `TopAppBar` mehr direkt im HomeScreen — `DefaultHomeHeader` nutzt auch keine
  `TopAppBar`, sondern eine `Box`): falls ungenutzt, entfernen. `StatusCluster`, `EinkSearchBar`,
  `IntOffset` bleiben genutzt.

> **Wichtig zur `TopAppBar`:** Das bisherige Layout steckte in `TopAppBar(title = { Box {...} })`.
> `DefaultHomeHeader` rendert die `Box` **ohne** `TopAppBar`-Wrapper. Das ist beabsichtigt (der Slot
> liefert den Header-Inhalt; das `Scaffold` gibt ihm den oberen Bereich). Verifiziere im E2E, dass die
> Höhe/Padding stimmen — falls der Header zu eng oben sitzt, in `DefaultHomeHeader` die `Box` in eine
> `TopAppBar(title = { … })` wrappen (dann `TopAppBar`-Import dorthin) oder ein oberes Padding ergänzen.

- [ ] **Step 6: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt
git commit -m "feat(app): HomeScreen rendert HomeHeaderSlot; Plugins-Filter Chip->Icon+Menü"
```

---

## Phase 4 — Swap-Beweis + docs-match-code

### Task 6: `@Preview`-Swap-Beweis + Regeln nachziehen

**Files:**
- Create: `app/src/debug/kotlin/com/komgareader/app/ui/home/HomeHeaderSlotPreview.kt`
- Modify: `.claude/rules/architecture-seams.md`, `.claude/rules/big-picture-and-goals.md`

- [ ] **Step 1: Swap-Beweis-Preview** (debug-only, KEINE Nutzer-Einstellung) — ein Alternativ-Layout,
  das dieselbe `HomeHeaderState` anders anordnet (Beweis: ganzes Layout via Pack ersetzbar):

```kotlin
package com.komgareader.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Swap-Beweis: ein alternatives Home-Header-Layout (Status oben, Suche+Aktionen darunter), das
 * dieselbe [HomeHeaderState]-Surface anders anordnet — ohne Tab-Logik anzufassen. Belegt, dass ein
 * UI-Pack den ganzen Header ersetzen kann. NUR Debug/Preview, keine Nutzer-Einstellung.
 */
@Composable
fun AlternativeHomeHeader(state: HomeHeaderState) {
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.status()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.actions()
        }
        state.menu()
    }
}

@Preview(widthDp = 1264, heightDp = 200)
@Composable
private fun AlternativeHomeHeaderPreview() {
    AlternativeHomeHeader(
        HomeHeaderState(
            status = {},
            search = HomeHeaderSearch("", {}, {}, "Suche", "", null, null, null),
            filter = null,
            menu = {},
            actions = {},
        ),
    )
}
```

- [ ] **Step 2: `architecture-seams.md` nachziehen** — die „UI-Slot-Naht / Chrome"-Sektion: zweite
  Region `homeHeader` als Ist ergänzen (Capability-Surface `HomeHeaderState`, `HomeHeaderSlot`,
  `DefaultHomeHeader`); die bisherige „Ausnahme HomeScreen" als **aufgehoben** markieren.

- [ ] **Step 3: `big-picture-and-goals.md` nachziehen** — im ui-modularity-`docs-match-code`-Absatz:
  HomeScreen-Header ist jetzt über `LocalResolvedSlots.current.homeHeader` swappbar (zweite gebaute
  Region nach `header`); Capability-Surface-Prinzip („UI neu, Kernlogik gleich") notieren. Die
  „Ausnahme HomeScreen passt nicht in HeaderSlot v1"-Stelle entsprechend korrigieren.

- [ ] **Step 4: Build (Preview kompiliert in Debug)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/debug/kotlin/com/komgareader/app/ui/home/HomeHeaderSlotPreview.kt .claude/rules/architecture-seams.md .claude/rules/big-picture-and-goals.md
git commit -m "docs+feat(app): HomeHeader-Swap-Beweis + UI-Slot-Naht homeHeader (docs-match-code)"
```

### Task 7: E2E auf `eink_test`

**Vorbedingung:** Emulator `eink_test` (1264×1680@300) läuft.

- [ ] **Step 1: Installieren** — `./gradlew :app:installDebug`

- [ ] **Step 2: Visuell/Screenshot verifizieren:**
  1. Alle 5 Tabs: Header rendert (StatusCluster links, Suche mittig, Tab-Aktionen rechts) — kein
     Layout-Bruch ggü. vorher.
  2. Library: Filter-Icon rechts neben Suche → Menü (Typ + Downloaded) → Filter wirkt; aktive
     Filter-Chips im Suchfeld.
  3. Plugins: **Filter-Icon** rechts neben Suche (nicht mehr Chip) → `PluginFilterMenu` (Alle/Quellen/
     Presets, Häkchen auf aktiv) → Auswahl filtert die Liste + schließt das Menü.
  4. Plugins: Suche, ⚙ Repo-Settings, ⟳ Reload weiter funktional.
  5. Tab-Wechsel räumt Such-/Filter-State (kein Leak).

- [ ] **Step 3: Gesamt-Test**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Unit-Tests grün.

- [ ] **Step 4: Commit (falls E2E-Anpassungen)** — sonst überspringen.

---

## Phase 5 — Validierungs-Schleife (Skill ↔ Header)

### Task 8: `skill-writer` validiert Header gegen `plugin-domain`-Skill, beide angleichen

**Files:**
- Modify: `.claude/skills/plugin-domain/SKILL.md` (Ergänzungen aus der Validierung)
- Ggf. Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeHeader.kt` u. a. (Header-Verbesserungen)

- [ ] **Step 1: `writing-skills` (skill-writer) anwenden** — prüfe den gebauten Header gegen den
  `plugin-domain`-Skill: Folgt `HomeHeaderState` dem Capability-Surface-Rezept (benannte Fähigkeiten,
  keine Logik im Slot)? Ist „UI neu, Kernlogik gleich" eingehalten (der `HomeHeaderSlot` ordnet nur,
  implementiert nie Suche/Sync/Filter)? Sind E-Ink-Invarianten host-erzwungen? Lücken notieren.

- [ ] **Step 2: Skill ergänzen** — fehlende Prinzipien/Anti-Patterns, die der Header aufgedeckt hat, in
  den Skill aufnehmen (z. B. „Menü-Overlay gehört in die Surface, nicht in den Slot", „Filter-Mechanik
  einmal im Default, nicht pro Tab"). Den Header als Referenz-Beispiel real verlinken: in `MEMORY.md`/
  einem Memo `[[modular-home-header]]` anlegen ODER im Skill direkt auf `app/ui/home/HomeHeader.kt`
  verweisen (kein Phantom-Link).

- [ ] **Step 3: Header verbessern** — falls die Validierung eine Abweichung fand (z. B. eine Fähigkeit,
  die noch Logik im Slot trägt), den Header so anpassen, dass er dem Skill folgt. Build + Tests grün halten.

- [ ] **Step 4: Build + Commit**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
git add .claude/skills/plugin-domain/SKILL.md app/src/main/kotlin/com/komgareader/app/ui/home/
git commit -m "docs+refactor: plugin-domain-Skill und Header angeglichen (Validierungs-Schleife)"
```

---

## Selbst-Review-Notizen (vor Übergabe geprüft)

- **Spec-Abdeckung:** A Skill (Task 1) · B Capability-Surface (Task 2) + Slot (Task 3) + Plugins-Filter
  DRY (Task 4) + HomeScreen-Wiring/Chip→Icon (Task 5) + Swap-Beweis/docs (Task 6) + E2E (Task 7) ·
  C Validierungs-Schleife (Task 8). Vollständig.
- **Typ-Konsistenz:** `HomeHeaderState`/`HomeHeaderSearch`/`HomeHeaderFilter` durchgehend gleich;
  `HomeHeaderSlot` = `@Composable (HomeHeaderState) -> Unit`; `DefaultSlots.homeHeader` ↔
  `ResolvedSlots.homeHeader` ↔ `UiSlotPack.homeHeader`; `FilterRow`/`PluginFilterMenu`/`PluginTypeFilter`
  matchen zwischen Task 4 und Task 5.
- **Offene Verifikation beim Bau:** `SlotFallbackTest`-Dateiname/Package vor Anhängen prüfen (Task 3);
  `TopAppBar`-Wrapper-Höhe im E2E (Task 5 Step 5-Hinweis); `AnchoredMenuPopup.kt`-Importe (Task 4).
