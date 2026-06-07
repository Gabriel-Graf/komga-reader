# Suche-Reset + Werk-Typ-Filter — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Suchfeld bekommt einen ✕-Reset, und beim Stöbern lassen sich Werke per nicht-modalem Multi-Select-Menü nach Werk-Typ filtern (Chips im Suchfeld, Erklär-Platzhalter bei leerem Ergebnis).

**Architecture:** Reine Filterfunktion `filterSeries` (testbar) in `app/ui/library`. `EinkSearchBar` erhält Chips-Slot + ✕-Button. Filter-State in `HomeScreen`, durchgereicht an `LibraryScreen`. Nicht-modales `AnchoredMenuPopup` wird aus `SeriesDetailScreen` in `app/ui/components` extrahiert und vom neuen `TypeFilterMenu` mitgenutzt.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 5 (jupiter), Hilt. E-Ink-Designsprache (flach, Hairline-Border, keine Animationen).

---

## Datei-Übersicht

- Create: `app/src/main/kotlin/com/komgareader/app/ui/library/SeriesFilter.kt` — reine Filterfunktion
- Create: `app/src/test/kotlin/com/komgareader/app/ui/library/SeriesFilterTest.kt` — Unit-Tests
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/AnchoredMenuPopup.kt` — extrahiertes Popup
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/TypeFilterMenu.kt` — Multi-Select-Filtermenü
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt` — Popup-Duplikat entfernen, geteilte Version importieren
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/EinkSearchBar.kt` — Chips-Slot + ✕-Button
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` — neue Keys (Interface + StringsDe + StringsEn)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt` — Filter-State, Icon, Menü, Chips, ✕-Reset
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/library/LibraryScreen.kt` — Filter anwenden + Platzhalter

---

## Task 1: Reine Filterfunktion `filterSeries` (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/library/SeriesFilter.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/library/SeriesFilterTest.kt`

- [ ] **Step 1: Failing-Test schreiben**

`app/src/test/kotlin/com/komgareader/app/ui/library/SeriesFilterTest.kt`:
```kotlin
package com.komgareader.app.ui.library

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeriesFilterTest {

    private fun series(title: String, type: ContentType? = null) =
        Series(id = 0, sourceId = 1, remoteId = title, title = title, contentTypeOverride = type)

    private val all = listOf(
        series("Berserk", ContentType.MANGA),
        series("Saga", ContentType.COMIC),
        series("Tower", ContentType.WEBTOON),
        series("Roman ohne Typ", null),
    )

    @Test fun `empty query and empty filter returns all`() {
        assertEquals(all, filterSeries(all, "", emptySet()))
    }

    @Test fun `single type filter keeps only that type`() {
        assertEquals(listOf("Berserk"), filterSeries(all, "", setOf(ContentType.MANGA)).map { it.title })
    }

    @Test fun `multi type filter keeps all selected types`() {
        assertEquals(
            listOf("Berserk", "Saga"),
            filterSeries(all, "", setOf(ContentType.MANGA, ContentType.COMIC)).map { it.title },
        )
    }

    @Test fun `filter excludes untyped series`() {
        assertEquals(
            emptyList<String>(),
            filterSeries(all, "", setOf(ContentType.NOVEL)).map { it.title },
        )
    }

    @Test fun `all untyped with active filter returns empty`() {
        val untyped = listOf(series("A"), series("B"))
        assertEquals(emptyList<Series>(), filterSeries(untyped, "", setOf(ContentType.MANGA)))
    }

    @Test fun `query and type combine`() {
        assertEquals(
            listOf("Berserk"),
            filterSeries(all, "ber", setOf(ContentType.MANGA)).map { it.title },
        )
    }

    @Test fun `query is case insensitive`() {
        assertEquals(listOf("Saga"), filterSeries(all, "SAG", emptySet()).map { it.title })
    }
}
```

- [ ] **Step 2: Test laufen lassen (muss fehlschlagen)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.library.SeriesFilterTest"`
Expected: FAIL — `Unresolved reference: filterSeries`.

- [ ] **Step 3: Minimale Implementierung**

`app/src/main/kotlin/com/komgareader/app/ui/library/SeriesFilter.kt`:
```kotlin
package com.komgareader.app.ui.library

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series

/**
 * Reine Filterfunktion fürs Stöbern: behält eine Serie, wenn der Titel den (leeren oder
 * gesetzten) Suchtext enthält UND der Typ-Filter leer ist oder der manuell zugewiesene
 * Typ ([Series.contentTypeOverride]) zu den gewählten Typen gehört. Serien ohne Typ
 * fallen bei aktivem Filter immer heraus (beim Stöbern greift kein Regal-Default).
 */
fun filterSeries(
    series: List<Series>,
    query: String,
    types: Set<ContentType>,
): List<Series> = series.filter { item ->
    (query.isBlank() || item.title.contains(query, ignoreCase = true)) &&
        (types.isEmpty() || item.contentTypeOverride in types)
}
```

- [ ] **Step 4: Test laufen lassen (muss grün sein)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.library.SeriesFilterTest"`
Expected: PASS (7 Tests grün).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/library/SeriesFilter.kt app/src/test/kotlin/com/komgareader/app/ui/library/SeriesFilterTest.kt
git commit -m "feat(library): reine filterSeries-Funktion (Titel + Werk-Typ) mit Tests"
```

---

## Task 2: `AnchoredMenuPopup` in geteilte Komponente extrahieren (DRY)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/AnchoredMenuPopup.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt`

- [ ] **Step 1: Geteilte Komponente anlegen**

`app/src/main/kotlin/com/komgareader/app/ui/components/AnchoredMenuPopup.kt`:
```kotlin
package com.komgareader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Positioniert ein Popup an einem absoluten Fenster-Anker. [alignEnd] = true richtet die
 * rechte Kante am Anker aus (Dropdown nach links, z.B. unter einem Icon oben rechts);
 * sonst die linke Kante (Kontextmenü genau am Druckpunkt). Stets im Fenster geklemmt.
 */
private class AnchorPositionProvider(
    private val anchor: IntOffset,
    private val alignEnd: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val rawX = if (alignEnd) anchor.x - popupContentSize.width else anchor.x
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(rawX.coerceIn(0, maxX), anchor.y.coerceIn(0, maxY))
    }
}

/** Bordered E-Ink-Popup-Container am [anchor]; flach, kein Material-Dropdown. */
@Composable
fun AnchoredMenuPopup(
    anchor: IntOffset,
    alignEnd: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val provider = remember(anchor, alignEnd) { AnchorPositionProvider(anchor, alignEnd) }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(260.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            content = content,
        )
    }
}
```

- [ ] **Step 2: Privates Duplikat aus `SeriesDetailScreen.kt` entfernen**

Lösche in `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt` den kompletten Block `private class AnchorPositionProvider(...) { ... }` (ehemals ~Z.1180–1200) **und** die `private fun AnchoredMenuPopup(...)`-Funktion (ehemals ~Z.1202–1225). Beide Definitionen wandern komplett in die neue Datei.

- [ ] **Step 3: Geteilte Version importieren + ungenutzte Imports entfernen**

Füge in `SeriesDetailScreen.kt` zu den `com.komgareader.app.ui.components`-Importen hinzu:
```kotlin
import com.komgareader.app.ui.components.AnchoredMenuPopup
```
Entferne die jetzt ungenutzten Imports (waren nur fürs Popup):
```kotlin
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
```
`import androidx.compose.ui.unit.IntOffset` **bleibt** (Anker-State nutzt es weiter).

- [ ] **Step 4: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, keine „unresolved reference"-Fehler für `AnchoredMenuPopup`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/AnchoredMenuPopup.kt app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt
git commit -m "refactor(ui): AnchoredMenuPopup in geteilte Komponente extrahiert (DRY)"
```

---

## Task 3: i18n-Keys ergänzen

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

- [ ] **Step 1: Interface-Keys hinzufügen**

In `Strings.kt` im `interface Strings` direkt nach `val searchNoResults: String` (Z.108) einfügen:
```kotlin
    val clearSearch: String
    val filterByType: String
    val filterTypePlaceholder: String
```

- [ ] **Step 2: Deutsche Werte (`object StringsDe`) hinzufügen**

In `object StringsDe` nach `override val searchNoResults = "Keine Treffer"` (Z.238) einfügen:
```kotlin
    override val clearSearch = "Suche zurücksetzen"
    override val filterByType = "Nach Werk-Typ filtern"
    override val filterTypePlaceholder =
        "Keine Werke mit dem gewählten Typ gefunden.\n\n" +
            "Die App muss wissen, welcher Lesemodus zu welchem Werk gehört " +
            "(Manga, Comic, Webtoon, Roman). Lege den Typ entweder gesammelt im Tab " +
            "\"Bibliotheken\" fest (Bibliothek bearbeiten → Werk-Typ wählen — gilt für alle " +
            "Werke darin) oder einzeln in den Serien-Details über das Drei-Punkte-Menü oben " +
            "rechts → \"Typ zuweisen\"."
```

- [ ] **Step 3: Englische Werte (`object StringsEn`) hinzufügen**

In `object StringsEn` nach `override val searchNoResults = "No results"` (Z.368) einfügen:
```kotlin
    override val clearSearch = "Clear search"
    override val filterByType = "Filter by type"
    override val filterTypePlaceholder =
        "No works found for the selected type.\n\n" +
            "The app needs to know which reading mode each work uses " +
            "(Manga, Comic, Webtoon, Novel). Set the type either in bulk under the " +
            "\"Libraries\" tab (edit a library → choose work type — applies to everything " +
            "in it) or per series in the series details via the three-dot menu in the top " +
            "right → \"Assign type\"."
```

- [ ] **Step 4: Build prüfen (Compile-Zeit-Parität)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — fehlt ein Key in einer Sprache, bricht der Compiler (beide `object` müssen `Strings` voll implementieren).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "feat(i18n): Keys für Suche-Reset, Typ-Filter und Erklär-Platzhalter (DE/EN)"
```

---

## Task 4: `EinkSearchBar` — Chips-Slot + ✕-Reset

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/EinkSearchBar.kt`

- [ ] **Step 1: Signatur + Layout erweitern**

Ersetze in `EinkSearchBar.kt` die komplette `EinkSearchBar`-Funktion (Z.34–84) durch:
```kotlin
@Composable
fun EinkSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    actionLabel: String,
    modifier: Modifier = Modifier,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.height(40.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.size(6.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
                if (onClear != null && query.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = clearLabel,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onSubmit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = actionLabel,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
```

- [ ] **Step 2: Fehlende Imports ergänzen**

Füge in `EinkSearchBar.kt` zu den Imports hinzu:
```kotlin
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.Close
```

- [ ] **Step 3: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Bestehende Aufrufe ohne neue Parameter kompilieren weiter — alle neuen Parameter haben Defaults.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/EinkSearchBar.kt
git commit -m "feat(search): EinkSearchBar mit ✕-Reset und führendem Chip-Slot"
```

---

## Task 5: `TypeFilterMenu` — nicht-modales Multi-Select-Menü

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/TypeFilterMenu.kt`

- [ ] **Step 1: Komponente anlegen**

`app/src/main/kotlin/com/komgareader/app/ui/components/TypeFilterMenu.kt`:
```kotlin
package com.komgareader.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.localizedContentType
import com.komgareader.domain.model.ContentType

/**
 * Nicht-modales Multi-Select-Menü zum Filtern nach Werk-Typ (kein „Auto" — das ist ein
 * Filter, keine Zuweisung). Tippen toggelt einen Typ, das Menü bleibt offen; Häkchen
 * markiert aktive Typen. Klappt unter dem Filter-Icon oben rechts nach links auf.
 */
@Composable
fun TypeFilterMenu(
    anchor: IntOffset,
    selected: Set<ContentType>,
    onToggle: (ContentType) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val options = listOf(
        ContentType.MANGA,
        ContentType.COMIC,
        ContentType.WEBTOON,
        ContentType.NOVEL,
    )
    AnchoredMenuPopup(anchor = anchor, alignEnd = true, onDismiss = onDismiss) {
        options.forEachIndexed { index, type ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(type) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.localizedContentType(type),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (type in selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (index < options.lastIndex) HorizontalDivider()
        }
    }
}
```

- [ ] **Step 2: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`localizedContentType` ist eine Extension auf `Strings` in `Strings.kt`.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/TypeFilterMenu.kt
git commit -m "feat(library): TypeFilterMenu (nicht-modales Multi-Select-Overlay)"
```

---

## Task 6: `HomeScreen` — Filter-State, Icon, Menü, Chips, ✕-Reset

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt`

- [ ] **Step 1: Imports ergänzen**

Füge in `HomeScreen.kt` zu den Imports hinzu:
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.TypeFilterMenu
import com.komgareader.app.i18n.localizedContentType
import com.komgareader.domain.model.ContentType
```
Hinweis: `mutableStateOf`, `getValue`, `setValue`, `remember`, `Icon`, `IconButton`, `Box`, `Alignment`, `Modifier` sind bereits importiert.

- [ ] **Step 2: Filter-State + Menü-Anker als State anlegen**

In `fun HomeScreen(...)` direkt nach `var submitted by rememberSaveable { mutableStateOf("") }` (Z.61) einfügen:
```kotlin
    val typeFilter = rememberSaveable(
        saver = listSaver<MutableState<Set<ContentType>>, Int>(
            save = { it.value.map(ContentType::ordinal) },
            restore = { mutableStateOf(it.map { o -> ContentType.entries[o] }.toSet()) },
        ),
    ) { mutableStateOf(emptySet()) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var filterAnchor by remember { mutableStateOf(IntOffset.Zero) }
```
Ergänze dafür die Imports:
```kotlin
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.setValue
```
(`getValue` ist bereits importiert.)

- [ ] **Step 3: Tab-Wechsel setzt Filter mit zurück**

In der `EinkBottomBar`-`onSelect`-Lambda (Z.115–120) nach `submitted = ""` einfügen:
```kotlin
                    typeFilter.value = emptySet()
                    filterMenuOpen = false
```

- [ ] **Step 4: `EinkSearchBar`-Aufruf um Chips + ✕ erweitern**

Ersetze den `EinkSearchBar(...)`-Aufruf (Z.89–96) durch:
```kotlin
                        EinkSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            onSubmit = { submitSearch() },
                            placeholder = if (onSettingsTab) s.searchSettingsHint else s.searchMediaHint,
                            actionLabel = s.searchAction,
                            clearLabel = s.clearSearch,
                            onClear = { query = ""; submitted = "" },
                            leading = if (selected == TAB_LIBRARY && typeFilter.value.isNotEmpty()) {
                                {
                                    typeFilter.value.forEach { type ->
                                        FilterChip(
                                            label = s.localizedContentType(type),
                                            onRemove = { typeFilter.value = typeFilter.value - type },
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(0.6f).widthIn(max = 360.dp),
                        )
```
(`localizedContentType` wurde bereits in Step 1 importiert.)

- [ ] **Step 5: Filter-Icon im CenterEnd-Cluster (nur Library-Tab)**

Ersetze den `Box(Modifier.align(Alignment.CenterEnd))`-Block (Z.97–106) durch:
```kotlin
                        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                            if (selected == TAB_LIBRARY) {
                                IconButton(
                                    onClick = { filterMenuOpen = true },
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        val pos = coords.positionInWindow()
                                        filterAnchor = IntOffset(
                                            (pos.x + coords.size.width).toInt(),
                                            (pos.y + coords.size.height).toInt(),
                                        )
                                    },
                                ) {
                                    Icon(Icons.Outlined.FilterList, contentDescription = s.filterByType)
                                }
                            }
                            when (selected) {
                                TAB_LIBRARY -> IconButton(onClick = { libraryVm.refresh() }) {
                                    Icon(Icons.Outlined.Sync, contentDescription = null)
                                }
                                TAB_GROUPS -> IconButton(onClick = { showCreateGroup = true }) {
                                    Icon(Icons.Outlined.Add, contentDescription = s.newGroup)
                                }
                            }
                        }
```
Ergänze den Import:
```kotlin
import androidx.compose.ui.layout.positionInWindow
```

- [ ] **Step 6: Filter-Menü rendern**

Direkt nach dem `TopAppBar { ... }`-Title-Block, noch innerhalb des `title = { Box(...) { ... } }` (also direkt vor dessen schließender `}` der äußeren Box, Z.107), einfügen:
```kotlin
                        if (filterMenuOpen && selected == TAB_LIBRARY) {
                            TypeFilterMenu(
                                anchor = filterAnchor,
                                selected = typeFilter.value,
                                onToggle = { type ->
                                    typeFilter.value =
                                        if (type in typeFilter.value) typeFilter.value - type
                                        else typeFilter.value + type
                                },
                                onDismiss = { filterMenuOpen = false },
                            )
                        }
```

- [ ] **Step 7: Filter an `LibraryScreen` durchreichen**

Ersetze die `TAB_LIBRARY -> LibraryScreen(...)`-Zeile (Z.126) durch:
```kotlin
                TAB_LIBRARY -> LibraryScreen(
                    query = submitted,
                    typeFilter = typeFilter.value,
                    onOpenSeries = onOpenSeries,
                    viewModel = libraryVm,
                )
```

- [ ] **Step 8: Lokalen `FilterChip`-Helfer anlegen**

Am Dateiende von `HomeScreen.kt` (nach der schließenden `}` von `HomeScreen`) einfügen:
```kotlin
/** Kompakter E-Ink-Filter-Chip im Suchfeld: Label + ✕ zum Entfernen des Typs. */
@Composable
private fun FilterChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface)
            .clickable(onClick = onRemove)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.surface,
        )
        Icon(
            Icons.Outlined.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(14.dp),
        )
    }
}
```
Ergänze den Import:
```kotlin
import androidx.compose.material3.MaterialTheme
```

- [ ] **Step 9: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt
git commit -m "feat(home): Werk-Typ-Filter-Icon, Multi-Select-Menü, Filter-Chips, ✕-Reset"
```

---

## Task 7: `LibraryScreen` — Filter anwenden + Erklär-Platzhalter

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/library/LibraryScreen.kt`

- [ ] **Step 1: `LibraryScreen`-Signatur + Durchreichung**

Ersetze die `LibraryScreen`-Signatur (Z.54–60) durch:
```kotlin
@Composable
fun LibraryScreen(
    query: String = "",
    typeFilter: Set<ContentType> = emptySet(),
    onOpenSeries: (seriesId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
```
Ergänze den Import:
```kotlin
import com.komgareader.domain.model.ContentType
```

- [ ] **Step 2: `BrowseTab`-Aufruf um `typeFilter` erweitern**

Im `BrowseTab(...)`-Aufruf (Z.80–88) nach `query = query,` einfügen:
```kotlin
            typeFilter = typeFilter,
```

- [ ] **Step 3: `BrowseTab`-Signatur erweitern**

In der `private fun BrowseTab(`-Signatur (Z.92–101) nach `query: String,` einfügen:
```kotlin
    typeFilter: Set<ContentType>,
```

- [ ] **Step 4: Filter-Logik + Platzhalter im Content-Zweig**

Ersetze den kompletten `is LibraryUiState.Content -> { ... }`-Block (Z.124–152) durch:
```kotlin
        is LibraryUiState.Content -> {
            val shown = remember(current.series, query, typeFilter) {
                filterSeries(current.series, query, typeFilter)
            }
            if (shown.isEmpty() && typeFilter.isNotEmpty()) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(
                        s.filterTypePlaceholder,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else if (shown.isEmpty() && query.isNotBlank()) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(s.searchNoResults, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown) { series ->
                        SeriesCover(
                            series = series,
                            serverConfig = current.serverConfig,
                            isLocal = series.remoteId in localSeriesIds,
                            onClick = { onOpenSeries(series.remoteId) },
                            onLongClick = { onDownload(series) },
                        )
                    }
                }
            }
        }
```
Hinweis: `filterSeries` stammt aus Task 1 (gleiches Package `com.komgareader.app.ui.library`, kein Import nötig).

- [ ] **Step 5: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Volltest + Commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (inkl. `SeriesFilterTest`).
```bash
git add app/src/main/kotlin/com/komgareader/app/ui/library/LibraryScreen.kt
git commit -m "feat(library): Werk-Typ-Filter anwenden + Erklär-Platzhalter bei leerem Ergebnis"
```

---

## Task 8: Verifikation (Build + E2E-Screenshot)

**Files:** keine

- [ ] **Step 1: Voller Debug-Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: APK auf Emulator `eink_test` installieren**

Stelle sicher, dass der Emulator `eink_test` (1264×1680@300) läuft und die lokale Test-Komga
(siehe Memory `local-test-komga`) verbunden ist. Dann:
Run: `./gradlew :app:installDebug`
Expected: `Installed on 1 device`.

- [ ] **Step 3: Manuelle E2E-Prüfung (Screenshots als Beweis)**

Auf dem Browse-/Bibliothek-Tab prüfen und je einen Screenshot ablegen
(`adb exec-out screencap -p > /tmp/<name>.png`):
1. Text eingeben → ✕ erscheint links der Lupe → ✕ tippen → Suche **und** Ergebnisse zurückgesetzt.
2. Filter-Icon (rechts neben Sync) tippen → nicht-modales Menü → Manga + Comic wählen → zwei Chips
   im Suchfeld; Grid zeigt nur Manga/Comic.
3. Einen Chip-✕ tippen → Typ verschwindet, Grid aktualisiert.
4. Filter auf einen Typ ohne Treffer setzen (bei verbundenem Server) → ausführlicher
   Erklär-Platzhalter (Bibliotheken / 3-Punkte-Menü).
5. Ohne Server + Filter aktiv → weiterhin „Server verbinden"-Platzhalter (`libraryEmpty`).

- [ ] **Step 4: Abschluss-Commit (falls noch uncommittete Reste)**

```bash
git status
```
Erwartung: working tree clean (alle Änderungen in Tasks 1–7 committet).

---

## Self-Review-Notizen

- **Spec-Abdeckung:** A (Task 4+6), B (Tasks 1,5,6,7), C (Task 7), D-Refactor (Task 2),
  i18n (Task 3), Tests (Task 1), E2E (Task 8). Alle Spec-Abschnitte abgedeckt.
- **Typ-Konsistenz:** `filterSeries(series, query, types)` einheitlich in Tasks 1/7;
  `TypeFilterMenu(anchor, selected, onToggle, onDismiss)` einheitlich in Tasks 5/6;
  `EinkSearchBar`-Parameter `clearLabel/onClear/leading` einheitlich in Tasks 4/6;
  `localizedContentType` (existierende Extension) — kein erfundener Name verwendet.
- **Saver:** `Set<ContentType>` über Ordinals (`listSaver`) — überlebt Prozess-Tod via `rememberSaveable`.
