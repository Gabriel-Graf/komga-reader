# Modulare UI: Shell-Pack — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das ganze Home-Layout-Skelett hinter eine Capability-Surface (`AppShellState`) legen, sodass ein zweiter Shell-Pack (Phone, Drawer/Rail) dieselbe Funktionalität fundamental anders anordnen kann — Form-Faktor-getrieben, ohne Kern-Umbau.

**Architecture:** Core (`HomeShellHost`) baut `AppShellState` aus benannten Stücken (Nav als Daten, content/header als host-gebaute Composables). Ein `ShellPack` (`@Composable (AppShellState) -> Unit`) ordnet an; `DefaultShell` = heutige Bottom-Bar (pixelgleich), `PhoneShell` = Drawer (Swap-Beweis). Auswahl per `formFactorFor(screenWidthDp)` über `ShellPackRegistry`, orthogonal zur Geräteklasse (Theme). NavHost/Reader bleiben unberührt (Geschwister-Routen).

**Tech Stack:** Kotlin, Jetpack Compose, Material3, JUnit5 (pure Tests), Hilt-VMs (bestehend), Emulator `eink_test`.

**Spec:** `docs/superpowers/specs/2026-06-12-modular-ui-shell-pack-design.md`

---

## Dateistruktur

- Create `app/src/main/kotlin/com/komgareader/app/ui/shell/AppShellState.kt` — Capability-Surface (`AppShellState`, `ShellDestination`, `ShellDestinationId`).
- Create `app/src/main/kotlin/com/komgareader/app/ui/shell/ShellPack.kt` — `ShellPack` fun-interface, `ShellFormFactor`, pures `formFactorFor`, `ShellPackRegistry`.
- Create `app/src/main/kotlin/com/komgareader/app/ui/shell/DefaultShell.kt` — heutige Bottom-Bar-Anordnung (aus `HomeScreen` extrahiert).
- Create `app/src/main/kotlin/com/komgareader/app/ui/shell/PhoneShell.kt` — Drawer/Rail-Anordnung (Phase C).
- Modify `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt` — wird `HomeShellHost`: baut `AppShellState`, löst Pack auf, ruft es. Keine Anordnungs-Logik mehr.
- Test `app/src/test/kotlin/com/komgareader/app/ui/shell/ShellSelectionTest.kt` — `formFactorFor` + `ShellPackRegistry`-Auflösung (pure).
- Modify `.claude/rules/architecture-seams.md` — Shell-Pack-Region im Ist-Stand nachziehen (Phase D).
- Rename `.claude/skills/plugin-domain/` → `.claude/skills/komga-plugins/` + Inhalt (Phase D).

---

## Phase A — Capability-Surface + Default-Shell (verhaltens-erhaltend)

### Task A1: Surface-Typen anlegen

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/shell/AppShellState.kt`

- [ ] **Step 1: Typen schreiben**

```kotlin
package com.komgareader.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.komgareader.app.ui.home.HomeHeaderState

/** Stabile, geräteunabhängige Identität einer Home-Destination. Die Reihenfolge in [AppShellState.destinations]
 *  bestimmt die Anzeige-Reihenfolge; ein Pack ordnet sie an, ändert die Menge aber nie. */
enum class ShellDestinationId { LIBRARY, COLLECTIONS, GROUPS, PLUGINS, SETTINGS }

/**
 * Eine Home-Destination als **benanntes, einzeln renderbares Stück**. `icon`/`label`/`id` sind reine
 * Nav-Daten (das Pack baut daraus sein Nav-Control — die Widget-Wahl IST die Variabilität). `header`
 * (schon gebaute Surface) und `content` (host-gebauter Screen mit voller Logik) werden vom Pack nur
 * platziert, nie nachgebaut. Vgl. `HomeHeaderState` — dieselbe Form eine Ebene höher.
 */
data class ShellDestination(
    val id: ShellDestinationId,
    val icon: ImageVector,
    val label: String,
    val header: HomeHeaderState,
    val content: @Composable () -> Unit,
)

/**
 * Die Capability-Surface des Home-Skeletts. Der Host (Core) baut sie; ein [ShellPack] arrangiert sie.
 * **Satz benannter Stücke, kein opaker Blob** — das trägt den 1→3-Pfad (deklarativer Deskriptor später).
 * E-Ink-Invarianten (Bewegung/Akzent) sind NICHT Teil der Surface — host-erzwungen.
 */
data class AppShellState(
    val destinations: List<ShellDestination>,
    val selectedId: ShellDestinationId,
    val onSelect: (ShellDestinationId) -> Unit,
) {
    val selected: ShellDestination get() = destinations.first { it.id == selectedId }
}
```

- [ ] **Step 2: Kompiliert**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/shell/AppShellState.kt
git commit -m "feat(app): AppShellState capability-surface (shell-pack naht)"
```

### Task A2: ShellPack-Vertrag + Form-Faktor-Auflösung (pure, TDD)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/shell/ShellPack.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/shell/ShellSelectionTest.kt`

- [ ] **Step 1: Failing test schreiben**

```kotlin
package com.komgareader.app.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ShellSelectionTest {

    @Test
    fun `schmale breite ist compact`() {
        assertEquals(ShellFormFactor.COMPACT, formFactorFor(widthDp = 411))
    }

    @Test
    fun `600dp und mehr ist expanded`() {
        assertEquals(ShellFormFactor.EXPANDED, formFactorFor(widthDp = 600))
        assertEquals(ShellFormFactor.EXPANDED, formFactorFor(widthDp = 1264))
    }

    @Test
    fun `expanded waehlt die default-shell`() {
        assertSame(DefaultShell, ShellPackRegistry.forFormFactor(ShellFormFactor.EXPANDED))
    }

    @Test
    fun `compact waehlt die phone-shell`() {
        assertSame(PhoneShell, ShellPackRegistry.forFormFactor(ShellFormFactor.COMPACT))
    }
}
```

- [ ] **Step 2: Test schlägt fehl (Typen fehlen)**

Run: `./gradlew :app:testDebugUnitTest --tests "*ShellSelectionTest*"`
Expected: FAIL — unresolved reference `formFactorFor`/`ShellFormFactor`/`ShellPackRegistry`/`DefaultShell`/`PhoneShell`

- [ ] **Step 3: Vertrag + Auflösung implementieren**

> Hinweis: `DefaultShell`/`PhoneShell` sind `ShellPack`-Objekte; ihre Composable-Bodies kommen in A3/C1. Hier nur als Objekte mit leerem Body anlegen, damit Selektion testbar ist. In A3 wird `DefaultShell`s Body gefüllt.

```kotlin
package com.komgareader.app.ui.shell

import androidx.compose.runtime.Composable

/** Vertrag: ein Pack ordnet die [AppShellState]-Stücke zu einem ganzen Home-Skelett an. Built-ins sind
 *  Compose (Ansatz 1); externe deklarative Packs (Ansatz 3) interpretiert später eine `DeclarativeShell`
 *  über dieselbe Surface. */
fun interface ShellPack {
    @Composable fun Render(state: AppShellState)
}

/** Form-Faktor-Achse (Bildschirmbreite), orthogonal zur Geräteklasse (Theme). */
enum class ShellFormFactor { COMPACT, EXPANDED }

/** Pure Auflösung: <600dp = compact (Phone), sonst expanded (Tablet/E-Ink). Unit-testbar, Compose-frei. */
fun formFactorFor(widthDp: Int): ShellFormFactor =
    if (widthDp < 600) ShellFormFactor.COMPACT else ShellFormFactor.EXPANDED

/**
 * Registry der Shell-Packs — analog [com.komgareader.app.ui.theme.UiPackRegistry], eine Schicht höher.
 * Heute zwei Built-ins, Auswahl nach Form-Faktor. Hier hängt sich später ein externer APK-Pack-Lader
 * (Ansatz 3, Phase 4) und ein manueller User-Override ein.
 */
object ShellPackRegistry {
    fun forFormFactor(formFactor: ShellFormFactor): ShellPack = when (formFactor) {
        ShellFormFactor.COMPACT -> PhoneShell
        ShellFormFactor.EXPANDED -> DefaultShell
    }
}
```

> `DefaultShell` und `PhoneShell` werden als `object ... : ShellPack` in A3 bzw. C1 angelegt. Damit A2 kompiliert, lege **jetzt** beide als minimale Objekte an (Body in A3/C1 ersetzt):

In `DefaultShell.kt` (Stub, A3 füllt den Body):
```kotlin
package com.komgareader.app.ui.shell

import androidx.compose.runtime.Composable

object DefaultShell : ShellPack {
    @Composable override fun Render(state: AppShellState) { /* A3 */ }
}
```

In `PhoneShell.kt` (Stub, C1 füllt den Body):
```kotlin
package com.komgareader.app.ui.shell

import androidx.compose.runtime.Composable

object PhoneShell : ShellPack {
    @Composable override fun Render(state: AppShellState) { /* C1 */ }
}
```

- [ ] **Step 4: Test grün**

Run: `./gradlew :app:testDebugUnitTest --tests "*ShellSelectionTest*"`
Expected: PASS (4 Tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/shell/ShellPack.kt app/src/main/kotlin/com/komgareader/app/ui/shell/DefaultShell.kt app/src/main/kotlin/com/komgareader/app/ui/shell/PhoneShell.kt app/src/test/kotlin/com/komgareader/app/ui/shell/ShellSelectionTest.kt
git commit -m "feat(app): ShellPack-vertrag + form-faktor-auflösung (pure, getestet)"
```

### Task A3: `DefaultShell` aus `HomeScreen` extrahieren (Rendering)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/shell/DefaultShell.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt:127-329`

Ziel: Die **Anordnung** (Scaffold + `homeHeader`-Slot oben + `when`-Content + `EinkBottomBar` unten + `LocalContentBottomInset`-Mechanik) wandert nach `DefaultShell`. `DefaultShell` rendert **nur** aus `AppShellState` — kein Wissen über Tabs/VMs/Dialoge.

- [ ] **Step 1: `DefaultShell.Render` füllen**

```kotlin
package com.komgareader.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.app.ui.components.EinkBottomBar
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.slots.LocalResolvedSlots

/**
 * Mitgeliefertes E-Ink/Tablet-Skelett: persistenter Home-Header oben (über den [homeHeader]-Slot),
 * Inhalt der aktiven Destination dahinter, schwebende [EinkBottomBar] als Overlay unten. Verhaltens-
 * und pixelgleich zum bisherigen `HomeScreen`-Scaffold. Ordnet NUR an — keine Tab-/VM-/Dialog-Logik.
 */
@OptIn(ExperimentalMaterial3Api::class)
object DefaultShell : ShellPack {
    @Composable
    override fun Render(state: AppShellState) {
        val slots = LocalResolvedSlots.current
        Scaffold(
            topBar = { slots.homeHeader(state.selected.header) },
        ) { inner ->
            var barHeightPx by remember { mutableIntStateOf(0) }
            val barInset = with(LocalDensity.current) { barHeightPx.toDp() }
            Box(Modifier.fillMaxSize().padding(inner)) {
                CompositionLocalProvider(LocalContentBottomInset provides barInset) {
                    Box(Modifier.fillMaxSize()) { state.selected.content() }
                }
                EinkBottomBar(
                    items = state.destinations.map { BottomNavItem(it.icon, it.label) },
                    selectedIndex = state.destinations.indexOfFirst { it.id == state.selectedId },
                    onSelect = { idx -> state.onSelect(state.destinations[idx].id) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { barHeightPx = it.height },
                )
            }
        }
    }
}
```

- [ ] **Step 2: `HomeScreen` zu `HomeShellHost` umbauen** — baut `AppShellState`, ruft den aufgelösten Pack

Ersetze den `Scaffold { ... }`-Block (heute `HomeScreen.kt:127-328`) durch: (a) die `ShellDestination`-Liste bauen, (b) Pack auflösen + rendern. Der **gesamte** State + die Header-Surface-Konstruktion (heute Zeilen 79-254) **bleibt** im Host; nur die *Anordnung* (Scaffold/Bar/when) ist weg. Die per-Tab-`HomeHeaderState` (heute inline in `topBar`) wird **pro Destination** gebaut.

Schlüssel-Struktur des neuen Host-Endes (statt des alten `Scaffold`):

```kotlin
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val pack = ShellPackRegistry.forFormFactor(formFactorFor(configuration.screenWidthDp))

    // Pro Destination eine Header-Surface bauen (dieselbe Logik wie bisher im topBar-when, jetzt
    // pro Tab gekapselt). headerFor(...) liefert HomeHeaderState für den jeweiligen Tab.
    val destinations = listOf(
        ShellDestination(ShellDestinationId.LIBRARY, AppIcons.Library, s.tabBrowse,
            header = libraryHeader(...), content = { LibraryScreen(...) }),
        ShellDestination(ShellDestinationId.COLLECTIONS, AppIcons.Bookmark, s.collections,
            header = collectionsHeader(...), content = { /* Collections list/detail */ }),
        ShellDestination(ShellDestinationId.GROUPS, AppIcons.Groups, s.tabGroups,
            header = groupsHeader(...), content = { GroupsScreen(...) }),
        ShellDestination(ShellDestinationId.PLUGINS, AppIcons.Plugins, s.navPlugins,
            header = pluginsHeader(...), content = { PluginsScreen(...) }),
        ShellDestination(ShellDestinationId.SETTINGS, AppIcons.Settings, s.settingsTitle,
            header = settingsHeader(...), content = { SettingsScreen(...) }),
    )
    val selectedId = ShellDestinationId.entries[selected]
    pack.Render(
        AppShellState(
            destinations = destinations,
            selectedId = selectedId,
            onSelect = { id -> onSelect(ShellDestinationId.entries.indexOf(id)) },
        )
    )
```

> **Extraktions-Regel (DRY):** Die heutige `topBar`-`HomeHeaderState`-Konstruktion (Zeilen 133-252) ist ein einziges `when(selected)`. Zerlege sie in pro-Tab-Helfer (`libraryHeader()`/`collectionsHeader()`/…) **als private `@Composable`-Funktionen in `HomeScreen.kt`**, die je `HomeHeaderState` zurückgeben. Den State (`query`, `typeFilter`, `filterMenuOpen`, …) und die VMs **nicht** verschieben — sie bleiben im Host, die Helfer schließen über sie. Die Sonderbehandlung „Collections-Detail offen → kein Home-Header" wird zu: in `collectionsHeader` bei offenem Detail `null`-Header? Nein — Header ist non-null. Stattdessen: wenn `openCollectionId != null`, liefert die `content`-Lambda das `CollectionDetailScreen` (das seine eigene Bar bringt) und der Host setzt für diesen Fall die `selected.header` auf einen **leeren** Header (`emptyHomeHeader()`), sodass keine zweite Bar doppelt. Dokumentiere das an der Stelle.

- [ ] **Step 3: `onSelect`-Reset-Logik erhalten** — die alte `EinkBottomBar.onSelect` (Zeilen 308-322) setzt beim Tab-Wechsel `query`/`filter`/Dialoge zurück. Diese Logik gehört in den **Host** (`onSelect`-Callback der Surface), nicht in den Pack:

```kotlin
    val onSelectTab: (Int) -> Unit = { idx ->
        selected = idx
        query = ""; submitted = ""; typeFilter.value = emptySet(); downloadedOnly = false
        filterMenuOpen = false; pluginFilterMenuOpen = false
        showCreateGroup = false; showCreateCollection = false; openCollectionId = null
        showRepoMgmt = false; pluginsVm.setQuery("")
    }
```

(im `AppShellState.onSelect` auf `onSelectTab` mappen.)

- [ ] **Step 4: Kompiliert + bestehende Tests grün**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle bestehenden Tests PASS (Netz für den verhaltens-erhaltenden Refactor)

- [ ] **Step 5: E2E — Default-Shell pixelgleich**

Run: Emulator `eink_test` starten, App installieren (`./gradlew :app:installDebug`), durch alle 5 Tabs klicken, Suche + Library-Filter + Collections-Detail öffnen.
Expected: visuell **identisch** zu vorher — Bottom-Bar unten, Header oben, alle Tabs/Such/Filter funktionieren, Reader öffnet normal. Screenshot Library + Collections-Detail.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/shell/DefaultShell.kt app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt
git commit -m "refactor(app): HomeScreen → HomeShellHost + DefaultShell (verhaltens-erhaltend)"
```

---

## Phase B — Auswahl-Naht verdrahtet (kein Verhaltenswechsel)

> Phase A hat die Auflösung schon im Host (`ShellPackRegistry.forFormFactor(formFactorFor(screenWidthDp))`). Phase B prüft nur, dass auf realen Breiten weiter `DefaultShell` greift, und dokumentiert die Orthogonalität.

### Task B1: Selektion auf Emulator-Breiten verifizieren

- [ ] **Step 1: Default-Pfad bestätigen** — auf `eink_test` (1264dp breit) muss `DefaultShell` greifen.

Run: temporär `Log.d("shell", formFactorFor(...).name)` im Host; App starten; Logcat prüfen.
Expected: `EXPANDED` → DefaultShell. Log danach entfernen.

- [ ] **Step 2: Commit** (nur falls Doku/Kommentar geändert)

```bash
git commit -am "docs(app): form-faktor-orthogonalität an HomeShellHost dokumentiert" || true
```

---

## Phase C — Phone-Shell als Swap-Beweis

### Task C1: `PhoneShell` mit Drawer-Nav

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/shell/PhoneShell.kt`

Andere Anordnung aus **derselben** `AppShellState`: Top-Bar mit Burger + aktivem Header, Nav als `ModalNavigationDrawer` (Destinations als Liste), Content darunter. Beweist: Skelett austauschbar, Nav an anderem Ort, **kein** Core-Code geändert.

- [ ] **Step 1: `PhoneShell.Render` implementieren**

```kotlin
package com.komgareader.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.slots.LocalResolvedSlots
import kotlinx.coroutines.launch

/**
 * Swap-Beweis-Built-in für den **compact** Form-Faktor: dasselbe Können, fundamental andere Anordnung —
 * Nav als Drawer (Burger oben links) statt Bottom-Bar. Konsumiert **dieselbe** [AppShellState] wie
 * [DefaultShell], rendert dieselben host-gebauten `header`/`content`-Stücke. Kein Core-Code wird berührt.
 * E-Ink-Invarianten bleiben host-erzwungen (Drawer-Animationen über bestehende Animation-Gates prüfen).
 */
@OptIn(ExperimentalMaterial3Api::class)
object PhoneShell : ShellPack {
    @Composable
    override fun Render(state: AppShellState) {
        val slots = LocalResolvedSlots.current
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet {
                    state.destinations.forEach { d ->
                        NavigationDrawerItem(
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(d.label) },
                            selected = d.id == state.selectedId,
                            onClick = {
                                state.onSelect(d.id)
                                scope.launch { drawer.close() }
                            },
                        )
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text(state.selected.label) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(AppIcons.Menu, contentDescription = null)
                                }
                            },
                        )
                        slots.homeHeader(state.selected.header)
                    }
                },
            ) { inner ->
                Box(Modifier.fillMaxSize().padding(inner)) { state.selected.content() }
            }
        }
    }
}
```

> Falls `AppIcons.Menu` nicht existiert: vorhandenes Burger-/Listen-Icon der Registry nutzen (per `grep "val " app/.../icons/AppIcons.kt` prüfen) oder eins ergänzen (Icon-System: `komga-eink-ui-polish`-Skill).

- [ ] **Step 2: Kompiliert**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: E2E — Phone-Shell auf compact-Profil**

Run: AVD mit compact-Breite anlegen/nutzen (`emulator -avd <phone> -dpi-device …`, ~411dp) ODER `adb shell wm size 411x914 && adb shell wm density 420`, App starten.
Expected: Burger oben links → Drawer mit 5 Destinations; Auswahl wechselt Tab; derselbe Header + Content; Reader öffnet normal. Auf `eink_test` (expanded) weiter Bottom-Bar. `adb shell wm size reset` danach.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/shell/PhoneShell.kt
git commit -m "feat(app): PhoneShell (drawer-nav) als form-faktor-swap-beweis"
```

---

## Phase D — Doku nachziehen (docs-match-code) + Skill

### Task D1: `architecture-seams.md` + `big-picture` Ist-Stand

**Files:**
- Modify: `.claude/rules/architecture-seams.md` (UI-Slot-Naht-Abschnitt)

- [ ] **Step 1:** Im UI-Slot-Naht-Abschnitt eine Zeile „Shell-Pack-Schicht (Ist, 2026-06-12): `AppShellState`/`ShellPack`/`DefaultShell`/`PhoneShell` gebaut, Auswahl `formFactorFor` über `ShellPackRegistry`, NavHost/Reader unberührt" ergänzen. `big-picture-and-goals.md`: die Shell-Pack-Tabellenzeile von „Soll" auf „gebaut" ziehen und den docs-match-code-Block aktualisieren.

- [ ] **Step 2: Commit**

```bash
git add .claude/rules/architecture-seams.md .claude/rules/big-picture-and-goals.md
git commit -m "docs(rules): shell-pack-naht auf Ist-Stand gezogen (docs-match-code)"
```

### Task D2: Skill `plugin-domain` → `komga-plugins` umbenennen + Shell-Pattern aufnehmen

**Files:**
- Rename: `.claude/skills/plugin-domain/` → `.claude/skills/komga-plugins/`
- Modify: `.claude/skills/komga-plugins/SKILL.md` (frontmatter `name:` + Inhalt)

- [ ] **Step 1: Verzeichnis umbenennen**

```bash
git mv .claude/skills/plugin-domain .claude/skills/komga-plugins
```

- [ ] **Step 2:** In `SKILL.md` das frontmatter `name:` auf `komga-plugins` setzen. In Säule 3 („Rezept: Subsystem plugbar machen") einen Verweis ergänzen, dass die **Shell-Pack-Schicht** (`AppShellState`/`DefaultShell`/`PhoneShell`, Form-Faktor-Naht) die jüngste Referenz-Implementierung des Rezepts ist — mit dem **verfeinerten Daten-vs-Composable-Schnitt** (reine-Präsentation-über-Daten-Stücke wie Nav gehen als Daten, weil die Widget-Wahl die Variabilität ist; logik-gebundene als host-gebaute Composables) und dem **1→3-Pfad** (benannte Stücke → deklarativer Deskriptor). Verweise auf den Spec + `big-picture` (ui-modularity).

- [ ] **Step 3: Verweise prüfen** — andere Dateien, die den alten Namen nennen:

Run: `grep -rn "plugin-domain" .claude/ docs/ 2>/dev/null`
Expected: gefundene Referenzen (z. B. `MEMORY.md`-Pointer, andere Rules) auf `komga-plugins` aktualisieren.

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/ .claude/rules/ docs/
git commit -m "docs(skill): plugin-domain → komga-plugins + shell-pack-pattern aufgenommen"
```

---

## Self-Review (Plan gegen Spec)

- **§2 3-Schichten / §5 Surface** → Task A1 (`AppShellState`/`ShellDestination`/`ShellDestinationId`). ✓
- **§3 Ansatz 1 / 1→3-Bedingung (benannte Stücke)** → A1 (Surface ist Satz benannter Stücke, kein Blob). ✓
- **§4 Core-Nav ⟂ Shell-Schnitt, Reader unberührt** → A3 (NavHost/Reader nicht angefasst; nur `HomeScreen`-Inneres). ✓
- **§5 Daten-vs-Composable** → A1 (Nav = `icon`/`label`/`id` Daten; `content`/`header` Composables) + A3/C1 (Packs bauen Nav-Widget selbst). ✓
- **§6 Form-Faktor-Trigger (auto nach Breite)** → A2 (`formFactorFor`) + A3-Wiring. User-Override = Soll, kein Task (korrekt). ✓
- **§7 Phasen A→B→C** → Phase A/B/C. ✓
- **§8 Invarianten host-erzwungen** → DefaultShell/PhoneShell nutzen `LocalResolvedSlots`/Locals, setzen keine Policy. ✓
- **§9 Tests** → A2 (pure Selektion + Fallback-via-Registry) + A3/C1 E2E. ✓
- **Skill-Rename** → D2. ✓
```
