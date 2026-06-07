# Settings Master-Detail + Such-Highlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Einstellungen von Kachel-Landing + Drill-in-Seiten auf adaptives Master-Detail (Tablet/E-Ink) bzw. Accordion (Phone) umstellen, mit Such-Highlight bis zur einzelnen Einstellung.

**Architecture:** Eine Section-Registry (Daten + `@Composable content(query)`) ersetzt 6 NavHost-Seiten. Ein `SettingsScreen`-Host wählt per `BoxWithConstraints` zwischen Master-Detail und Accordion. Reine, getestete Such-Funktionen (`matchRanges`/`sectionMatches`) treiben Filter + Auto-Sprung; ein `HighlightText`-Composable markiert Treffer monochrom (fett + `outlineVariant`-Hintergrund).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt (`hiltViewModel`), JUnit (pure Unit-Tests). Modul `app`. Keine Domain-/Source-/Daten-Änderung.

---

## File Structure

- Create `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSearch.kt` — pure: `matchRanges`, `sectionMatches`.
- Create `app/src/test/kotlin/com/komgareader/app/ui/settings/SettingsSearchTest.kt` — Unit-Tests.
- Create `app/src/main/kotlin/com/komgareader/app/ui/components/HighlightText.kt` — `HighlightText`.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/components/EinkComponents.kt` — `ChoiceRow` bekommt optionalen `query`.
- Create `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt` — die 6 Content-Composables + `SettingsSectionId`.
- Create `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSections.kt` — `SettingsSection`, `buildSettingsSections`.
- Create `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsScreen.kt` — Host (`BoxWithConstraints`, Sizing, Sidebar, Master-Detail, Accordion).
- Modify `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt` — `SettingsScreen` statt Landing, live-query auf Settings-Tab, adaptive Such-Breite, `onOpenSettingsPage` raus.
- Modify `app/src/main/kotlin/com/komgareader/app/MainActivity.kt` — 6 Settings-Routen + `settingsRoute` + `onOpenSettingsPage` raus.
- Delete `SettingsLandingScreen.kt`, `ConnectionSettingsScreen.kt`, `AppearanceSettingsScreen.kt`, `ReaderSettingsScreen.kt`, `DownloadsSettingsScreen.kt`, `LanguageSettingsScreen.kt`, `AboutScreen.kt`.
- Modify `.claude/skills/komga-eink-ui/SKILL.md` — Abschnitt „Settings-Architektur".

---

## Task 1: Pure Such-Funktionen (matchRanges, sectionMatches)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSearch.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/settings/SettingsSearchTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    @Test fun `matchRanges returns empty when no occurrence`() {
        assertEquals(emptyList<IntRange>(), matchRanges("Hello world", "xyz"))
    }

    @Test fun `matchRanges finds single occurrence`() {
        assertEquals(listOf(6..10), matchRanges("Hello world", "world"))
    }

    @Test fun `matchRanges finds multiple occurrences`() {
        assertEquals(listOf(0..1, 5..6), matchRanges("abxxxab", "ab"))
    }

    @Test fun `matchRanges is case insensitive`() {
        assertEquals(listOf(0..3), matchRanges("Dunkel", "dunk"))
    }

    @Test fun `matchRanges blank query returns empty`() {
        assertEquals(emptyList<IntRange>(), matchRanges("Hello", ""))
        assertEquals(emptyList<IntRange>(), matchRanges("Hello", "   "))
    }

    @Test fun `matchRanges query longer than text returns empty`() {
        assertEquals(emptyList<IntRange>(), matchRanges("ab", "abc"))
    }

    @Test fun `sectionMatches true when a term contains query`() {
        assertTrue(sectionMatches(listOf("Darstellung", "Theme Hell Dunkel"), "dunkel"))
    }

    @Test fun `sectionMatches false when no term contains query`() {
        assertFalse(sectionMatches(listOf("Sprache", "Deutsch English"), "theme"))
    }

    @Test fun `sectionMatches blank query is false`() {
        assertFalse(sectionMatches(listOf("Anything"), "  "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.komgareader.app.ui.settings.SettingsSearchTest'`
Expected: FAIL — `matchRanges`/`sectionMatches` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.komgareader.app.ui.settings

/**
 * Alle Vorkommen von [query] in [text], case-insensitive, als inklusive IntRanges.
 * Leere/blanke [query] oder kein Treffer → leere Liste. Pure Funktion (testbar).
 */
fun matchRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty() || needle.length > text.length) return emptyList()
    val haystack = text.lowercase()
    val lowered = needle.lowercase()
    val ranges = mutableListOf<IntRange>()
    var from = haystack.indexOf(lowered)
    while (from >= 0) {
        ranges += from..(from + lowered.length - 1)
        from = haystack.indexOf(lowered, from + lowered.length)
    }
    return ranges
}

/** Sektion matcht, wenn irgendein Term die (getrimmte) [query] enthält. Blank → false. */
fun sectionMatches(searchTerms: List<String>, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return false
    return searchTerms.any { it.contains(needle, ignoreCase = true) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.komgareader.app.ui.settings.SettingsSearchTest'`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSearch.kt app/src/test/kotlin/com/komgareader/app/ui/settings/SettingsSearchTest.kt
git commit -m "feat(settings): pure matchRanges + sectionMatches mit Tests"
```

---

## Task 2: HighlightText-Composable + ChoiceRow-query

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/HighlightText.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/EinkComponents.kt` (`ChoiceRow`)

- [ ] **Step 1: Create HighlightText**

```kotlin
package com.komgareader.app.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.komgareader.app.ui.settings.matchRanges

/**
 * Text, der die Treffer von [query] markiert: fett + `outlineVariant`-Hintergrund —
 * monochrom, E-Ink-konform (keine Akzentfarbe). Ohne Treffer = normaler Text.
 */
@Composable
fun HighlightText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val ranges = matchRanges(text, query)
    if (ranges.isEmpty()) {
        Text(text, modifier = modifier, style = style, color = color)
        return
    }
    val markBg = MaterialTheme.colorScheme.outlineVariant
    val annotated: AnnotatedString = buildAnnotatedString {
        append(text)
        ranges.forEach { r ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = markBg), r.first, r.last + 1)
        }
    }
    Text(annotated, modifier = modifier, style = style, color = color)
}
```

- [ ] **Step 2: Add optional `query` to ChoiceRow**

In `EinkComponents.kt`, `ChoiceRow` so ändern, dass das Label highlightet wird. Ersetze die bestehende `ChoiceRow`-Funktion (aktuell Zeilen ~112-136) durch:

```kotlin
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    query: String = "",
    onSelect: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightText(
            text = label,
            query = query,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
```

(`HighlightText` ist im selben Paket `ui.components` → kein Import nötig.)

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Bestehende `ChoiceRow(label, selected) { … }`-Aufrufer bleiben gültig (neuer Param hat Default).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/HighlightText.kt app/src/main/kotlin/com/komgareader/app/ui/components/EinkComponents.kt
git commit -m "feat(settings): HighlightText + ChoiceRow query-Highlight"
```

---

## Task 3: Content-Composables (Extraktion aus den 6 Screens)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt`

Enthält `SettingsSectionId` und die 6 Content-Composables (Bodies der alten Screens ohne `SubPageScaffold`, Helper-/Section-Texte über `HighlightText`). Die alten Screen-Dateien werden erst in Task 7 gelöscht.

- [ ] **Step 1: Create SettingsContent.kt**

```kotlin
package com.komgareader.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.HighlightText
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.app.ui.theme.ThemeMode
import com.komgareader.domain.model.DisplayMode

/** Die einzelnen Settings-Sektionen. Reihenfolge = Sidebar-/Accordion-Reihenfolge. */
enum class SettingsSectionId { CONNECTION, APPEARANCE, READER, DOWNLOADS, LANGUAGE, ABOUT }

/** Schrittweite und Grenzen der Webtoon-Überlappung (in Prozent). */
private const val OVERLAP_STEP = 5
private const val OVERLAP_MIN = 0
private const val OVERLAP_MAX = 50

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConnectionSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val server by viewModel.server.collectAsState()

    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        val statusText = if (server != null) "${s.connected}: ${server!!.name}" else s.notConnected
        HighlightText(statusText, query, MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text(s.serverDisplayName) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text(s.serverUrl) },
            placeholder = { Text(s.serverUrlHint) },
            supportingText = { Text(s.serverUrlHelper) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text(s.serverApiKeyOptional) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        HorizontalDivider()
        Text(
            text = s.orSeparator,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        HorizontalDivider()

        val autofill = LocalAutofill.current
        val autofillTree = LocalAutofillTree.current

        val usernameNode = remember {
            AutofillNode(autofillTypes = listOf(AutofillType.Username), onFill = { usernameInput = it })
        }
        autofillTree += usernameNode
        OutlinedTextField(
            value = usernameInput,
            onValueChange = { usernameInput = it },
            label = { Text(s.serverUsername) },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { usernameNode.boundingBox = it.boundsInWindow() }
                .onFocusChanged { focus ->
                    autofill?.run {
                        if (focus.isFocused) requestAutofillForNode(usernameNode)
                        else cancelAutofillForNode(usernameNode)
                    }
                },
            singleLine = true,
        )

        val passwordNode = remember {
            AutofillNode(autofillTypes = listOf(AutofillType.Password), onFill = { passwordInput = it })
        }
        autofillTree += passwordNode
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            label = { Text(s.serverPassword) },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { passwordNode.boundingBox = it.boundsInWindow() }
                .onFocusChanged { focus ->
                    autofill?.run {
                        if (focus.isFocused) requestAutofillForNode(passwordNode)
                        else cancelAutofillForNode(passwordNode)
                    }
                },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row {
            Button(onClick = {
                viewModel.saveServer(nameInput, urlInput, apiKeyInput, usernameInput, passwordInput)
                nameInput = ""; urlInput = ""; apiKeyInput = ""; usernameInput = ""; passwordInput = ""
            }) { Text(s.connect) }
            if (server != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { viewModel.disconnect() }) { Text(s.disconnect) }
            }
        }
    }
}

@Composable
fun AppearanceSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val themeModeStr by viewModel.themeMode.collectAsState()
    val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)

    Column {
        SectionHeader(s.settingsTheme)
        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.LIGHT -> s.themeLight
                ThemeMode.DARK -> s.themeDark
                ThemeMode.SYSTEM -> s.themeSystem
            }
            ChoiceRow(label, selected = mode == themeMode, query = query) { viewModel.setTheme(mode.name) }
        }
    }
}

@Composable
fun ReaderSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val displayModeStr by viewModel.displayMode.collectAsState()
    val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
    val overlap by viewModel.webtoonOverlapPercent.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        Column {
            SectionHeader(s.settingsWebtoon)
            HighlightText(
                s.webtoonOverlapHelper, query, MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepperRow(
                label = s.webtoonOverlap,
                value = overlap,
                canDecrement = overlap > OVERLAP_MIN,
                canIncrement = overlap < OVERLAP_MAX,
                onDecrement = { viewModel.setWebtoonOverlap(overlap - OVERLAP_STEP) },
                onIncrement = { viewModel.setWebtoonOverlap(overlap + OVERLAP_STEP) },
                display = { "$it %" },
            )
        }
        Column {
            SectionHeader(s.settingsDisplayMode)
            HighlightText(
                s.displayModeHelper, query, MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DisplayMode.entries.forEach { dm ->
                val label = when (dm) {
                    DisplayMode.EINK -> s.displayEink
                    DisplayMode.SMARTPHONE -> s.displaySmartphone
                }
                ChoiceRow(label, selected = dm == displayMode, query = query) { viewModel.setDisplayMode(dm.name) }
            }
        }
    }
}

@Composable
fun DownloadsSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val downloadDir by viewModel.downloadDir.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setDownloadDir(uri.toString())
        }
    }

    Column {
        SectionHeader(s.downloadFolder)
        val folderLabel = downloadDir?.let { dir ->
            runCatching { Uri.parse(dir).lastPathSegment ?: dir }.getOrElse { dir }
        } ?: s.defaultFolder
        HighlightText(
            folderLabel, query, MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            Button(onClick = { folderPicker.launch(null) }) { Text(s.chooseFolder) }
            if (downloadDir != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { viewModel.setDownloadDir(null) }) { Text(s.resetFolder) }
            }
        }
    }
}

@Composable
fun LanguageSettingsContent(viewModel: SettingsViewModel, query: String) {
    val languageStr by viewModel.language.collectAsState()
    Column {
        Language.entries.forEach { lang ->
            val label = when (lang) {
                Language.DE -> "Deutsch"
                Language.EN -> "English"
            }
            ChoiceRow(label, selected = lang.code == languageStr, query = query) { viewModel.setLanguage(lang.code) }
        }
    }
}

@Composable
fun AboutContent(query: String) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        Column {
            HighlightText(s.appName, query, MaterialTheme.typography.titleLarge)
            HighlightText(
                s.aboutDevice, query, MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${s.versionLabel}: ", style = MaterialTheme.typography.bodyMedium)
            HighlightText(BuildConfig.VERSION_NAME, query, MaterialTheme.typography.bodyMedium)
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (alte Screens existieren noch, kompilieren weiter).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt
git commit -m "feat(settings): Content-Composables aus Screens extrahiert"
```

---

## Task 4: Section-Registry (SettingsSection, buildSettingsSections)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSections.kt`

- [ ] **Step 1: Create SettingsSections.kt**

```kotlin
package com.komgareader.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.Strings

/** Eine Settings-Sektion: Metadaten + such-Terme + ihr Inhalt. */
data class SettingsSection(
    val id: SettingsSectionId,
    val icon: ImageVector,
    val title: String,
    val searchTerms: List<String>,
    val content: @Composable (query: String) -> Unit,
)

/**
 * Baut die Sektionsliste (Reihenfolge = UI-Reihenfolge). [searchTerms] sind lokalisierte
 * Strings (Titel + Zeilen-Labels + Helper) — Grundlage für Filter + „warum gefunden".
 */
@Composable
fun buildSettingsSections(s: Strings, viewModel: SettingsViewModel): List<SettingsSection> = listOf(
    SettingsSection(
        id = SettingsSectionId.CONNECTION,
        icon = Icons.Outlined.Cloud,
        title = s.settingsConnection,
        searchTerms = listOf(
            s.settingsConnection, s.settingsServer, s.serverDisplayName, s.serverUrl, s.serverUrlHelper,
            s.serverApiKeyOptional, s.serverUsername, s.serverPassword, s.connect, s.disconnect,
        ),
        content = { q -> ConnectionSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.APPEARANCE,
        icon = Icons.Outlined.Contrast,
        title = s.settingsAppearance,
        searchTerms = listOf(s.settingsAppearance, s.settingsTheme, s.themeLight, s.themeDark, s.themeSystem),
        content = { q -> AppearanceSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.READER,
        icon = Icons.Outlined.ChromeReaderMode,
        title = s.settingsReader,
        searchTerms = listOf(
            s.settingsReader, s.settingsWebtoon, s.webtoonOverlap, s.webtoonOverlapHelper,
            s.settingsDisplayMode, s.displayModeHelper, s.displayEink, s.displaySmartphone,
        ),
        content = { q -> ReaderSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.DOWNLOADS,
        icon = Icons.Outlined.Download,
        title = s.settingsDownloads,
        searchTerms = listOf(s.settingsDownloads, s.downloadFolder, s.chooseFolder, s.resetFolder, s.defaultFolder),
        content = { q -> DownloadsSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.LANGUAGE,
        icon = Icons.Outlined.Language,
        title = s.settingsLanguage,
        searchTerms = listOf(s.settingsLanguage, "Deutsch", "English"),
        content = { q -> LanguageSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.ABOUT,
        icon = Icons.Outlined.Info,
        title = s.settingsAbout,
        searchTerms = listOf(s.settingsAbout, s.appName, s.aboutDevice, s.versionLabel, BuildConfig.VERSION_NAME),
        content = { q -> AboutContent(q) },
    ),
)
```

- [ ] **Step 2: Verify Strings-Keys existieren**

Run: `grep -E 'val (settingsServer|serverDisplayName|resetFolder|settingsTheme) ' app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`
Expected: alle vier Keys gefunden. Falls ein Key im Listing nicht existiert, in `Strings.kt` nachsehen und den realen Namen verwenden (keine neuen Keys anlegen). Bekannte vorhandene Keys laut Ist-Code: `settingsConnection, settingsServer, serverUrl, serverUrlHelper, serverApiKeyOptional, serverUsername, serverPassword, connect, disconnect, settingsAppearance, settingsTheme, themeLight, themeDark, themeSystem, settingsReader, settingsWebtoon, webtoonOverlap, webtoonOverlapHelper, settingsDisplayMode, displayModeHelper, displayEink, displaySmartphone, settingsDownloads, downloadFolder, chooseFolder, resetFolder, defaultFolder, settingsLanguage, settingsAbout, appName, aboutDevice, versionLabel, serverDisplayName`.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSections.kt
git commit -m "feat(settings): Section-Registry mit such-Termen"
```

---

## Task 5: SettingsScreen-Host (Sizing, Sidebar, Master-Detail, Accordion)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create SettingsScreen.kt**

```kotlin
package com.komgareader.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.HighlightText
import com.komgareader.app.ui.theme.EinkTokens

/** Größenklasse abhängig von der verfügbaren Breite — keine Magic-dp verstreut. */
private data class SettingsSizing(
    val sidebarWidth: Dp,
    val iconSize: Dp,
    val labelSize: TextUnit,
    val contentPadding: Dp,
)

private val SizingMedium = SettingsSizing(220.dp, 28.dp, 16.sp, EinkTokens.screenPadding)
private val SizingExpanded = SettingsSizing(280.dp, 32.dp, 20.sp, 24.dp)

/**
 * Adaptiver Settings-Host. < 600 dp → Accordion (Phone), sonst Master-Detail
 * (Tablet/E-Ink), ab 900 dp größer. [query] (live) filtert auf Treffer-Sektionen,
 * springt automatisch zur ersten und markiert Treffer (siehe HighlightText).
 */
@Composable
fun SettingsScreen(
    query: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val sections = buildSettingsSections(s, viewModel)
    val visible = if (query.isBlank()) sections else sections.filter { sectionMatches(it.searchTerms, query) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.searchNoResults, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
            return@BoxWithConstraints
        }
        if (maxWidth < 600.dp) {
            SettingsAccordion(visible, query, SizingMedium)
        } else {
            val sizing = if (maxWidth >= 900.dp) SizingExpanded else SizingMedium
            SettingsMasterDetail(visible, query, sizing)
        }
    }
}

@Composable
private fun SettingsMasterDetail(visible: List<SettingsSection>, query: String, sizing: SettingsSizing) {
    var selectedId by rememberSaveable { mutableStateOf(visible.first().id) }
    // Auto-Sprung: wenn die Auswahl ausgefiltert ist, auf die erste sichtbare wechseln.
    LaunchedEffect(visible) {
        if (visible.none { it.id == selectedId }) selectedId = visible.first().id
    }
    val selected = visible.firstOrNull { it.id == selectedId } ?: visible.first()

    Row(Modifier.fillMaxSize()) {
        SettingsSidebar(
            visible = visible,
            selectedId = selectedId,
            query = query,
            sizing = sizing,
            onSelect = { selectedId = it },
            modifier = Modifier.width(sizing.sidebarWidth).fillMaxHeight(),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(sizing.contentPadding),
        ) {
            selected.content(query)
        }
    }
}

@Composable
private fun SettingsSidebar(
    visible: List<SettingsSection>,
    selectedId: SettingsSectionId,
    query: String,
    sizing: SettingsSizing,
    onSelect: (SettingsSectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outlineVariant)
            .verticalScroll(rememberScrollState()),
    ) {
        visible.forEach { section ->
            val active = section.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(section.id) }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Aktiv: schwarzer Akzent-Balken links (E-Ink-Bottom-Bar-Sprache, vertikal).
                Box(
                    Modifier
                        .width(3.dp)
                        .size(width = 3.dp, height = sizing.iconSize)
                        .background(if (active) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    section.icon,
                    contentDescription = null,
                    modifier = Modifier.size(sizing.iconSize),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                HighlightText(
                    text = section.title,
                    query = query,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = sizing.labelSize,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingsAccordion(visible: List<SettingsSection>, query: String, sizing: SettingsSizing) {
    // Bei aktiver Suche alle Treffer-Sektionen aufgeklappt; sonst nur die manuell geöffnete (null = alle zu).
    var openId by rememberSaveable { mutableStateOf<SettingsSectionId?>(visible.first().id) }
    val searching = query.isNotBlank()

    LazyColumn(
        Modifier.fillMaxSize().padding(EinkTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
    ) {
        items(visible, key = { it.id }) { section ->
            val expanded = searching || section.id == openId
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(
                        EinkTokens.hairline,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(EinkTokens.tileRadius),
                    ),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !searching) { openId = if (openId == section.id) null else section.id }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        section.icon,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.iconSize),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(12.dp))
                    HighlightText(
                        text = section.title,
                        query = query,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = sizing.labelSize),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        section.content(query)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): adaptiver Master-Detail/Accordion-Host"
```

---

## Task 6: HomeScreen-Integration (live-query, adaptive Suchbreite)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt`

- [ ] **Step 1: Settings-Tab auf SettingsScreen + live-query umstellen**

In `HomeScreen.kt`:

a) Import ersetzen: `import com.komgareader.app.ui.settings.SettingsLandingScreen` → `import com.komgareader.app.ui.settings.SettingsScreen`. Den jetzt unbenutzten `import com.komgareader.app.ui.settings.SettingsPage` entfernen.

b) Parameter `onOpenSettingsPage: (SettingsPage) -> Unit` aus der `HomeScreen`-Signatur entfernen.

c) Im `when (selected)`-Block (aktuell Zeile ~135) die `else`-Zeile ersetzen:

```kotlin
                else -> SettingsScreen(query = if (onSettingsTab) query else submitted)
```

(Auf dem Settings-Tab live `query` → Highlight beim Tippen; `submitted` bleibt für Medien.)

- [ ] **Step 2: Adaptive Suchbreite**

Die feste `Modifier.width(360.dp)` der `EinkSearchBar` (Zeile ~97) durch eine gedeckelte Breite ersetzen, damit sie auf schmalen Screens nicht überläuft:

```kotlin
                            modifier = Modifier.fillMaxWidth(0.6f).widthIn(max = 360.dp),
```

Import ergänzen: `import androidx.compose.foundation.layout.widthIn`.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `MainActivity` ruft `HomeScreen(onOpenSettingsPage = …)` noch auf. Das wird in Task 7 behoben. (Wenn allein gebaut: erwartbarer Fehler nur in MainActivity.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt
git commit -m "feat(settings): HomeScreen nutzt SettingsScreen + live-Suche"
```

---

## Task 7: MainActivity aufräumen + alte Screens löschen

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`
- Delete: `SettingsLandingScreen.kt`, `ConnectionSettingsScreen.kt`, `AppearanceSettingsScreen.kt`, `ReaderSettingsScreen.kt`, `DownloadsSettingsScreen.kt`, `LanguageSettingsScreen.kt`, `AboutScreen.kt`

- [ ] **Step 1: HomeScreen-Aufruf entschlacken**

In `MainActivity.kt`, `composable("home")` (Zeile ~96-102) den `onOpenSettingsPage`-Parameter entfernen:

```kotlin
                        composable("home") {
                            HomeScreen(
                                onOpenSeries = { seriesId -> nav.navigate("series/$seriesId") },
                                onOpenGroup = { shelfId, _ -> nav.navigate("group/$shelfId") },
                            )
                        }
```

- [ ] **Step 2: 6 Settings-Routen + settingsRoute entfernen**

Lösche die sechs `composable("settings/...")`-Blöcke (Zeilen ~103-120) und die `settingsRoute(page)`-Funktion (Zeilen ~189-197) am Dateiende komplett.

- [ ] **Step 3: Tote Imports entfernen**

Diese Imports aus `MainActivity.kt` entfernen:

```
com.komgareader.app.ui.settings.AboutScreen
com.komgareader.app.ui.settings.AppearanceSettingsScreen
com.komgareader.app.ui.settings.ConnectionSettingsScreen
com.komgareader.app.ui.settings.DownloadsSettingsScreen
com.komgareader.app.ui.settings.LanguageSettingsScreen
com.komgareader.app.ui.settings.ReaderSettingsScreen
com.komgareader.app.ui.settings.SettingsPage
```

(`com.komgareader.app.ui.settings.SettingsViewModel` BLEIBT — wird in `onCreate` genutzt.)

- [ ] **Step 4: Alte Screen-Dateien löschen**

```bash
git rm app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsLandingScreen.kt \
       app/src/main/kotlin/com/komgareader/app/ui/settings/ConnectionSettingsScreen.kt \
       app/src/main/kotlin/com/komgareader/app/ui/settings/AppearanceSettingsScreen.kt \
       app/src/main/kotlin/com/komgareader/app/ui/settings/ReaderSettingsScreen.kt \
       app/src/main/kotlin/com/komgareader/app/ui/settings/DownloadsSettingsScreen.kt \
       app/src/main/kotlin/com/komgareader/app/ui/settings/LanguageSettingsScreen.kt \
       app/src/main/kotlin/com/komgareader/app/ui/settings/AboutScreen.kt
```

- [ ] **Step 5: Full build + Unit-Tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Tests grün. Falls ein „unused import" o. ä. den Build über `allWarningsAsErrors` bricht, den genannten Import entfernen.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(settings): NavHost-Settings-Routen + alte Screens entfernt"
```

---

## Task 8: eink-ui-Skill nachziehen + E2E-Verifikation

**Files:**
- Modify: `.claude/skills/komga-eink-ui/SKILL.md`

- [ ] **Step 1: Skill-Abschnitt „Settings-Architektur" aktualisieren**

In `.claude/skills/komga-eink-ui/SKILL.md` den Abschnitt „## Settings-Architektur" ersetzen durch eine Beschreibung des neuen Musters:

```markdown
## Settings-Architektur

**Adaptives Master-Detail.** Eine Section-Registry (`SettingsSection`: id, Icon, Titel,
`searchTerms`, `content(query)`) treibt drei Layouts über `BoxWithConstraints`:

- **< 600 dp (Phone):** Accordion — eine Scroll-Liste, Sektionskopf tappbar, Inhalt klappt
  inline auf.
- **600–900 dp (Tablet):** Sidebar links (Icon 28 dp + `titleMedium`, aktiver schwarzer
  Akzent-Balken links) + Detail rechts.
- **≥ 900 dp (großes E-Ink):** wie Tablet, größer (Icon 32 dp, größeres Label, mehr Padding).

Keine eigenen NavHost-Seiten pro Sektion mehr. Sektionen:
Verbindung · Darstellung · Reader · Downloads · Sprache · Über.

**Such-Highlight.** Die TopBar-Suche reicht live `query` an `SettingsScreen`. Reine
Funktionen `matchRanges`/`sectionMatches` filtern auf Treffer-Sektionen, springen zur
ersten und `HighlightText` markiert den gematchten Text (fett + `outlineVariant`-Hintergrund,
monochrom) bis in `ChoiceRow`-Labels — der Nutzer sieht, *warum* etwas gefunden wurde.
```

- [ ] **Step 2: Setup-Skript (Skill-Sync) ausführen**

Run: `cd ~/Work/claude_settings 2>/dev/null && ./setup.sh; cd - >/dev/null` — falls der eink-ui-Skill ein Symlink aus dem claude_settings-Repo ist. Andernfalls (projekt-lokaler Skill) entfällt dieser Schritt. Prüfe vorher: `ls -l .claude/skills/komga-eink-ui/SKILL.md` (Symlink? → setup nötig).

- [ ] **Step 3: Commit Skill-Update**

```bash
git add .claude/skills/komga-eink-ui/SKILL.md
git commit -m "docs(eink-ui): Settings-Architektur auf Master-Detail aktualisiert"
```

- [ ] **Step 4: E2E auf Emulator `eink_test` (Master-Detail)**

App auf dem `eink_test`-AVD (1264×1680) installieren/starten, zum Settings-Tab. Erwartung:
Sidebar links + Detail rechts, Sektionswechsel ohne Seitenwechsel. Screenshot ablegen unter
`docs/superpowers/plans/artifacts/settings-masterdetail.png`.

Run (Beispiel): `./gradlew :app:installDebug && adb shell am start -n com.komgareader.app/.MainActivity && sleep 3 && adb exec-out screencap -p > docs/superpowers/plans/artifacts/settings-masterdetail.png`

- [ ] **Step 5: E2E Accordion (schmal) + Such-Highlight**

Schmales Fenster/AVD (oder `adb shell wm size 480x800` temporär): Settings zeigt Accordion,
Kopf auf-/zuklappen. Dann in der Suche „dunkel" tippen → nur Treffer-Sektion(en), Auto-Sprung,
sichtbarer Marker auf dem „Dunkel"-Label. Screenshot
`docs/superpowers/plans/artifacts/settings-search-highlight.png`. Danach `adb shell wm size reset`.

- [ ] **Step 6: Abschluss-Commit der Artefakte**

```bash
git add docs/superpowers/plans/artifacts/
git commit -m "test(settings): E2E-Screenshots Master-Detail + Such-Highlight"
```

---

## Self-Review-Notiz (Autor)

- **Spec-Coverage:** Master-Detail (T5), Accordion (T5), Sidebar größere Icons/Text (T5 Sizing),
  responsive Breakpoints (T5), Filter+Auto-Sprung (T5/T1), Highlight bis ChoiceRow (T2/T3),
  live-Suche + adaptive Suchbreite (T6), Registry statt Routen (T4/T7), Skill-Nachzug (T8),
  Tests pure (T1) + E2E (T8). Keine offene Spec-Anforderung.
- **Typkonsistenz:** `SettingsSectionId` (T3) durchgängig; `sectionMatches(List<String>, String)` (T1)
  passt zu `sectionMatches(it.searchTerms, query)` (T5); `HighlightText(text,query,style,modifier,color)`
  (T2) konsistent verwendet (T3/T5); `content: @Composable (query)->Unit` (T4) konsistent aufgerufen.
- **Hinweis Task 5:** nullable `openId`-Toggle ist die verbindliche Variante (Listing-No-Op ersetzen).
