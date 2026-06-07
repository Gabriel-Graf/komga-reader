# Kapitel-Ansicht Liste/Kachel-Toggle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In der Serien-Detail-Ansicht die Kapitelliste per Toggle zwischen Textliste und Cover-Kachel-Gitter umschaltbar machen; der Modus gilt global und wird persistiert.

**Architecture:** Neues String-Setting `chapterViewMode` ("LIST"|"GRID") über das bestehende `SettingsRepository`/`RoomSettingsRepository`-Muster. `SeriesDetailViewModel` exponiert es als eigenständigen `StateFlow` (kein Reload-Combine). `SeriesDetailScreen` rendert den ganzen Inhalt in **einem** `LazyVerticalGrid` (adaptiv): Hero + Header full-span, Kapitel je nach Modus als full-span `ChapterRow` oder als Kachel `ChapterTile`. Toggle ersetzt das entfernte Einklapp-Icon im Kapitel-Header.

**Tech Stack:** Kotlin, Jetpack Compose (LazyVerticalGrid), Hilt, Room (key/value settings), Coil (`FilteredAsyncImage`), Material Symbols Outlined, kotlin.test.

**Referenz-Spec:** `docs/superpowers/specs/2026-06-07-chapter-tile-view-design.md`

**Verbindliche Regeln:** `eink-ui`-Skill (Tokens, Outlined-Icons, Hairline ≥1.5dp), `eink-design-language.md` (keine Animationen/Schatten, monochrom), Naht A (Cover-URL spiegelt bestehendes Series-Muster).

---

## Dateien-Übersicht

| Datei | Verantwortung | Aktion |
|---|---|---|
| `domain/.../repository/SettingsRepository.kt` | Setting-Interface | Modify: `chapterViewMode` + Setter |
| `data/.../repository/RoomSettingsRepository.kt` | Room-Impl | Modify: Key + Flow + Setter |
| `data/src/test/.../repository/RoomSettingsRepositoryTest.kt` | Unit-Test (Fake-DAO) | Create |
| `app/.../ui/series/SeriesDetailViewModel.kt` | VM-Exposition | Modify: inject `SettingsRepository`, `chapterViewMode` + `setChapterViewMode` |
| `app/.../i18n/Strings.kt` | i18n-Keys | Modify: 2 Toggle-Keys (DE+EN) |
| `app/.../ui/series/SeriesDetailScreen.kt` | Detail-UI | Modify: Header-Toggle, ein LazyVerticalGrid, `ChapterTile` |

Kein Room-Schema-Bump (die `settings`-Tabelle ist generisch key/value).

---

## Task 1: Setting `chapterViewMode` in domain + data (TDD)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt`

- [ ] **Step 1: Failing Test schreiben**

Erstelle `data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt`:

```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.SettingEntity
import com.komgareader.data.db.SettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-Memory-Fake des SettingsDao — kein Room nötig (reiner Unit-Test). */
private class FakeSettingsDao : SettingsDao {
    private val store = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String): Flow<String?> = store.map { it[key] }
    override suspend fun put(entity: SettingEntity) {
        store.value = store.value + (entity.key to entity.value)
    }
    override suspend fun delete(key: String) {
        store.value = store.value - key
    }
}

class RoomSettingsRepositoryTest {

    @Test
    fun `chapterViewMode default ist LIST ohne gesetzten Wert`() = runBlocking {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals("LIST", repo.chapterViewMode.first())
    }

    @Test
    fun `setChapterViewMode persistiert und Flow liefert neuen Wert`() = runBlocking {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setChapterViewMode("GRID")
        assertEquals("GRID", repo.chapterViewMode.first())
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen (Kompilierfehler: `chapterViewMode` existiert nicht)**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomSettingsRepositoryTest"`
Expected: FAIL — `unresolved reference: chapterViewMode` / `setChapterViewMode`.

- [ ] **Step 3: Interface erweitern**

In `domain/.../repository/SettingsRepository.kt` — Property nach `webtoonOverlapPercent` (Zeile 12) ergänzen:

```kotlin
    val webtoonOverlapPercent: Flow<Int>  // Überlappung zwischen Webtoon-Streifen in Prozent (0–50)
    val chapterViewMode: Flow<String>  // "LIST" | "GRID" — Kapitel als Textliste oder Cover-Gitter
```

und den Setter nach `setWebtoonOverlapPercent` (Zeile 18) ergänzen:

```kotlin
    suspend fun setWebtoonOverlapPercent(percent: Int)
    suspend fun setChapterViewMode(mode: String)
```

- [ ] **Step 4: Room-Impl erweitern**

In `data/.../repository/RoomSettingsRepository.kt`:

Flow-Property nach Zeile 18 (`webtoonOverlapPercent`) ergänzen:

```kotlin
    override val chapterViewMode: Flow<String> =
        dao.observe(KEY_CHAPTER_VIEW_MODE).map { it ?: "LIST" }
```

Setter nach `setWebtoonOverlapPercent` (Zeile 28) ergänzen:

```kotlin
    override suspend fun setChapterViewMode(mode: String) =
        dao.put(SettingEntity(KEY_CHAPTER_VIEW_MODE, mode))
```

Key-Konstante im `companion object` nach `KEY_WEBTOON_OVERLAP` (Zeile 39) ergänzen:

```kotlin
        const val KEY_WEBTOON_OVERLAP = "webtoon_overlap_percent"
        const val KEY_CHAPTER_VIEW_MODE = "chapter_view_mode"
```

- [ ] **Step 5: Test laufen lassen — muss bestehen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomSettingsRepositoryTest"`
Expected: PASS (2 Tests).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt
git commit -m "feat(settings): chapterViewMode-Setting (Liste/Kachel) persistiert"
```

---

## Task 2: `SeriesDetailViewModel` exponiert chapterViewMode

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailViewModel.kt`

Begründung: Der Modus ist **unabhängig** vom Content-State — er darf **nicht** in den `state`-`combine` einfließen (sonst Voll-Reload/Ghosting beim Umschalten). Eigener `stateIn`-Flow + Setter, gespiegelt am `SettingsViewModel`-Muster.

- [ ] **Step 1: Import + Konstruktor-Injektion ergänzen**

Import nach Zeile 21 (`ShelfRepository`) ergänzen:

```kotlin
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.repository.SettingsRepository
```

Konstruktor-Parameter nach `readProgressRepository` (Zeile 98) ergänzen:

```kotlin
    private val readProgressRepository: ReadProgressRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
```

- [ ] **Step 2: StateFlow + Setter ergänzen**

Direkt nach dem `localBookIds`-Block (nach Zeile 224) einfügen:

```kotlin
    /** Globaler Kapitel-Anzeigemodus ("LIST"|"GRID") — unabhängig vom Content-State (kein Reload). */
    val chapterViewMode: StateFlow<String> = settings.chapterViewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "LIST")

    /** Schaltet den globalen Kapitel-Anzeigemodus um und persistiert ihn. */
    fun setChapterViewMode(mode: String) {
        viewModelScope.launch { settings.setChapterViewMode(mode) }
    }
```

- [ ] **Step 3: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt löst `SettingsRepository` über das bestehende `DataModule.settingsRepository`-Binding auf — keine DI-Änderung nötig).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailViewModel.kt
git commit -m "feat(series): VM exponiert chapterViewMode + Setter"
```

---

## Task 3: i18n-Keys für den Toggle (DE + EN)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

- [ ] **Step 1: Interface-Keys ergänzen**

Im `interface Strings` neben den anderen Kapitel-bezogenen Keys (z. B. nach `val chapters`) ergänzen:

```kotlin
    val chapterViewSwitchToGrid: String
    val chapterViewSwitchToList: String
```

- [ ] **Step 2: Deutsche Werte (`object StringsDe`)**

```kotlin
    override val chapterViewSwitchToGrid = "Kachelansicht"
    override val chapterViewSwitchToList = "Listenansicht"
```

- [ ] **Step 3: Englische Werte (`object StringsEn`)**

```kotlin
    override val chapterViewSwitchToGrid = "Grid view"
    override val chapterViewSwitchToList = "List view"
```

- [ ] **Step 4: Kompilieren (Compile-Zeit-Parität DE/EN erzwungen durch Interface)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fehlt eine Sprache ein Key → Kompilierfehler.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "i18n(series): Keys für Kapitel-Ansicht-Toggle (DE/EN)"
```

---

## Task 4: Header-Umbau — Einklappen entfernen, Toggle einführen

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt`

Ziel dieses Tasks: das Einklapp-Feature **vollständig** entfernen und durch den Liste/Kachel-Toggle ersetzen. Die Kapitel-Render-Logik (Liste vs. Grid) kommt in Task 6 — hier bleibt vorerst die bestehende `ChapterRow`-Liste, nur ohne `AnimatedVisibility`/Collapse.

- [ ] **Step 1: Nicht mehr benötigte Imports entfernen**

In `SeriesDetailScreen.kt` diese Imports löschen (Collapse-Animation entfällt):

```
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
```

- [ ] **Step 2: Toggle-Icon-Imports + LazyVerticalGrid-Imports ergänzen**

Ergänzen (alphabetisch passend einsortieren):

```kotlin
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import com.komgareader.app.ui.theme.EinkTokens
```

- [ ] **Step 3: `ChaptersSectionHeader` umbauen (Collapse raus, Toggle rein)**

Ersetze die komplette `ChaptersSectionHeader`-Funktion (Zeilen 586–611) durch:

```kotlin
@Composable
private fun ChaptersSectionHeader(
    count: Int,
    gridMode: Boolean,
    onToggleViewMode: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${s.chapters} ($count)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        // Toggle Liste ⇄ Kachel: zeigt das Ziel-Layout-Icon. Sofortiger Wechsel (E-Ink).
        IconButton(onClick = onToggleViewMode) {
            Icon(
                if (gridMode) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription =
                    if (gridMode) s.chapterViewSwitchToList else s.chapterViewSwitchToGrid,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
```

- [ ] **Step 4: `SeriesDetailContent` — Collapse-State entfernen, Modus durchreichen**

In `SeriesDetailContent` (ab Zeile 224):

(a) Zeile 229 löschen:
```kotlin
    var chaptersExpanded by rememberSaveable { mutableStateOf(true) }
```

(b) Neue Parameter zur `SeriesDetailContent`-Signatur ergänzen (nach `onTypeMenuAnchor`, vor `modifier`):
```kotlin
    onTypeMenuAnchor: (IntOffset) -> Unit,
    gridMode: Boolean,
    onToggleViewMode: () -> Unit,
    modifier: Modifier = Modifier,
```

(c) Den `ChaptersSectionHeader`-Aufruf (Zeilen 274–280) ersetzen durch:
```kotlin
        item {
            ChaptersSectionHeader(
                count = books.size,
                gridMode = gridMode,
                onToggleViewMode = onToggleViewMode,
            )
        }
```

(d) Den `AnimatedVisibility`-Kapitelblock (Zeilen 282–314) ersetzen durch die **schlichte Liste ohne Animation** (Grid kommt in Task 6):
```kotlin
        item {
            Column {
                books.forEach { book ->
                    ChapterRow(
                        book = book,
                        isSelected = book.remoteId == currentBook?.remoteId,
                        showBookmark = book.remoteId == bookmarkBookId,
                        isLocal = book.remoteId in localIds,
                        isDownloading = book.remoteId in downloadingIds,
                        onOpen = {
                            onOpenChapter(book)
                            onOpenBook(
                                book.remoteId, book.pageCount, book.format.name, false,
                                viewerModes[book.remoteId] ?: "PAGED",
                            )
                        },
                        onDownload = { onDownload(book) },
                        onRemoveDownload = { onRemoveDownload(book.remoteId) },
                        onSetRead = { read -> onSetRead(book, read) },
                    )
                    HorizontalDivider()
                }
            }
        }
```

- [ ] **Step 5: `SeriesDetailScreen` — Modus lesen + an Content geben**

Im `SeriesDetailScreen` nach Zeile 101 (`cancelling`) ergänzen:
```kotlin
    val chapterViewMode by viewModel.chapterViewMode.collectAsState()
```

Im `SeriesDetailContent`-Aufruf (nach `onTypeMenuAnchor = { burgerAnchor = it },`, Zeile 175) ergänzen:
```kotlin
                    onTypeMenuAnchor = { burgerAnchor = it },
                    gridMode = chapterViewMode == "GRID",
                    onToggleViewMode = {
                        viewModel.setChapterViewMode(if (chapterViewMode == "GRID") "LIST" else "GRID")
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
```

- [ ] **Step 6: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Kapitel sind nun immer sichtbar; Toggle-Button da, schaltet das Setting (Grid-Render folgt in Task 6 — bis dahin bleibt die Liste in beiden Modi).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt
git commit -m "refactor(series): Kapitel-Einklappen entfernt, Liste/Kachel-Toggle im Header"
```

---

## Task 5: `ChapterTile`-Composable (Cover + Ecken-Overlays)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt`

Reine UI-Composable — visuell, kein Unit-Test (Verifikation per E2E in Task 7). Imports `ImageRequest`, `AuthHeaders`, `FilteredAsyncImage`, `LocalContext`, `EinkActionMenu`, `LoadingIndicator` sind bereits in der Datei.

- [ ] **Step 1: Zusätzliche Imports ergänzen**

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.text.style.TextOverflow
```

(Falls ein Import bereits existiert, nicht doppelt hinzufügen.)

- [ ] **Step 2: `ChapterTile` + `CoverBadge` ans Dateiende (nach `ChapterRow`) ergänzen**

```kotlin
/**
 * Kapitel als Kachel: eigenes Cover mit Status-/Aktions-Overlays in den Ecken.
 * oben-rechts = Lesezeichen + „gelesen"-Häkchen, unten-rechts = Download/Löschen.
 * Tap öffnet das Kapitel, Long-Press öffnet das Gelesen/Ungelesen-Menü (wie ChapterRow).
 */
@Composable
private fun ChapterTile(
    book: Book,
    serverConfig: ServerConfig?,
    isSelected: Boolean,
    showBookmark: Boolean,
    isLocal: Boolean,
    isDownloading: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onSetRead: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var tilePos by remember { mutableStateOf(Offset.Zero) }
    var pressAnchor by remember { mutableStateOf(IntOffset.Zero) }
    val coverUrl = serverConfig?.let { "${it.baseUrl}books/${book.remoteId}/thumbnail" }

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .onGloballyPositioned { tilePos = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { local ->
                        pressAnchor = IntOffset(
                            (tilePos.x + local.x).toInt(),
                            (tilePos.y + local.y).toInt(),
                        )
                        menuOpen = true
                    },
                )
            }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .border(
                    EinkTokens.hairline,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(EinkTokens.tileRadius),
                )
                .clip(RoundedCornerShape(EinkTokens.tileRadius)),
        ) {
            if (coverUrl != null) {
                val authHeaders = AuthHeaders.forCovers(serverConfig)
                FilteredAsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(coverUrl)
                        .apply { authHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .crossfade(false)
                        .build(),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // oben-rechts: Lesezeichen + Häkchen (nur was zutrifft), vertikal gestapelt.
            Column(
                Modifier.align(Alignment.TopEnd).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showBookmark) CoverBadge {
                    Icon(
                        Icons.Outlined.Bookmark,
                        contentDescription = s.resumeHere,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (book.readCompleted) CoverBadge {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = s.statusRead,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // unten-rechts: Download / Löschen / Lade-Spinner.
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                when {
                    isDownloading -> CoverBadge {
                        LoadingIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    isLocal -> CoverBadge(onClick = onRemoveDownload) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = s.removeDownload,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    else -> CoverBadge(onClick = onDownload) {
                        Icon(
                            Icons.Outlined.CloudDownload,
                            contentDescription = s.download,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            book.number?.let { "$it · ${book.title}" } ?: book.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (menuOpen) {
        EinkActionMenu(
            anchor = pressAnchor,
            onDismiss = { menuOpen = false },
            items = listOf(
                s.markRead to { onSetRead(true) },
                s.markUnread to { onSetRead(false) },
            ),
        )
    }
}

/**
 * Opaker Eck-Chip auf dem Cover: solide Fläche + Hairline-Rahmen, damit Icons auf
 * jedem (Kaleido-)Cover kontraststark bleiben. Optional klickbar (Download/Löschen).
 */
@Composable
private fun CoverBadge(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val base = Modifier
        .background(MaterialTheme.colorScheme.surface, shape)
        .border(EinkTokens.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
    val sized = if (onClick != null) base.size(36.dp).clickable(onClick = onClick) else base.padding(4.dp)
    Box(sized, contentAlignment = Alignment.Center) { content() }
}
```

- [ ] **Step 3: Kompilieren (Composable noch ungenutzt — Grid-Verdrahtung in Task 6)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (ggf. „unused"-Warnung für `ChapterTile` — in Task 6 verdrahtet).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt
git commit -m "feat(series): ChapterTile mit Cover + Ecken-Overlay-Badges"
```

---

## Task 6: Ein LazyVerticalGrid — Liste vs. Kachel-Gitter verdrahten

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt`

Den `LazyColumn`-Container in `SeriesDetailContent` durch **ein** `LazyVerticalGrid` ersetzen: Hero + Header full-span; Kapitel im LIST-Modus full-span `ChapterRow`, im GRID-Modus adaptive `ChapterTile`-Zellen.

- [ ] **Step 1: `LazyColumn` durch `LazyVerticalGrid` ersetzen**

In `SeriesDetailContent`: `LazyColumn(modifier = modifier) {` (Zeile 239) ersetzen durch:

```kotlin
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        contentPadding = PaddingValues(horizontal = EinkTokens.screenPadding, vertical = EinkTokens.tileGap),
    ) {
```

- [ ] **Step 2: Hero- und Header-`item` auf full-span setzen**

Den Hero-`item { SeriesHeroCard(...) }` (Zeile 241) öffnen als:
```kotlin
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeriesHeroCard(
```
(Der `SeriesHeroCard`-Aufruf selbst bleibt unverändert. Das `modifier = Modifier.padding(12.dp)` im Aufruf bleibt — Grid-`contentPadding` ersetzt nur die LazyColumn-Ränder.)

Den Header-`item { ChaptersSectionHeader(...) }` aus Task 4 öffnen als:
```kotlin
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChaptersSectionHeader(
```

- [ ] **Step 3: Kapitel-Rendering nach Modus aufteilen**

Den schlichten Listen-`item { Column { books.forEach { ChapterRow(...) } } }` aus Task 4 ersetzen durch:

```kotlin
        if (gridMode) {
            items(books, key = { it.remoteId }) { book ->
                ChapterTile(
                    book = book,
                    serverConfig = serverConfig,
                    isSelected = book.remoteId == currentBook?.remoteId,
                    showBookmark = book.remoteId == bookmarkBookId,
                    isLocal = book.remoteId in localIds,
                    isDownloading = book.remoteId in downloadingIds,
                    onOpen = {
                        onOpenChapter(book)
                        onOpenBook(
                            book.remoteId, book.pageCount, book.format.name, false,
                            viewerModes[book.remoteId] ?: "PAGED",
                        )
                    },
                    onDownload = { onDownload(book) },
                    onRemoveDownload = { onRemoveDownload(book.remoteId) },
                    onSetRead = { read -> onSetRead(book, read) },
                )
            }
        } else {
            items(books, key = { it.remoteId }, span = { GridItemSpan(maxLineSpan) }) { book ->
                Column {
                    ChapterRow(
                        book = book,
                        isSelected = book.remoteId == currentBook?.remoteId,
                        showBookmark = book.remoteId == bookmarkBookId,
                        isLocal = book.remoteId in localIds,
                        isDownloading = book.remoteId in downloadingIds,
                        onOpen = {
                            onOpenChapter(book)
                            onOpenBook(
                                book.remoteId, book.pageCount, book.format.name, false,
                                viewerModes[book.remoteId] ?: "PAGED",
                            )
                        },
                        onDownload = { onDownload(book) },
                        onRemoveDownload = { onRemoveDownload(book.remoteId) },
                        onSetRead = { read -> onSetRead(book, read) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
```

- [ ] **Step 4: Verwaiste Imports prüfen/entfernen**

Falls `LazyColumn` (`androidx.compose.foundation.lazy.LazyColumn`) jetzt ungenutzt ist → Import entfernen. `androidx.compose.foundation.layout.PaddingValues` ist bereits importiert (Zeile 15).

- [ ] **Step 5: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Unit-Tests gesamt grün**

Run: `./gradlew :data:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS (inkl. `RoomSettingsRepositoryTest`).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt
git commit -m "feat(series): adaptives LazyVerticalGrid — Liste/Kachel je nach Modus"
```

---

## Task 7: E2E-Verifikation (Emulator-Screenshots)

**Files:**
- Keine Code-Änderung — Verifikation gegen lokale Test-Komga + Emulator `eink_test` (1264×1680@300, siehe Memory `local-test-komga`).

- [ ] **Step 1: Debug-APK bauen**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Emulator `eink_test` starten, App installieren, Test-Komga verbinden**

Sicherstellen, dass die lokale Test-Komga (Docker) läuft und der Server in der App verbunden ist. Eine Serie mit mehreren Kapiteln öffnen (z. B. Berserk/Saga).

- [ ] **Step 3: Listen-Modus screenshotten**

Detail-Ansicht öffnen (Default „LIST"). Screenshot ziehen:
```bash
adb -s emulator-5554 exec-out screencap -p > /tmp/chapter-list.png
```
Erwartet: Kapitel als Textliste, im Kapitel-Header rechts das **Gitter-Icon** (`GridView`), kein Einklapp-Pfeil mehr.

- [ ] **Step 4: Toggle → Kachel-Modus, screenshotten**

Auf das Toggle-Icon tippen. Screenshot:
```bash
adb -s emulator-5554 exec-out screencap -p > /tmp/chapter-grid.png
```
Erwartet: ~4-spaltiges Cover-Gitter; je Kachel Cover + Titel darunter; Overlays — Lesezeichen/Häkchen oben-rechts, Download/Löschen unten-rechts (als kontraststarke Chips); Header-Icon jetzt **Listen-Icon** (`ViewList`).

- [ ] **Step 5: Persistenz prüfen**

Zurück zur Bibliothek, andere Serie öffnen → muss ebenfalls im Kachel-Modus erscheinen (global). App-Neustart → Modus bleibt „GRID".
```bash
adb -s emulator-5554 shell am force-stop com.komgareader.app && adb -s emulator-5554 shell am start -n com.komgareader.app/.MainActivity
```
Erwartet: nach Neustart weiterhin Kachel-Modus.

- [ ] **Step 6: Beide Screenshots sichten (Read-Tool) und gegen die Erwartung prüfen**

Read `/tmp/chapter-list.png` und `/tmp/chapter-grid.png`. Prüfen: E-Ink-Kontrast der Eck-Chips, Hairline-Rahmen sichtbar, keine abgeschnittenen Titel, Toggle-Icon korrekt.

- [ ] **Step 7: Abschluss-Hinweis**

Kein eigener Commit nötig (keine Code-Änderung). Bei sichtbaren Mängeln → betroffenen Task erneut öffnen (Tile-Maße/Chip-Kontrast in Task 5, Spaltenzahl `minSize` in Task 6).

---

## Self-Review (durch den Plan-Autor)

- **Spec-Coverage:** Setting (T1) · VM-Exposition ohne Reload (T2) · i18n (T3) · Einklappen raus + Toggle (T4) · ChapterTile mit Ecken-Overlays (T5) · ein LazyVerticalGrid adaptiv (T6) · TDD-Unit + E2E (T1, T7). Naht-A-Cover-URL gespiegelt (T5). Alle Spec-Abschnitte abgedeckt.
- **Typ-Konsistenz:** `chapterViewMode`/`setChapterViewMode` identisch in domain/data/VM. Modus-Strings `"LIST"`/`"GRID"` durchgängig. `ChapterTile`-Signatur in T5 = Aufruf in T6.
- **Keine Platzhalter:** alle Schritte mit echtem Code/Befehl + erwartetem Ergebnis.
- **E-Ink:** Outlined-Icons, `EinkTokens.hairline`/`tileRadius`/`tileGap`/`screenPadding`, keine Animation, kein Reload beim Toggle.
```
