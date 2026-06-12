# Aurora Modern-Mobile-Look — Phase 1 (In-Tree) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Einen distinktiven Modern-Mobile-Look „Aurora" (dark + light) als In-Tree-`UiPack` bauen, der im Smartphone-Display-Modus greift — mit eigenem Farbschema (Slate/Deeper-Grey + Cobalt), schwebender Pill-Bottom-Nav und Card-Serien-Kacheln.

**Architecture:** Aurora ist ein weiteres `UiPack` hinter der bestehenden Theme-Pack-Naht (`packFor(behavior)`), gewählt für die LCD-Geräteklasse (= `DisplayMode.SMARTPHONE`). Die mobile Nav ist ein neuer `ShellNavStyle.FLOATING_NAV`, den die host-eigene `DeclarativeShell` rendert; die Card-Kachel ist ein `tiles`-Slot-Pack, in den Host (`MainActivity`/`HomeShellHost`) eingespeist, nur wenn Smartphone aktiv. Reader/Engines bleiben Core, erben nur das Theme. Bestehende Geräteklassen-Looks (Mono/Kaleido) und der `LcdPack`-Fallback bleiben unberührt.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit (pure unit tests in `:ui-api`/`:app`), Android-Emulator (Boox-Maße + Smartphone) zur E2E-Sichtprüfung.

**Referenz-Spec:** `docs/superpowers/specs/2026-06-12-modern-mobile-ui-pack-aurora-design.md` (§2 Tokens verbindlich). Phase 2 (deklaratives `ui_pack.json`-Schema) ist ein **separater** Plan, der nach Phase 1 aus dieser Referenz abgeleitet wird.

---

## File Structure

**Neu:**
- `ui-api/src/main/kotlin/com/komgareader/ui/theme/AuroraPack.kt` — das Aurora-`UiPack` (ColorSchemes hell/dunkel, Shapes, Typo, DesignTokens). Verantwortung: der volle Look einer Geräteklasse als Daten.
- `ui-api/src/test/kotlin/com/komgareader/ui/theme/AuroraPackTest.kt` — Token-/Schema-Asserts.
- `app/src/main/kotlin/com/komgareader/app/ui/shell/FloatingNavBar.kt` — die schwebende Pill-Bottom-Nav (token-getrieben: Schatten auf LCD, sonst Border).
- `app/src/main/kotlin/com/komgareader/app/ui/shell/AuroraShell.kt` — pure `auroraShellOverride(DisplayMode)` (FLOATING_NAV im Smartphone-Modus).
- `app/src/test/kotlin/com/komgareader/app/ui/shell/AuroraShellTest.kt` — Override-Logik.
- `app/src/main/kotlin/com/komgareader/app/ui/components/AuroraSeriesTile.kt` — Card-Kachel (`TilesSlot`).
- `app/src/debug/kotlin/com/komgareader/app/ui/theme/AuroraPackPreview.kt` — Swap-Beweis Theme.
- `app/src/debug/kotlin/com/komgareader/app/ui/shell/FloatingNavShellPreview.kt` — Swap-Beweis Shell.

**Geändert:**
- `ui-api/.../ui/theme/UiPack.kt` — `packFor` LCD-Zweig → `AuroraPack`; `UiPackRegistry` (in `UiPackRegistry.kt`) listet Aurora.
- `ui-api/.../ui/shell/ShellDescriptor.kt` — `ShellNavStyle` += `FLOATING_NAV`.
- `app/.../ui/shell/DeclarativeShell.kt` — `FLOATING_NAV`-Zweig → `FloatingNavShell`.
- `ui-api/src/test/.../theme/UiPackTest.kt` — Erwartung LCD → Aurora.
- `app/.../MainActivity.kt` — `slotPack` (Aurora-Tiles im Smartphone-Modus) an `KomgaReaderTheme`.
- `app/.../ui/home/HomeScreen.kt` (`HomeShellHost`) — Aurora-Shell-Override.
- `.claude/rules/architecture-seams.md` + `big-picture-and-goals.md` — docs-match-code (Aurora + FLOATING_NAV).

---

## Task 1: AuroraPack (Theme — UiPack)

**Files:**
- Create: `ui-api/src/main/kotlin/com/komgareader/ui/theme/AuroraPack.kt`
- Test: `ui-api/src/test/kotlin/com/komgareader/ui/theme/AuroraPackTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraPackTest {
    @Test fun `id ist aurora`() = assertEquals("aurora", AuroraPack.id)

    @Test fun `dark colorScheme traegt Slate-Grey + Cobalt`() {
        val cs = AuroraPack.colorScheme(dark = true)
        assertEquals(Color(0xFF15171C), cs.background)
        assertEquals(Color(0xFF1C1F26), cs.surface)
        assertEquals(Color(0xFF3D5AFE), cs.primary)
    }

    @Test fun `light colorScheme traegt Deeper-Grey + dunkles Dock`() {
        val cs = AuroraPack.colorScheme(dark = false)
        assertEquals(Color(0xFFCDD1D9), cs.background)
        assertEquals(Color(0xFF959CAA), cs.surfaceVariant) // Nav-Dock (dunkler als bg)
        assertEquals(Color(0xFF3D5AFE), cs.primary)
    }

    @Test fun `designTokens = LCD-Klasse, Cobalt-Akzent, 16dp, Schatten`() {
        val t = AuroraPack.designTokens(dark = true)
        assertEquals(Color(0xFF3D5AFE), t.accent)
        assertEquals(16.dp, t.cornerRadius)
        assertTrue(t.usesShadows)
    }

    @Test fun `shapes sind soft 16dp medium`() =
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(16.dp), AuroraPack.shapes.medium)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ui-api:testDebugUnitTest --tests "com.komgareader.ui.theme.AuroraPackTest"`
Expected: FAIL — `AuroraPack` ist unbekannt (compile error / unresolved reference).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.komgareader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

// ── Aurora Farb-Rollen (Spec §2) ───────────────────────────────────────────────
private val Cobalt = Color(0xFF3D5AFE)

private val AuroraDark = darkColorScheme(
    primary = Cobalt, onPrimary = Color.White,
    primaryContainer = Color(0xFF262C45), onPrimaryContainer = Color(0xFFAEB7F5),
    background = Color(0xFF15171C), onBackground = Color(0xFFE9EAEE),
    surface = Color(0xFF1C1F26), onSurface = Color(0xFFE9EAEE),
    surfaceVariant = Color(0xFF1C1F26), onSurfaceVariant = Color(0xFF9296A0),
    outline = Color(0xFF2E313A), outlineVariant = Color(0xFF3A3E48),
)

private val AuroraLight = lightColorScheme(
    primary = Cobalt, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE0FF), onPrimaryContainer = Color(0xFF11205E),
    background = Color(0xFFCDD1D9), onBackground = Color(0xFF1A1D24),
    surface = Color(0xFFC3C8D1), onSurface = Color(0xFF1A1D24),
    surfaceVariant = Color(0xFF959CAA), onSurfaceVariant = Color(0xFF3F4450), // Nav-Dock + Icon
    outline = Color(0xFFB1B7C2), outlineVariant = Color(0xFFBFC4CE),
)

// ── Soft Shapes (groß, modern) ──────────────────────────────────────────────────
private val AuroraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

// ── Typografie: System-Font, modern getunt (bold + tight Headlines) ─────────────
private val AuroraBase = Typography()
private val AuroraTypography = AuroraBase.copy(
    headlineSmall = AuroraBase.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = AuroraBase.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = AuroraBase.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = AuroraBase.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/**
 * Aurora: distinktiver Modern-Mobile-Look (LCD-Geräteklasse). Slate/Deeper-Grey + Cobalt, weiche große
 * Radien, Tiefe über Schatten, Motion erlaubt. Vierter [UiPack] hinter der Theme-Naht; gewählt im
 * Smartphone-Modus (`packFor`). Spec: `2026-06-12-modern-mobile-ui-pack-aurora-design.md` §2.
 */
val AuroraPack: UiPack = object : UiPack {
    override val id = "aurora"
    override fun colorScheme(dark: Boolean) = if (dark) AuroraDark else AuroraLight
    override fun designTokens(dark: Boolean) = DesignTokens(
        accent = Cobalt,
        onAccent = Color.White,
        usesShadows = true,
        cardElevation = 3.dp,
        cornerRadius = 16.dp,
    )
    override val shapes = AuroraShapes
    override val typography = AuroraTypography
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ui-api:testDebugUnitTest --tests "com.komgareader.ui.theme.AuroraPackTest"`
Expected: PASS (5 Tests grün).

- [ ] **Step 5: Commit**

```bash
git add ui-api/src/main/kotlin/com/komgareader/ui/theme/AuroraPack.kt ui-api/src/test/kotlin/com/komgareader/ui/theme/AuroraPackTest.kt
git commit -m "feat(ui-pack): AuroraPack — Modern-Mobile-Theme (dark+light, Cobalt, soft shapes)"
```

---

## Task 2: packFor → Aurora für die LCD-Geräteklasse

**Files:**
- Modify: `ui-api/src/main/kotlin/com/komgareader/ui/theme/UiPack.kt` (`packFor`, Zeile ~225)
- Modify: `ui-api/src/main/kotlin/com/komgareader/ui/theme/UiPackRegistry.kt:22` (`builtIns`)
- Test: `ui-api/src/test/kotlin/com/komgareader/ui/theme/UiPackTest.kt:26,47`

- [ ] **Step 1: Update the failing test (Erwartung LCD → Aurora)**

In `UiPackTest.kt` die LCD-Zeile ändern und Aurora-im-Registry prüfen:

```kotlin
// war: assertSame(LcdPack, packFor(lcd))
assertSame(AuroraPack, packFor(lcd))
```
Und im Registry-Test ergänzen (LcdPack bleibt per id auflösbar als Fallback):

```kotlin
assertSame(AuroraPack, UiPackRegistry.byId("aurora"))
assertSame(LcdPack, UiPackRegistry.byId("lcd"))
```

> Die bestehende Zeile `assertEquals(designTokensFor(lcd, dark = true), LcdPack.designTokens(true))` bleibt unverändert — sie testet `LcdPack` direkt, nicht die Auswahl.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ui-api:testDebugUnitTest --tests "com.komgareader.ui.theme.UiPackTest"`
Expected: FAIL — `packFor(lcd)` liefert noch `LcdPack`, nicht `AuroraPack`.

- [ ] **Step 3: Implementation**

In `UiPack.kt`, `packFor` LCD-Zweig umbiegen (Mono/Kaleido unverändert):

```kotlin
fun packFor(behavior: DisplayBehavior): UiPack = when {
    !behavior.allowsAccentColor -> MonoEinkPack
    !behavior.allowsMotion -> KaleidoPack
    else -> AuroraPack   // war: LcdPack — LcdPack bleibt als generischer Fallback im Registry
}
```

In `UiPackRegistry.kt:22` Aurora in die Liste aufnehmen (LcdPack bleibt drin):

```kotlin
private val builtIns: List<UiPack> = listOf(MonoEinkPack, KaleidoPack, AuroraPack, LcdPack)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ui-api:testDebugUnitTest --tests "com.komgareader.ui.theme.UiPackTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ui-api/src/main/kotlin/com/komgareader/ui/theme/UiPack.kt ui-api/src/main/kotlin/com/komgareader/ui/theme/UiPackRegistry.kt ui-api/src/test/kotlin/com/komgareader/ui/theme/UiPackTest.kt
git commit -m "feat(ui-pack): packFor waehlt Aurora fuer LCD-Klasse (Lcd bleibt Fallback)"
```

---

## Task 3: ShellNavStyle FLOATING_NAV + pure Aurora-Shell-Override

**Files:**
- Modify: `ui-api/src/main/kotlin/com/komgareader/ui/shell/ShellDescriptor.kt:7`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/shell/DeclarativeShell.kt:52-55` (temporärer Zweig, Build grün halten)
- Create: `app/src/main/kotlin/com/komgareader/app/ui/shell/AuroraShell.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/shell/AuroraShellTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.app.ui.shell

import com.komgareader.domain.model.DisplayMode
import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellNavStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuroraShellTest {
    @Test fun `smartphone-modus erzwingt floating nav`() =
        assertEquals(ShellDescriptor(ShellNavStyle.FLOATING_NAV), auroraShellOverride(DisplayMode.SMARTPHONE))

    @Test fun `eink-modus kein override`() =
        assertNull(auroraShellOverride(DisplayMode.EINK))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.shell.AuroraShellTest"`
Expected: FAIL — `auroraShellOverride` und `ShellNavStyle.FLOATING_NAV` unbekannt.

- [ ] **Step 3: Implementation**

In `ShellDescriptor.kt:7` das Enum additiv erweitern:

```kotlin
enum class ShellNavStyle { BOTTOM_BAR, DRAWER, FLOATING_NAV }
```

In `DeclarativeShell.kt` den `when` exhaustiv halten (temporär auf BottomBarShell, in Task 4 ersetzt):

```kotlin
override fun Render(state: AppShellState) = when (descriptor.navStyle) {
    ShellNavStyle.BOTTOM_BAR -> BottomBarShell(state)
    ShellNavStyle.DRAWER -> DrawerShell(state)
    ShellNavStyle.FLOATING_NAV -> BottomBarShell(state) // TODO Task 4: FloatingNavShell
}
```

Neue Datei `AuroraShell.kt`:

```kotlin
package com.komgareader.app.ui.shell

import com.komgareader.domain.model.DisplayMode
import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellNavStyle

/**
 * Aurora ist der Smartphone-Look → seine Nav ist die schwebende Pill-Bottom-Nav (FLOATING_NAV),
 * unabhängig vom Form-Faktor. Pure Funktion (testbar). Der Host kombiniert: ein L2-UI-Pack-Override
 * schlägt diesen Aurora-Default (siehe HomeShellHost). E-Ink-Modus → kein Override (Form-Faktor-Default).
 */
fun auroraShellOverride(mode: DisplayMode): ShellDescriptor? =
    if (mode == DisplayMode.SMARTPHONE) ShellDescriptor(ShellNavStyle.FLOATING_NAV) else null
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.shell.AuroraShellTest"`
Expected: PASS. Außerdem `./gradlew :app:compileDebugKotlin` grün (when exhaustiv).

- [ ] **Step 5: Commit**

```bash
git add ui-api/src/main/kotlin/com/komgareader/ui/shell/ShellDescriptor.kt app/src/main/kotlin/com/komgareader/app/ui/shell/AuroraShell.kt app/src/main/kotlin/com/komgareader/app/ui/shell/DeclarativeShell.kt app/src/test/kotlin/com/komgareader/app/ui/shell/AuroraShellTest.kt
git commit -m "feat(shell): ShellNavStyle.FLOATING_NAV + pure auroraShellOverride"
```

---

## Task 4: FloatingNavBar + FloatingNavShell

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/shell/FloatingNavBar.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/shell/DeclarativeShell.kt` (FLOATING_NAV-Zweig + `FloatingNavShell`)

> Reine UI (Compose) — verifiziert über Preview (Task 7) + Emulator (Task 8), kein Unit-Test. Mechanik 1:1 aus `BottomBarShell` (Inset-Logik), nur die Bar getauscht (`shared-structure-before-variants`).

- [ ] **Step 1: FloatingNavBar schreiben**

`FloatingNavBar.kt`:

```kotlin
package com.komgareader.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Schwebende Pill-Bottom-Nav für den Aurora-/Mobile-Look: rundum 20dp-Pille, Tiefe über Schatten
 * (LCD: `tokens.usesShadows`) statt Border, aktives Item als gefüllte Akzent-Pille hinter dem Icon.
 * Token-getrieben (Akzent/Elevation) — derselbe `BottomNavItem`-Vertrag wie [com.komgareader.app.ui.components.EinkBottomBar].
 */
@Composable
fun FloatingNavBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalDesignTokens.current
    val shape = RoundedCornerShape(24.dp)
    val dock = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        val base = Modifier.fillMaxWidth()
        val withDepth = if (tokens.usesShadows) {
            base.shadow(tokens.cardElevation + 5.dp, shape, clip = false).clip(shape).background(dock)
        } else {
            base.clip(shape).background(dock).border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
        }
        Row(
            withDepth.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                FloatingNavCell(item, index == selectedIndex, { onSelect(index) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FloatingNavCell(item: BottomNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = LocalDesignTokens.current.accent
    val onAccent = LocalDesignTokens.current.onAccent
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 28.dp)
                .clip(RoundedCornerShape(99.dp))
                .then(if (selected) Modifier.background(accent) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp),
                tint = if (selected) onAccent else inactive)
        }
        Spacer(Modifier.height(3.dp))
        Text(item.label, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else inactive)
    }
}
```

- [ ] **Step 2: FloatingNavShell in DeclarativeShell ergänzen + Zweig schalten**

In `DeclarativeShell.kt`: den Import ergänzen (`import androidx.compose.material3.Scaffold` etc. sind vorhanden) und den FLOATING_NAV-Zweig + die private Funktion. `FloatingNavShell` ist `BottomBarShell` verbatim, nur `EinkBottomBar` → `FloatingNavBar`:

```kotlin
ShellNavStyle.FLOATING_NAV -> FloatingNavShell(state)
```
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingNavShell(state: AppShellState) {
    val slots = LocalResolvedSlots.current
    Scaffold(
        topBar = { state.selected.header?.let { slots.homeHeader(it) } },
    ) { inner ->
        var barHeightPx by remember { mutableIntStateOf(0) }
        val barInset = with(LocalDensity.current) { barHeightPx.toDp() }
        Box(Modifier.fillMaxSize().padding(inner)) {
            CompositionLocalProvider(LocalContentBottomInset provides barInset) {
                Box(Modifier.fillMaxSize()) { state.selected.content() }
            }
            FloatingNavBar(
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
```

- [ ] **Step 3: Build verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/shell/FloatingNavBar.kt app/src/main/kotlin/com/komgareader/app/ui/shell/DeclarativeShell.kt
git commit -m "feat(shell): FloatingNavBar + FloatingNavShell fuer den FLOATING_NAV-Stil"
```

---

## Task 5: AuroraSeriesTile (Card-Kachel, tiles-Slot)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/AuroraSeriesTile.kt`

> UI-Slot-Pack; Render-Pfad identisch zu `DefaultSeriesTile`, nur Rahmen = Card (Radius aus `tokens.cornerRadius`, Schatten statt Border auf LCD). Cover-Laden + E-Ink-Filter (`FilteredAsyncImage`) bleiben host-erzwungen. Verifikation über Emulator (Task 8).

- [ ] **Step 1: AuroraSeriesTile schreiben**

```kotlin
package com.komgareader.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.TileState
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Aurora-Card-Kachel (`tiles`-Slot): wie [DefaultSeriesTile], aber als erhabene Card — Eckradius aus
 * [LocalDesignTokens] (16dp), Tiefe über Schatten (`usesShadows`). Auf E-Ink würde dieser Slot nie
 * gewählt (nur Smartphone-Modus speist ihn ein), daher reiner LCD-Card-Look.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroraSeriesTile(state: TileState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val tokens = LocalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadius)
    val request = remember(state.series.sourceId, state.series.remoteId) {
        ImageRequest.Builder(ctx)
            .data(SourceCover(state.series.sourceId, state.series.remoteId, isSeries = true))
            .crossfade(false).build()
    }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .shadow(if (tokens.usesShadows) tokens.cardElevation + 2.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = state.onClick, onLongClick = state.onLongClick),
    ) {
        FilteredAsyncImage(
            model = request,
            contentDescription = state.series.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.isLocal) AppIcons.Local else AppIcons.Cloud,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        TileTitleBand(state.series.title, Modifier.align(Alignment.BottomStart))
    }
}
```
(`import androidx.compose.ui.unit.dp` ergänzen.)

- [ ] **Step 2: Build verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/AuroraSeriesTile.kt
git commit -m "feat(tiles): AuroraSeriesTile — Card-Kachel fuer den Aurora-Look"
```

---

## Task 6: Aurora in den Host verdrahten (slotPack + Shell-Override)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt` (~Z. 137-147)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt` (`HomeShellHost`, ~Z. 356-366)

> Wiring; verifiziert durch Emulator (Task 8). Keine neue Unit-Logik (die pure Auswahl ist Task 2/3).

- [ ] **Step 1: MainActivity — Aurora-Tiles-Slot im Smartphone-Modus einspeisen**

In `MainActivity.kt` direkt vor dem `KomgaReaderTheme(...)`-Aufruf (displayMode ist Z.137 schon berechnet):

```kotlin
// Aurora-Card-Kacheln nur im Smartphone-Modus (LCD); E-Ink behält die Default-Kachel.
val slotPack = remember(displayMode) {
    if (displayMode == DisplayMode.SMARTPHONE) {
        UiSlotPack(tiles = { s, m -> AuroraSeriesTile(s, m) })
    } else {
        UiSlotPack()
    }
}
```
Und den Aufruf erweitern:
```kotlin
KomgaReaderTheme(themeMode = themeMode, slotPack = slotPack, tokenOverride = tokenOverride) {
```
Imports ergänzen: `com.komgareader.app.ui.components.AuroraSeriesTile`, `com.komgareader.ui.slots.UiSlotPack`.

- [ ] **Step 2: HomeShellHost — Aurora-Shell-Override mit L2-Vorrang**

In `HomeScreen.kt` den `displayMode` lesen (SettingsViewModel hat `displayMode`, vgl. MainActivity) und den Override kombinieren:

```kotlin
val displayModeStr by settingsVm.displayMode.collectAsState()
val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
// L2-UI-Pack-Override schlägt den Aurora-Default; ohne beides → Form-Faktor-Default.
val effectiveOverride = shellOverride ?: auroraShellOverride(displayMode)
val pack = ShellPackRegistry.forFormFactor(
    resolveFormFactor(layoutMode, configuration.screenWidthDp),
    effectiveOverride,
)
```
Imports ergänzen: `com.komgareader.app.ui.shell.auroraShellOverride`, `com.komgareader.domain.model.DisplayMode`.

- [ ] **Step 3: Build verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/MainActivity.kt app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt
git commit -m "feat(ui-pack): Aurora im Host verdrahtet (Card-Tiles + FLOATING_NAV im Smartphone-Modus)"
```

---

## Task 7: Debug-Previews (Swap-Beweise)

**Files:**
- Create: `app/src/debug/kotlin/com/komgareader/app/ui/theme/AuroraPackPreview.kt`
- Create: `app/src/debug/kotlin/com/komgareader/app/ui/shell/FloatingNavShellPreview.kt`

> `@Preview` (nur Debug, keine Nutzer-Einstellung) — beweist den Look in Android-Studio-Preview ohne Emulator. Muster: bestehende `*SlotPreview.kt`.

- [ ] **Step 1: AuroraPackPreview (dark + light)**

```kotlin
package com.komgareader.app.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Aurora dark") @Preview(name = "Aurora light")
@Composable
private fun AuroraPackPreview() {
    // Pack direkt anwenden (ohne Geräteklassen-Auflösung), beide Modi via systemDark-Preview.
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(colorScheme = AuroraPack.colorScheme(dark), shapes = AuroraPack.shapes, typography = AuroraPack.typography) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Text("Bibliothek", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
```

- [ ] **Step 2: FloatingNavShellPreview**

```kotlin
package com.komgareader.app.ui.shell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.AuroraPack

@Preview(name = "Aurora FloatingNavBar")
@Composable
private fun FloatingNavBarPreview() {
    MaterialTheme(colorScheme = AuroraPack.colorScheme(dark = true)) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FloatingNavBar(
                items = listOf(
                    BottomNavItem(AppIcons.Library, "Bibliothek"),
                    BottomNavItem(AppIcons.Collections, "Sammlungen"),
                    BottomNavItem(AppIcons.Settings, "Mehr"),
                ),
                selectedIndex = 0,
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```
> Falls ein Icon-Name (`AppIcons.Collections`/`Library`) nicht existiert: per `grep "val " ui-api/src/main/kotlin/com/komgareader/ui/icons/AppIcons*.kt` einen vorhandenen IconKey wählen. Preview darf nicht den Build brechen.

- [ ] **Step 3: Build verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/debug/kotlin/com/komgareader/app/ui/theme/AuroraPackPreview.kt app/src/debug/kotlin/com/komgareader/app/ui/shell/FloatingNavShellPreview.kt
git commit -m "test(ui-pack): Aurora Theme- + FloatingNav-Previews (Swap-Beweis)"
```

---

## Task 8: Volle Test-Suite + Emulator-E2E

**Files:** keine (Verifikation).

- [ ] **Step 1: Unit-Tests grün**

Run: `./gradlew :ui-api:testDebugUnitTest :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, keine Fehler.

- [ ] **Step 2: Debug-APK bauen**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Emulator — Smartphone-Modus, Look prüfen**

Auf einem Smartphone-AVD (z. B. Pixel, LCD) installieren, App öffnen, Einstellungen → Anzeige-Modus → **Smartphone**. Erwartet sichtbar:
- Bibliothek: **Card-Kacheln** (runde Ecken 16dp, Schatten), Cover scharf.
- Unten: **schwebende Pill-Bottom-Nav**, aktives Item als **Cobalt-Pille**.
- Dark/Light umschalten (Theme-Modus): Dark = Slate `#15171C`, Light = Deeper-Grey `#CDD1D9` mit **dunklerem Nav-Dock**.

Screenshot dark + light ablegen (Beleg „fertig").

- [ ] **Step 4: Regression — E-Ink unverändert**

Anzeige-Modus → **E-Ink**: Bibliothek zeigt wieder Default-Kachel (4dp-Border, kein Schatten), Bottom-Bar = `EinkBottomBar`, monochrom. Beweist: Aurora ist sauber auf den Smartphone-Modus begrenzt.

- [ ] **Step 5: Commit (nur falls Verifikation Fixes nötig machte)**

```bash
git add -A && git commit -m "fix(ui-pack): Aurora Emulator-Feinschliff"
```

---

## Task 9: docs-match-code (Pflicht, gleicher Branch)

**Files:**
- Modify: `.claude/rules/architecture-seams.md` (Theme-Pack-Naht: vierter Built-in `AuroraPack`, LCD-Klasse → Aurora; Shell: `ShellNavStyle.FLOATING_NAV` + `FloatingNavShell`)
- Modify: `.claude/rules/big-picture-and-goals.md` (Theme-Pack-Zeile: Aurora als LCD-Look; ui-modularity „gebaut"-Liste)

- [ ] **Step 1: Regeln auf Ist-Stand ziehen** — je einen knappen Ist-Absatz ergänzen (Datum 2026-06-12): `AuroraPack` ist der vierte Theme-Built-in, `packFor(LCD)→Aurora` (Lcd Fallback), neue `FLOATING_NAV`-Shell, Card-Tiles via `tiles`-Slot im Smartphone-Modus; Reader unberührt. **Phase 2 (deklaratives `ui_pack.json`-Theme) bleibt Soll.** Keinen Typ als real behaupten, der nicht existiert.

- [ ] **Step 2: Commit**

```bash
git add .claude/rules/architecture-seams.md .claude/rules/big-picture-and-goals.md
git commit -m "docs(ui-pack): docs-match-code — Aurora-Theme + FLOATING_NAV-Shell (Ist)"
```

---

## Self-Review (vom Plan-Autor ausgeführt)

**Spec-Abdeckung:** §2 Tokens → Task 1. §3.1 AuroraPack → Task 1. §3.2 Auswahl (packFor/LcdPack-Fallback) → Task 2. §3.3 FLOATING_NAV-Shell → Task 3+4. §3.4 Card-Tiles → Task 5+6. §3.5 Reader nur Theme → kein Task (bewusst, keine Reader-Änderung). §6 Tests → Task 1-3 (pure) + 7 (Preview) + 8 (E2E). docs-match-code → Task 9. **Phase 2 (§4) bewusst NICHT in diesem Plan** (separater Plan nach Phase 1).

**Platzhalter:** keine — jeder Code-Schritt trägt vollständigen Code.

**Typ-Konsistenz:** `AuroraPack`, `auroraShellOverride`, `ShellNavStyle.FLOATING_NAV`, `FloatingNavBar`, `FloatingNavShell`, `AuroraSeriesTile`, `UiSlotPack(tiles=…)`, `TileState` — durchgängig identisch verwendet. `BottomNavItem`-Vertrag aus `EinkBottomBar` wiederverwendet.

**Offene Verifikationspunkte (im Bau zu prüfen, nicht Plan-Lücke):** exakte IconKey-Namen in der Preview (Task 7 Step 2 Hinweis); `SettingsViewModel.displayMode` existiert (von MainActivity belegt); Light-Mode Nav-Dock-Kontrast am Emulator (Task 8).
```
