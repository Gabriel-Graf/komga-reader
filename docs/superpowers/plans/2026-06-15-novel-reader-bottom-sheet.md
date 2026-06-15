# Novel Reader Bottom-Sheet (Typography + TOC) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the novel reader's centered Typography + TOC modals with one full-width bottom sheet (two tabs), opened by an upward bottom-edge swipe or an expandable peek bar in the chrome overlay, closed on scrim tap / down-drag / back.

**Architecture:** The sheet lives behind the `readerChrome` seam. A new optional `ReaderBottomSheet` capability on `ReaderScaffoldState` (`:ui-api`) carries `expanded`/`onExpandedChange`/`peekLabel`/`content`. The host (`DefaultReaderScaffold`, `:app`) owns the swipe detector + peek bar + scrim + expand/collapse and enforces E-Ink invariants (instant on E-Ink, animated on phone); the novel reader supplies only the tabbed content. Other readers pass `bottomSheet = null`.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle multi-module (`:ui-api`, `:app`), Hilt. E-Ink design language (flat, 1.5px border, no animation on E-Ink, mono, i18n DE+EN).

**Branch:** `feat/novel-reader-bottom-sheet` (already created off `main`; the design spec is committed there: `docs/superpowers/specs/2026-06-15-novel-reader-bottom-sheet-design.md`).

**Build commands (from repo root `/home/gabriel/Documents/Projekte/komga-reader`):**
- ui-api module: `./gradlew :ui-api:compileDebugKotlin`
- app compile: `./gradlew :app:assembleDebug`
- unit tests: `./gradlew :app:testDebugUnitTest :ui-api:testDebugUnitTest`

---

## File Structure

- **Modify** `ui-api/src/main/kotlin/com/komgareader/ui/slots/ReaderScaffoldState.kt` — add `ReaderBottomSheet` data class + `bottomSheet: ReaderBottomSheet? = null` field.
- **Create** `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderBottomSheet.kt` — host renderers: `BoxScope.ReaderBottomSheetLayer`, private `ReaderBottomSheetPeek`, `ReaderBottomSheetExpanded`.
- **Modify** `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderScaffold.kt` — add `bottomSheet` param to `ReaderScaffold`; render the layer inside `DefaultReaderScaffold`.
- **Modify** `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTocPanel.kt` — replace the `NovelTocPanel` modal composable with a reusable `NovelTocList(chapters, onChapterSelected)`; keep `groupChapters`/`TocGroup`/rows.
- **Create** `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelSettingsSheet.kt` — `enum NovelSheetTab`, the 2-tab sheet content, and the two reverse-map helpers moved from `NovelTypoPanel`.
- **Delete** `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypoPanel.kt` — its body (EinkInfoDialog + NovelTypographyControls) is now the sheet's Typography tab.
- **Modify** `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderScreen.kt` — drop typo/toc flags + branches + icons; add `sheetExpanded`/`sheetTab` + `BackHandler` + `bottomSheet`.
- **Modify** `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` + `MapBackedStrings.kt` — add `novelSettings`.
- **Create** `app/src/debug/kotlin/com/komgareader/app/ui/reader/ReaderBottomSheetPreview.kt` — swap/preview proof.
- **Modify** docs: `.claude/rules/architecture-seams.md`, `.claude/rules/big-picture-and-goals.md`.

---

## Task 1: `ReaderBottomSheet` capability in `:ui-api`

**Files:**
- Modify: `ui-api/src/main/kotlin/com/komgareader/ui/slots/ReaderScaffoldState.kt`

- [ ] **Step 1: Add the data class + field**

In `ReaderScaffoldState.kt`, add the data class **above** `data class ReaderScaffoldState`, and add the field as the last constructor parameter of `ReaderScaffoldState` (before `content` is fine too, but put it right after `showTapZoneHints` and before `content`):

```kotlin
/**
 * Optionale Bottom-Sheet-Fähigkeit des Reader-Chrome. Der **Host** besitzt die Öffnen-Mechanik
 * (Aufwärts-Wisch am unteren Rand, ein- und ausklappbarer Peek-Balken bei sichtbarem Chrome,
 * Scrim, Auf/Zu) und **erzwingt die E-Ink-Invarianten** (keine Bewegung auf E-Ink); der Reader
 * liefert nur [content] (beliebig — z. B. ein getabbtes Panel). `null` auf [ReaderScaffoldState]
 * = kein Bottom-Sheet für diesen Reader (Default; Paged/Comic/Webtoon/Epub).
 */
data class ReaderBottomSheet(
    val expanded: Boolean,
    val onExpandedChange: (Boolean) -> Unit,
    /** Beschriftung des eingeklappten Peek-Balkens (nur sichtbar bei sichtbarem Chrome). */
    val peekLabel: String,
    /** Reader-gelieferter Body (die Tabs leben hier — der Host kennt sie nicht). */
    val content: @Composable () -> Unit,
)
```

Then in `ReaderScaffoldState`, add right after the `showTapZoneHints` field:

```kotlin
    /**
     * Optionales Bottom-Sheet (Inhalt reader-geliefert, Mechanik host-besessen). `null` = keins.
     * Host-erzwungene E-Ink-Invarianten: instant auf E-Ink, animiert nur auf Phone.
     */
    val bottomSheet: ReaderBottomSheet? = null,
```

- [ ] **Step 2: Compile the module**

Run: `./gradlew :ui-api:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (additive field, no callers broken).

- [ ] **Step 3: Commit**

```bash
git add ui-api/src/main/kotlin/com/komgareader/ui/slots/ReaderScaffoldState.kt
git commit -m "feat(ui-api): add optional ReaderBottomSheet capability to ReaderScaffoldState"
```

---

## Task 2: Host renderer + scaffold wiring (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderBottomSheet.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderScaffold.kt`

- [ ] **Step 1: Create the host renderer**

Create `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderBottomSheet.kt`:

```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.ui.slots.ReaderBottomSheet
import com.komgareader.ui.theme.EinkTokens

/**
 * Host-Renderer der optionalen [ReaderBottomSheet]-Capability der `readerChrome`-Region. Liegt an
 * **einer** Stelle (wie die Tap-Zonen + Frontlight-Streifen), damit jeder Reader, der ein Sheet
 * will, dieselbe Mechanik bekommt (`shared-structure-before-variants`). Drei Teile:
 *
 * 1. **Bottom-Edge-Swipe** (immer, auch immersiv): voller Bodenrand-Streifen, **nur** vertikaler
 *    Aufwärts-Drag öffnet → linke/mittlere/rechte Blätter-Taps bleiben durch (wie die Frontlight-
 *    Streifen nur horizontalen Drag konsumieren).
 * 2. **Peek-Balken** (nur bei sichtbarem Chrome, eingeklappt): schlanker Greifer + Label, Tap oder
 *    Aufwärts-Wisch klappt auf — die wischlose Alternative.
 * 3. **Expanded-Sheet + Scrim** (aufgeklappt): vollbreiter, unten verankerter, flacher Container.
 *
 * **E-Ink host-erzwungen** ([LocalEinkMode]): instant auf/zu auf E-Ink, `slide`+`fade` nur auf Phone
 * (`animation-gating`). Scrim deckend auf E-Ink via [readerOverlayScrim].
 */
@Composable
fun BoxScope.ReaderBottomSheetLayer(sheet: ReaderBottomSheet, chromeVisible: Boolean) {
    val eink = LocalEinkMode.current

    // 1. Bottom-edge swipe-up detector (collapsed only; works even when chrome is hidden).
    if (!sheet.expanded) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount < 0f) { // upward
                            sheet.onExpandedChange(true)
                            change.consume()
                        }
                    }
                },
        )
    }

    // 2. Peek bar — chrome visible & collapsed.
    if (chromeVisible && !sheet.expanded) {
        ReaderBottomSheetPeek(
            label = sheet.peekLabel,
            onExpand = { sheet.onExpandedChange(true) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // 3. Expanded sheet + scrim.
    if (eink) {
        if (sheet.expanded) {
            Scrim(onTap = { sheet.onExpandedChange(false) })
            ReaderBottomSheetExpanded(
                sheet = sheet,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    } else {
        AnimatedVisibility(visible = sheet.expanded, enter = fadeIn(), exit = fadeOut()) {
            Scrim(onTap = { sheet.onExpandedChange(false) })
        }
        AnimatedVisibility(
            visible = sheet.expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ReaderBottomSheetExpanded(sheet = sheet, modifier = Modifier)
        }
    }
}

// Plain composable (NOT a BoxScope extension): it is also called inside AnimatedVisibility's
// content lambda (AnimatedVisibilityScope), where a BoxScope receiver would not resolve. It uses
// fillMaxSize only — no align — so no BoxScope is needed.
@Composable
private fun Scrim(onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(readerOverlayScrim(Color.Black, 0.45f))
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    )
}

@Composable
private fun ReaderBottomSheetPeek(label: String, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount < 0f) { onExpand(); change.consume() }
                }
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 32.dp, height = 3.dp).background(MaterialTheme.colorScheme.outline))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReaderBottomSheetExpanded(sheet: ReaderBottomSheet, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cap = maxHeight * 0.55f
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline),
        ) {
            // Grabber handle row — drag down to dismiss (the scrollable body does not).
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount > 0f) { sheet.onExpandedChange(false); change.consume() }
                        }
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width = 32.dp, height = 3.dp).background(MaterialTheme.colorScheme.outline))
            }
            // Scrollable content, height-capped (mirrors EinkInfoDialog's Box(heightIn)+verticalScroll).
            Box(Modifier.heightIn(max = cap)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    sheet.content()
                }
            }
        }
    }
}
```

Note: `readerOverlayScrim` lives in `ReaderChrome.kt` (same package `com.komgareader.app.ui.reader`), so it is callable without import. `EinkTokens.hairline` is a `Dp` in `com.komgareader.ui.theme`.

- [ ] **Step 2: Thread `bottomSheet` through `ReaderScaffold`**

In `ReaderScaffold.kt`, add the import:

```kotlin
import com.komgareader.ui.slots.ReaderBottomSheet
```

Add a parameter to `fun ReaderScaffold(...)` — insert after `showTapZoneHints: Boolean = true,` and before `content`:

```kotlin
    bottomSheet: ReaderBottomSheet? = null,
```

Add it to the `ReaderScaffoldState(...)` construction (after `showTapZoneHints = showTapZoneHints,`):

```kotlin
        bottomSheet = bottomSheet,
```

- [ ] **Step 3: Render the layer in `DefaultReaderScaffold`**

In `DefaultReaderScaffold`, inside the root `Box`, add **after** the `state.footer` block and **before** the `ReaderStartHint(...)` line:

```kotlin
        // Optionales Bottom-Sheet (host-besessen, Inhalt reader-geliefert). Liegt über Footer/
        // Overlay; der Scrim + die Bewegung sind host-/E-Ink-erzwungen.
        val sheet = state.bottomSheet
        if (sheet != null) {
            ReaderBottomSheetLayer(sheet = sheet, chromeVisible = state.chromeVisible)
        }
```

- [ ] **Step 4: Compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderBottomSheet.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderScaffold.kt
git commit -m "feat(reader): host-rendered bottom sheet (swipe-up + peek bar) in DefaultReaderScaffold"
```

---

## Task 3: Extract `NovelTocList`, drop the TOC modal

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTocPanel.kt`

The current `NovelTocPanel` wraps the chapter list in an `EinkInfoDialog`. Extract the list body into a reusable, dialog-free `NovelTocList` so the sheet's TOC tab reuses it. Keep `TocGroup`, `groupChapters`, `TocParentRow`, `TocChildRow` exactly as-is.

- [ ] **Step 1: Replace the `NovelTocPanel` composable with `NovelTocList`**

In `NovelTocPanel.kt`, delete the `import com.komgareader.app.ui.components.EinkInfoDialog` line, and replace the whole `@Composable fun NovelTocPanel(...) { ... }` (lines ~63-112) with:

```kotlin
/**
 * Roman-Inhaltsverzeichnis als **rahmenlose** Liste (für das Settings-Bottom-Sheet, TOC-Tab):
 * oberste Ebenen mit **faltbaren** Unterkapiteln (per Default alles zu). Ein Tap auf den Titel
 * springt zum Anker (über [onChapterSelected]); der Chevron links klappt nur auf/zu. Enge Zeilen,
 * **keine** Trennlinien. **Keine Animation** (`animation-gating`). Texte über [LocalStrings].
 */
@Composable
fun NovelTocList(
    chapters: List<Chapter>,
    onChapterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    if (chapters.isEmpty()) {
        Text(
            strings.novelTocEmpty,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val groups = remember(chapters) { groupChapters(chapters) }
    var expanded by remember(chapters) { mutableStateOf(emptySet<Int>()) }

    Column(modifier.fillMaxWidth()) {
        groups.forEachIndexed { index, group ->
            TocParentRow(
                chapter = group.parent,
                hasChildren = group.children.isNotEmpty(),
                expanded = index in expanded,
                onToggle = {
                    expanded = if (index in expanded) expanded - index else expanded + index
                },
                onSelect = { onChapterSelected(group.parent.anchor) },
            )
            if (index in expanded) {
                group.children.forEach { child ->
                    TocChildRow(child) { onChapterSelected(child.anchor) }
                }
            }
        }
    }
}
```

Add the needed import at the top (next to the other layout imports):

```kotlin
import androidx.compose.foundation.layout.Column
```

(The `onDismiss` that the old modal called after select is gone — the **caller** now closes the sheet inside its `onChapterSelected` lambda, see Task 6.)

- [ ] **Step 2: Compile**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL — `NovelTocPanel` is still referenced in `NovelReaderScreen.kt` (fixed in Task 6). That is expected at this point; if you are running tasks in order, instead verify just this file compiles conceptually and move on. To get a clean green here without Task 6, temporarily this will fail; **defer the build check to Task 6**. (Do not add a shim — Task 6 removes the caller.)

- [ ] **Step 3: Keep the `groupChapters` test green (if present)**

Run: `./gradlew :app:testDebugUnitTest --tests "*Toc*"` (and `--tests "*groupChapters*"`).
Expected: PASS if such a test exists (the pure `groupChapters` is unchanged). If no such test exists, skip.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTocPanel.kt
git commit -m "refactor(reader): extract dialog-free NovelTocList from NovelTocPanel"
```

---

## Task 4: i18n key `novelSettings`

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt`

- [ ] **Step 1: Interface declaration**

In `Strings.kt`, next to `val novelToc: String` (~line 365), add:

```kotlin
    val novelSettings: String
```

- [ ] **Step 2: German value**

In `Strings.kt`, in `GermanStrings`, next to `override val novelToc = "Inhaltsverzeichnis"` (~line 771), add:

```kotlin
    override val novelSettings = "Einstellungen"
```

- [ ] **Step 3: English value**

In `Strings.kt`, in `EnglishStrings`, next to `override val novelToc = "Table of contents"` (~line 1168), add:

```kotlin
    override val novelSettings = "Settings"
```

- [ ] **Step 4: MapBackedStrings override**

In `MapBackedStrings.kt`, next to the `novelToc` override (~line 353), add:

```kotlin
    override val novelSettings: String get() = overrides["novelSettings"] ?: fallback.novelSettings
```

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (all `Strings` implementors now satisfy the interface).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt \
        app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt
git commit -m "i18n: add novelSettings (DE Einstellungen / EN Settings)"
```

---

## Task 5: `NovelSettingsSheet` (2-tab content)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelSettingsSheet.kt`

The sheet body: a flat two-segment tab row `[Typografie | TOC]` and the active tab below it. Typography reuses `NovelTypographyControls`; TOC reuses `NovelTocList`. The two private reverse-map helpers (`ReflowConfig.marginPreset()`, `ReflowConfig.hyphenationLang()`) move here from `NovelTypoPanel` (which Task 6 deletes).

- [ ] **Step 1: Create the file**

```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign as UiTextAlign
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.domain.render.Chapter
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.NovelFont
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.TextAlign
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.theme.LocalDesignTokens
import java.io.File

/** Welcher Tab im Settings-Bottom-Sheet aktiv ist. Novel-lokal; der Host kennt die Tabs nicht. */
enum class NovelSheetTab { TYPOGRAPHY, TOC }

/**
 * Inhalt des Novel-Settings-Bottom-Sheets: zwei Tabs [Typografie | TOC]. Stateless — Werte rein,
 * Callbacks raus; den `expanded`-Zustand + den Schließvorgang besitzt der Aufrufer (Screen). Beide
 * Tabs nutzen die bereits geteilten Bausteine ([NovelTypographyControls] / [NovelTocList]) — kein
 * Dialog-Rahmen mehr (`shared-structure-before-variants`). **Keine Animation** (`animation-gating`).
 */
@Composable
fun NovelSettingsSheet(
    selectedTab: NovelSheetTab,
    onTabChange: (NovelSheetTab) -> Unit,
    config: ReflowConfig,
    onFontSizeEm: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onFontWeight: (Int) -> Unit,
    onMargin: (String) -> Unit,
    onTextAlign: (String) -> Unit,
    onHyphenation: (String) -> Unit,
    onFontFamily: (String) -> Unit,
    chapters: List<Chapter>,
    onChapterSelected: (String) -> Unit,
    availableFonts: List<NovelFont> = NovelFonts.ALL,
    fontFiles: Map<String, File> = emptyMap(),
) {
    val strings = LocalStrings.current

    SheetTabRow(
        tabs = listOf(NovelSheetTab.TYPOGRAPHY to strings.novelTypography, NovelSheetTab.TOC to strings.novelToc),
        selected = selectedTab,
        onSelect = onTabChange,
    )

    when (selectedTab) {
        NovelSheetTab.TYPOGRAPHY -> NovelTypographyControls(
            fontSizeEm = config.fontSizeEm,
            onFontSize = onFontSizeEm,
            lineHeight = config.lineHeight,
            onLineHeight = onLineHeight,
            fontWeight = config.fontWeight,
            onFontWeight = onFontWeight,
            marginPreset = config.marginPreset(),
            onMargin = onMargin,
            textAlign = if (config.textAlign == TextAlign.LEFT) "LEFT" else "JUSTIFY",
            onTextAlign = onTextAlign,
            hyphenationLang = config.hyphenationLang(),
            onHyphenation = onHyphenation,
            fontFamily = config.fontFamily,
            onFontFamily = onFontFamily,
            availableFonts = availableFonts,
            fontFiles = fontFiles,
        )
        NovelSheetTab.TOC -> NovelTocList(
            chapters = chapters,
            onChapterSelected = onChapterSelected,
        )
    }
}

/**
 * Flache, monochrome Zwei-(oder mehr-)Segment-Tab-Leiste im Onyx-Look: ein Rahmen außen, der aktive
 * Tab füllt mit dem Mono-Akzent ([LocalDesignTokens]), die anderen sind transparent. **Keine
 * Animation** — sofortiger Wechsel.
 */
@Composable
private fun SheetTabRow(
    tabs: List<Pair<NovelSheetTab, String>>,
    selected: NovelSheetTab,
    onSelect: (NovelSheetTab) -> Unit,
) {
    val tokens = LocalDesignTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline),
    ) {
        tabs.forEach { (tab, label) ->
            val isSelected = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, onClick = { onSelect(tab) })
                    .background(if (isSelected) tokens.accent else MaterialTheme.colorScheme.surface)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) tokens.onAccent else MaterialTheme.colorScheme.onSurface,
                    textAlign = UiTextAlign.Center,
                )
            }
        }
    }
}

/** Reverse-Map: konkrete [ReflowConfig]-Ränder → Preset-String, den die Controls erwarten. */
private fun ReflowConfig.marginPreset(): String = when (margin) {
    NovelSettings.marginFor(NovelSettings.MARGIN_NARROW) -> NovelSettings.MARGIN_NARROW
    NovelSettings.marginFor(NovelSettings.MARGIN_WIDE) -> NovelSettings.MARGIN_WIDE
    else -> NovelSettings.MARGIN_NORMAL
}

/** Reverse-Map: [Hyphenation] → Sprachcode-String ("" = aus), den die Controls erwarten. */
private fun ReflowConfig.hyphenationLang(): String = when (val h = hyphenation) {
    is Hyphenation.Language -> h.lang
    Hyphenation.Off -> ""
}
```

Note on `LocalDesignTokens`: it exposes `accent`/`onAccent` (mono = black/white) — verify the exact property names in `ui-api/.../theme/` (`DesignTokens`); if they differ, use the existing names (the same ones `EinkBottomBar` uses for its selected row). If `LocalDesignTokens` is not the right accessor, fall back to `MaterialTheme.colorScheme.onSurface` (selected bg) / `surface` (selected text) for an inverted mono look.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:assembleDebug`
Expected: still FAIL on `NovelReaderScreen.kt` (NovelTypoPanel/NovelTocPanel references) until Task 6. Verify no *new* errors originate from `NovelSettingsSheet.kt` itself (read the error list — errors should only point at `NovelReaderScreen.kt`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelSettingsSheet.kt
git commit -m "feat(reader): NovelSettingsSheet — 2-tab (Typography/TOC) bottom-sheet content"
```

---

## Task 6: Wire `NovelReaderScreen`, delete the typo modal

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderScreen.kt`
- Delete: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypoPanel.kt`

- [ ] **Step 1: Delete the Typography modal**

```bash
git rm app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypoPanel.kt
```

- [ ] **Step 2: Add imports to `NovelReaderScreen.kt`**

Add:

```kotlin
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import com.komgareader.ui.slots.ReaderBottomSheet
```

- [ ] **Step 3: Replace the panel flags**

Remove these two lines (~85-86):

```kotlin
    var typoPanelOpen by remember { mutableStateOf(false) }
    var tocPanelOpen by remember { mutableStateOf(false) }
```

Add in their place:

```kotlin
    var sheetExpanded by remember { mutableStateOf(false) }
    var sheetTab by rememberSaveable { mutableStateOf(NovelSheetTab.TYPOGRAPHY) }
```

Keep `searchPanelOpen`, `bookmarkPanelOpen`, `renameId` as-is.

- [ ] **Step 4: Remove the typo + toc branches from the `when` block**

Delete the `typoPanelOpen -> NovelTypoPanel(...)` branch (~100-112) and the `tocPanelOpen -> NovelTocPanel(...)` branch (~113-117). Keep the `searchPanelOpen` and `bookmarkPanelOpen` branches. The `when {` now starts with `searchPanelOpen ->`.

- [ ] **Step 5: Add the `BackHandler`**

Immediately after the `when { ... }` block (and before the `renameId?.let { ... }` block), add:

```kotlin
    // Hardware-Back schließt zuerst das Bottom-Sheet.
    BackHandler(sheetExpanded) { sheetExpanded = false }
```

- [ ] **Step 6: Remove the TOC + Typography icons from `actions`**

In the `actions = { ... }` lambda, delete the two `IconButton`s — the TOC one (`onClick = { tocPanelOpen = true }`, AppIcons.TableOfContents, ~172-178) and the Typography one (`onClick = { typoPanelOpen = true }`, AppIcons.Typography, ~186-192). Keep the bookmark-mode toggle, the bookmarks-list button, and the search button.

- [ ] **Step 7: Pass `bottomSheet` to `ReaderScaffold`**

In the `ReaderScaffold(...)` call, add the `bottomSheet` argument after the `persistentBars = { ... },` block and before the trailing content lambda `) {`:

```kotlin
        bottomSheet = ReaderBottomSheet(
            expanded = sheetExpanded,
            onExpandedChange = { sheetExpanded = it },
            peekLabel = strings.novelSettings,
            content = {
                NovelSettingsSheet(
                    selectedTab = sheetTab,
                    onTabChange = { sheetTab = it },
                    config = reflowConfig,
                    onFontSizeEm = novelVm::setFontSizeEm,
                    onLineHeight = novelVm::setLineHeight,
                    onFontWeight = novelVm::setFontWeight,
                    onMargin = novelVm::setMargin,
                    onTextAlign = novelVm::setTextAlign,
                    onHyphenation = novelVm::setHyphenation,
                    onFontFamily = novelVm::setFontFamily,
                    chapters = chapters,
                    onChapterSelected = { anchor ->
                        novelVm.goToAnchor(anchor)
                        sheetExpanded = false
                    },
                    availableFonts = availableNovelFonts,
                    fontFiles = fontSampleFiles,
                )
            },
        ),
```

- [ ] **Step 8: Compile (now green)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL — `NovelTypoPanel`/`NovelTocPanel` no longer referenced; `NovelTocList`/`NovelSettingsSheet` resolve.

- [ ] **Step 9: Unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (no behavior change to tested pure logic; `groupChapters` unchanged).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderScreen.kt
git commit -m "feat(reader): novel Typography+TOC via bottom sheet; drop top icons + typo modal"
```

---

## Task 7: Debug swap/preview proof

**Files:**
- Create: `app/src/debug/kotlin/com/komgareader/app/ui/reader/ReaderBottomSheetPreview.kt`

- [ ] **Step 1: Create the preview**

```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.komgareader.ui.slots.ReaderBottomSheet

/**
 * Swap-/Layout-Beweis der `readerChrome`-Bottom-Sheet-Capability: links der eingeklappte Peek-Balken
 * (Chrome sichtbar), rechts das aufgeklappte vollbreite Sheet. Nur Debug/Preview — keine
 * Nutzer-Einstellung. (Die echte Wisch-Geste ist gerätegebunden; hier nur die zwei Render-Zustände.)
 */
@Preview(name = "Bottom sheet — collapsed peek", widthDp = 360, heightDp = 640)
@Composable
private fun BottomSheetCollapsedPreview() {
    Box(Modifier.fillMaxSize()) {
        val sheet = ReaderBottomSheet(
            expanded = false,
            onExpandedChange = {},
            peekLabel = "Einstellungen",
            content = { Text("Inhalt") },
        )
        ReaderBottomSheetLayer(sheet = sheet, chromeVisible = true)
    }
}

@Preview(name = "Bottom sheet — expanded", widthDp = 360, heightDp = 640)
@Composable
private fun BottomSheetExpandedPreview() {
    Box(Modifier.fillMaxSize()) {
        var expanded by remember { mutableStateOf(true) }
        val sheet = ReaderBottomSheet(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            peekLabel = "Einstellungen",
            content = { Text("Typografie / TOC Inhalt …") },
        )
        ReaderBottomSheetLayer(sheet = sheet, chromeVisible = true)
    }
}
```

- [ ] **Step 2: Compile debug**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/debug/kotlin/com/komgareader/app/ui/reader/ReaderBottomSheetPreview.kt
git commit -m "test(reader): debug preview for the reader bottom-sheet (collapsed + expanded)"
```

---

## Task 8: Docs sync + full verification

**Files:**
- Modify: `.claude/rules/architecture-seams.md`
- Modify: `.claude/rules/big-picture-and-goals.md`

- [ ] **Step 1: Update `architecture-seams.md`**

In the `readerChrome` region paragraph (Naht B / UI-Slot-Naht section), append a sentence documenting the new optional capability (Ist, 2026-06-15): the `readerChrome` surface (`ReaderScaffoldState`) grew an optional `bottomSheet: ReaderBottomSheet?` — host (`DefaultReaderScaffold`) owns the bottom-edge swipe-up + peek bar + scrim + expand/collapse and enforces the E-Ink invariants (instant on E-Ink, slide on phone); the reader supplies only `content`. The **only** consumer is the novel reader (Typography + TOC as two tabs, replacing the two centered modals); paged/comic/webtoon/epub pass `null`. Keep Soll/Ist separation; only state what `grep` finds.

- [ ] **Step 2: Update `big-picture-and-goals.md`**

In the ui-modularity "Gebaut" list for the `readerChrome` region, note the optional `bottomSheet` capability (novel-only consumer) added 2026-06-15.

- [ ] **Step 3: Full build + unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :ui-api:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Emulator E2E (manual, document result)**

Boot the `arm_test` AVD (Boox Go 7 geometry, per memory `local-test-komga`/`crengine-emulator-arm-translation`), open a NOVEL book, and verify:
1. Bottom swipe-up opens the sheet; peek bar (chrome visible) tap opens it; both land on the last-used tab.
2. Switch Typografie↔TOC; change font size → live re-layout; tap a TOC chapter → jumps + closes.
3. Close via scrim tap, hardware back, down-drag.
4. Sheet is full width; E-Ink mode = instant (no slide).
5. Page taps (left/right) still turn pages while the swipe strip is present; search + bookmark modals still work; bookmark-mode word-tap still works.

Note any device-bound gaps (crengine `.so` is arm64; the ARM-translation emulator runs it — see memory). Record the outcome in the commit message / final report.

- [ ] **Step 5: Commit docs**

```bash
git add .claude/rules/architecture-seams.md .claude/rules/big-picture-and-goals.md
git commit -m "docs: record readerChrome bottomSheet capability (novel Typography+TOC sheet)"
```

---

## Notes for the implementer

- **E-Ink first.** Never add an ungated animation. Every motion path checks `LocalEinkMode.current` and has an instant E-Ink branch (`animation-gating.md`).
- **Mono, flat, 1.5px border.** No elevation, no ripple, no accent color beyond the mono token. Labels via i18n (DE+EN).
- **Gesture coexistence.** The bottom swipe strip consumes only vertical upward drags; horizontal page taps and the bookmark-mode word-tap must keep working (mirror the frontlight strips' `change.consume()` discipline).
- **Don't touch Naht B.** The sheet is chrome only; it never references the `Viewer`, refresh, or engine navigation.
- **Search/Bookmarks untouched.** Leave their flags, branches, icons, and modals exactly as they are.
