# Hyphenation Language Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Automatic the default novel-hyphenation mode, keep Off as an option, and add a language modal listing every hyphenation pattern the app ships — backed by bundling crengine-ng's full pattern set (~24 languages instead of de/en).

**Architecture:** A domain-owned canonical language-code list (`HyphenationLanguages.SUPPORTED`) is the single source of truth; the render layer maps each code to its `.pattern` filename (`ReflowCss.PATTERN_DICTS`) and derives the asset-extraction list from it, with a unit test guarding parity. The UI replaces the de/en chips with a shared `HyphenationPicker` (Automatic / Off chips + a Language button) opening a `HyphenationLanguageModal` (`EinkInfoDialog`), used in both the settings screen and the in-reader typography panel.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, crengine-ng (`HyphMan` pattern dictionaries, bundled assets), `java.util.Locale` for localized language names, JUnit (Jupiter in app, kotlin.test in domain/render).

**Spec:** `docs/superpowers/specs/2026-06-15-hyphenation-language-selection-design.md`

---

## Coordination & isolation (read first)

A parallel session has **uncommitted, currently-non-building** work-in-progress on the crengine word-bookmark capability (`render-crengine/src/main/cpp/cr3_bridge.cpp` with duplicate JNI symbols, `CrengineDocument.kt`/`CrengineNative.kt` overriding domain methods that don't exist yet). This plan touches **none** of those files. To build cleanly, execute this plan in a **separate git worktree** created from the `feat/hyphenation-language-selection` branch (which is clean — only the spec commit), so the parallel session's uncommitted changes in the main working tree do not contaminate the build. Do not stash, commit, discard, or edit the parallel session's files.

The canonical language set for this plan (24 base codes → pattern file; combo/romanization/variant files skipped):

| code | file | | code | file |
|---|---|---|---|---|
| ar | hyph-ar.pattern | | hu | hyph-hu.pattern |
| bg | hyph-bg.pattern | | it | hyph-it.pattern |
| bn | hyph-bn.pattern | | mr | hyph-mr.pattern |
| cs | hyph-cs.pattern | | nl | hyph-nl.pattern |
| da | hyph-da.pattern | | pa | hyph-pa.pattern |
| de | hyph-de-1996.pattern | | pl | hyph-pl.pattern |
| el | hyph-el-monoton.pattern | | pt | hyph-pt.pattern |
| en | hyph-en-us.pattern | | ru | hyph-ru-ru.pattern |
| es | hyph-es.pattern | | ta | hyph-ta.pattern |
| fa | hyph-fa.pattern | | te | hyph-te.pattern |
| fi | hyph-fi.pattern | | uk | hyph-uk.pattern |
| fr | hyph-fr.pattern | | gu | hyph-gu.pattern |

Source files live at `render-crengine/native/prefix/aarch64-linux-android/share/crengine-ng/hyph/`. Skip: `hyph-en-gb.pattern`, `hyph-grc.pattern`, `hyph-ru-ru,en-us.pattern`, `hyph-zh-latn-pinyin.pattern`.

---

## Task 1: Default hyphenation = "auto"

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt` (the `novelHyphenationLang` getter)
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing test** — add to the existing test class (mirror its harness):

```kotlin
@Test fun `novel hyphenation defaults to auto when unset`() = runTest {
    assertEquals("auto", repo.novelHyphenationLang.first())
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomSettingsRepositoryTest"`
Expected: FAIL — returns "" not "auto".

- [ ] **Step 3: Change the default.** The current getter is:

```kotlin
    override val novelHyphenationLang: Flow<String> =
        dao.observe(KEY_NOVEL_HYPHENATION).map { it ?: "" }
```

Change to:

```kotlin
    override val novelHyphenationLang: Flow<String> =
        dao.observe(KEY_NOVEL_HYPHENATION).map { it ?: "auto" }
```

(No migration — free-form key; absence now resolves to "auto".)

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomSettingsRepositoryTest"`
Expected: PASS (and existing hyphenation round-trip tests still pass — an explicitly set "" still reads back "").

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt
git commit -m "feat(settings): default novel hyphenation to auto"
```

---

## Task 2: Domain language list + mode helper

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/render/HyphenationLanguages.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/render/HyphenationResolver.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/render/HyphenationResolverTest.kt` (extend)
- Test: `domain/src/test/kotlin/com/komgareader/domain/render/HyphenationModeTest.kt` (create)

- [ ] **Step 1: Write the new/extended failing tests.**

Extend `HyphenationResolverTest.kt` (kotlin.test, like the existing file):

```kotlin
@Test fun `auto resolves any supported bundled language`() {
    assertEquals("it", resolveHyphenationLang("auto", "it"))
    assertEquals("fr", resolveHyphenationLang("auto", "fr-FR"))
    assertEquals("ru", resolveHyphenationLang("auto", "ru-RU"))
}

@Test fun `auto with a bundled-but-unsupported language is off`() {
    assertEquals("", resolveHyphenationLang("auto", "ja")) // Japanese not bundled
}
```

Create `HyphenationModeTest.kt`:

```kotlin
package com.komgareader.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals

class HyphenationModeTest {
    @Test fun `auto value is AUTO mode`() = assertEquals(HyphenationMode.AUTO, hyphenationModeOf("auto"))
    @Test fun `empty value is OFF mode`() = assertEquals(HyphenationMode.OFF, hyphenationModeOf(""))
    @Test fun `a language code is LANGUAGE mode`() = assertEquals(HyphenationMode.LANGUAGE, hyphenationModeOf("it"))

    @Test fun `supported list contains the bundled languages`() {
        assertEquals(24, HyphenationLanguages.SUPPORTED.size)
        kotlin.test.assertTrue("de" in HyphenationLanguages.SUPPORTED)
        kotlin.test.assertTrue("it" in HyphenationLanguages.SUPPORTED)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :domain:testDebugUnitTest --tests "com.komgareader.domain.render.*"`
Expected: FAIL — `HyphenationLanguages`, `HyphenationMode`, `hyphenationModeOf` unresolved.

- [ ] **Step 3: Create `HyphenationLanguages.kt`:**

```kotlin
package com.komgareader.domain.render

/**
 * Canonical set of hyphenation language codes the app supports — the single source of truth
 * shared by [resolveHyphenationLang] (domain) and the render layer's pattern-file map
 * (ReflowCss.PATTERN_DICTS, keyed by exactly these codes; a render parity test guards the match).
 * Each code has a bundled crengine-ng `.pattern` dictionary. Base BCP-47 codes only.
 */
object HyphenationLanguages {
    val SUPPORTED: List<String> = listOf(
        "ar", "bg", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fi", "fr",
        "gu", "hu", "it", "mr", "nl", "pa", "pl", "pt", "ru", "ta", "te", "uk",
    )
}

/** Which of the three hyphenation UI states a stored setting value represents. */
enum class HyphenationMode { AUTO, OFF, LANGUAGE }

/** Pure mapping of the stored setting value to its UI mode. */
fun hyphenationModeOf(value: String): HyphenationMode = when (value) {
    HYPHENATION_AUTO -> HyphenationMode.AUTO
    "" -> HyphenationMode.OFF
    else -> HyphenationMode.LANGUAGE
}
```

- [ ] **Step 4: Point the resolver at the list.** In `HyphenationResolver.kt`, replace the hard-coded set:

```kotlin
private val SUPPORTED_HYPHENATION = setOf("de", "en")
```

with a reference to the canonical list:

```kotlin
private val SUPPORTED_HYPHENATION = HyphenationLanguages.SUPPORTED.toSet()
```

(Leave `HYPHENATION_AUTO` and the `resolveHyphenationLang` body unchanged — it already normalizes `docLanguage.substringBefore('-').lowercase()` and checks membership.)

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew :domain:testDebugUnitTest --tests "com.komgareader.domain.render.*"`
Expected: PASS (new + existing resolver tests green).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/render/HyphenationLanguages.kt domain/src/main/kotlin/com/komgareader/domain/render/HyphenationResolver.kt domain/src/test/kotlin/com/komgareader/domain/render/HyphenationResolverTest.kt domain/src/test/kotlin/com/komgareader/domain/render/HyphenationModeTest.kt
git commit -m "feat(render): domain hyphenation language list + mode helper"
```

---

## Task 3: Bundle patterns + render map + parity test

**Files:**
- Create (copy): `app/src/main/assets/hyph/<file>.pattern` for the 22 new languages (de + en already present)
- Modify: `render-crengine/src/main/kotlin/com/komgareader/render/crengine/ReflowCss.kt` (`PATTERN_DICTS`, make it `internal`)
- Modify: `render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineDocumentFactory.kt` (`HYPH_PATTERNS` derive from `PATTERN_DICTS`)
- Test: `render-crengine/src/test/kotlin/com/komgareader/render/crengine/HyphenationParityTest.kt` (create)

- [ ] **Step 1: Copy the pattern assets.** Run (one command, copies the 22 new single-language files; de/en already exist):

```bash
SRC=render-crengine/native/prefix/aarch64-linux-android/share/crengine-ng/hyph
DST=app/src/main/assets/hyph
for f in hyph-ar hyph-bg hyph-bn hyph-cs hyph-da hyph-el-monoton hyph-es hyph-fa hyph-fi hyph-fr hyph-gu hyph-hu hyph-it hyph-mr hyph-nl hyph-pa hyph-pl hyph-pt hyph-ru-ru hyph-ta hyph-te hyph-uk; do cp "$SRC/$f.pattern" "$DST/$f.pattern"; done
ls "$DST" | wc -l   # expect 24
```

Expected: 24 files in `app/src/main/assets/hyph/`.

- [ ] **Step 2: Write the failing parity test** `HyphenationParityTest.kt` (render module uses kotlin.test — confirm via a neighboring render test like `ReflowCssTest.kt`):

```kotlin
package com.komgareader.render.crengine

import com.komgareader.domain.render.HyphenationLanguages
import kotlin.test.Test
import kotlin.test.assertEquals

class HyphenationParityTest {
    @Test fun `pattern dict codes match the domain supported list`() {
        assertEquals(HyphenationLanguages.SUPPORTED.toSet(), ReflowCss.PATTERN_DICTS.keys)
    }

    @Test fun `extraction list equals the pattern files`() {
        assertEquals(ReflowCss.PATTERN_DICTS.values.toSet(), CrengineDocumentFactory.hyphPatternFiles().toSet())
    }
}
```

Run: `./gradlew :render-crengine:testDebugUnitTest --tests "com.komgareader.render.crengine.HyphenationParityTest"`
Expected: FAIL — `PATTERN_DICTS` is private / `hyphPatternFiles` does not exist.

- [ ] **Step 3: Extend `PATTERN_DICTS` and make it accessible.** In `ReflowCss.kt`, change the declaration from `private val PATTERN_DICTS` to `internal val PATTERN_DICTS` and replace its contents with the full map:

```kotlin
    internal val PATTERN_DICTS: Map<String, String> = mapOf(
        "ar" to "hyph-ar.pattern",
        "bg" to "hyph-bg.pattern",
        "bn" to "hyph-bn.pattern",
        "cs" to "hyph-cs.pattern",
        "da" to "hyph-da.pattern",
        "de" to "hyph-de-1996.pattern",
        "el" to "hyph-el-monoton.pattern",
        "en" to "hyph-en-us.pattern",
        "es" to "hyph-es.pattern",
        "fa" to "hyph-fa.pattern",
        "fi" to "hyph-fi.pattern",
        "fr" to "hyph-fr.pattern",
        "gu" to "hyph-gu.pattern",
        "hu" to "hyph-hu.pattern",
        "it" to "hyph-it.pattern",
        "mr" to "hyph-mr.pattern",
        "nl" to "hyph-nl.pattern",
        "pa" to "hyph-pa.pattern",
        "pl" to "hyph-pl.pattern",
        "pt" to "hyph-pt.pattern",
        "ru" to "hyph-ru-ru.pattern",
        "ta" to "hyph-ta.pattern",
        "te" to "hyph-te.pattern",
        "uk" to "hyph-uk.pattern",
    )
```

- [ ] **Step 4: Derive the extraction list.** In `CrengineDocumentFactory.kt`, replace the hard-coded `HYPH_PATTERNS` companion val:

```kotlin
        /** Gebündelte Silbentrennungs-Muster (Assets unter `hyph/`). */
        val HYPH_PATTERNS = listOf("hyph-de-1996.pattern", "hyph-en-us.pattern")
```

with a derivation from the single map plus a test accessor:

```kotlin
        /** Bundled hyphenation pattern files (assets under `hyph/`) — derived from the one map. */
        val HYPH_PATTERNS = ReflowCss.PATTERN_DICTS.values.toList()

        /** Test accessor for the parity guard. */
        fun hyphPatternFiles(): List<String> = HYPH_PATTERNS
```

(`HYPH_PATTERNS` is already used in `extractHyphenationPatterns()` — that loop now extracts all 24.)

- [ ] **Step 5: Run the parity test**

Run: `./gradlew :render-crengine:testDebugUnitTest --tests "com.komgareader.render.crengine.HyphenationParityTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/assets/hyph/ render-crengine/src/main/kotlin/com/komgareader/render/crengine/ReflowCss.kt render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineDocumentFactory.kt render-crengine/src/test/kotlin/com/komgareader/render/crengine/HyphenationParityTest.kt
git commit -m "feat(render): bundle full crengine hyphenation pattern set"
```

---

## Task 4: Shared `HyphenationPicker` + language modal + i18n

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/HyphenationPicker.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` (+ `StringsDe`, `StringsEn`)
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt`

- [ ] **Step 1: Add i18n keys.** In `Strings.kt` interface, next to the other `novelHyphenation*`:

```kotlin
    val novelHyphenationLanguage: String
    val hyphenationLanguageTitle: String
```

`StringsDe`:

```kotlin
    override val novelHyphenationLanguage = "Sprache"
    override val hyphenationLanguageTitle = "Trennsprache"
```

`StringsEn`:

```kotlin
    override val novelHyphenationLanguage = "Language"
    override val hyphenationLanguageTitle = "Hyphenation language"
```

`MapBackedStrings.kt` — add both keys following the existing map-backed override pattern in that file (look at how `novelHyphenationAuto` was added and mirror it for both new keys).

- [ ] **Step 2: Find the existing close/cancel string.** Grep for an existing dismiss label to reuse in the modal's `closeLabel`:

Run: `grep -rn "val close\|val cancel\|= \"Schließen\"\|= \"Close\"\|= \"Abbrechen\"" app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`
Use the existing one (e.g. `strings.close` or `strings.cancel`). If none exists, add `close` ("Schließen"/"Close") the same way as Step 1. Note which you used.

- [ ] **Step 3: Create `HyphenationPicker.kt`:**

```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.SegmentOption
import com.komgareader.app.ui.components.SegmentedChoiceRow
import com.komgareader.domain.render.HyphenationLanguages
import com.komgareader.domain.render.HyphenationMode
import com.komgareader.domain.render.hyphenationModeOf
import java.util.Locale

private const val LANGUAGE_KEY = "__language__"

/** Localized, capitalized display name for a hyphenation language code (e.g. "it" -> "Italienisch"). */
internal fun hyphenationLanguageName(code: String): String =
    Locale(code).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

/**
 * Shared hyphenation control: Automatic / Off chips plus a Language chip that opens a modal of all
 * supported languages. Used in both the settings screen and the in-reader typography panel
 * (shared-structure-before-variants). [value] is the stored setting ("auto" | "" | a language code).
 */
@Composable
fun HyphenationPicker(value: String, onValue: (String) -> Unit, query: String = "") {
    val strings = LocalStrings.current
    var modalOpen by remember { mutableStateOf(false) }
    val mode = hyphenationModeOf(value)
    val languageLabel =
        if (mode == HyphenationMode.LANGUAGE) hyphenationLanguageName(value) else strings.novelHyphenationLanguage

    SegmentedChoiceRow(
        label = strings.novelHyphenation,
        options = listOf(
            SegmentOption("auto", strings.novelHyphenationAuto),
            SegmentOption("", strings.novelHyphenationOff),
            SegmentOption(LANGUAGE_KEY, languageLabel),
        ),
        selectedKey = if (mode == HyphenationMode.LANGUAGE) LANGUAGE_KEY else value,
        onSelect = { key -> if (key == LANGUAGE_KEY) modalOpen = true else onValue(key) },
        query = query,
    )

    if (modalOpen) {
        HyphenationLanguageModal(
            current = value.takeIf { mode == HyphenationMode.LANGUAGE },
            onPick = { onValue(it); modalOpen = false },
            onDismiss = { modalOpen = false },
        )
    }
}

@Composable
private fun HyphenationLanguageModal(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val items = remember {
        HyphenationLanguages.SUPPORTED
            .map { it to hyphenationLanguageName(it) }
            .sortedBy { it.second }
    }
    EinkInfoDialog(
        title = strings.hyphenationLanguageTitle,
        onDismiss = onDismiss,
        closeLabel = strings.close, // adjust to the string found in Step 2
    ) {
        items.forEach { (code, name) ->
            ChoiceRow(label = name, selected = code == current, dense = true, onSelect = { onPick(code) })
        }
    }
}
```

If Step 2 found a different close-label name, use it in place of `strings.close`.

- [ ] **Step 4: Compile** (also enforces i18n DE/EN parity):

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/HyphenationPicker.kt app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt
git commit -m "feat(novel): shared hyphenation picker + language modal"
```

---

## Task 5: Wire the picker into both call sites

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt` (the hyphenation `SegmentedChoiceRow`, ~lines 713-723)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypographyControls.kt` (the hyphenation `PanelSectionHeader` + `ChoiceRow`s, ~lines 122-140)

- [ ] **Step 1: Replace the settings picker.** In `SettingsContent.kt`, replace the whole `SegmentedChoiceRow(...)` hyphenation block (the one with `SegmentOption("auto"/""/"de"/"en")` from the previous feature) with:

```kotlin
        HyphenationPicker(
            value = hyphenationLang,
            onValue = { viewModel.setNovelHyphenationLang(it) },
            query = query,
        )
```

`hyphenationLang` is already collected (`val hyphenationLang by viewModel.novelHyphenationLang.collectAsState()`). Add the import `com.komgareader.app.ui.reader.HyphenationPicker`. Remove now-unused `SegmentOption`/`SegmentedChoiceRow` imports only if no other section in the file uses them (check first — they likely do; if so, leave the imports).

- [ ] **Step 2: Replace the in-reader panel picker.** In `NovelTypographyControls.kt`, replace the hyphenation block — the `PanelSectionHeader(strings.novelHyphenation)` and the four `ChoiceRow`s (Auto/Off/de/en) — with:

```kotlin
        HyphenationPicker(
            value = hyphenationLang,
            onValue = onHyphenation,
        )
```

`hyphenationLang: String` and `onHyphenation: (String) -> Unit` are already parameters of this composable. Add the import `com.komgareader.app.ui.reader.HyphenationPicker`. Remove the now-unused `PanelSectionHeader`/`ChoiceRow` imports only if nothing else in the file uses them (they are used by other sections — likely leave them).

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypographyControls.kt
git commit -m "feat(novel): use shared hyphenation picker in settings + reader panel"
```

---

## Task 6: Docs sync + full verification

**Files:**
- Modify: `.claude/rules/architecture-seams.md` (the 2026-06-15 hyphenation note)

- [ ] **Step 1: Update the seam doc.** Extend the existing "Dokumentsprache + Auto-Silbentrennung (Ist, 2026-06-15)" bullet (added by the prior feature) with: the supported-language list is now domain-owned (`HyphenationLanguages.SUPPORTED`, 24 codes) with a render parity guard (`PATTERN_DICTS.keys == SUPPORTED`); the full crengine pattern set is bundled as assets (`HYPH_PATTERNS` derived from `PATTERN_DICTS`); the default is now `"auto"`; the UI uses a shared `HyphenationPicker` (Auto/Off chips + Language modal `EinkInfoDialog`) in settings and the reader panel.

- [ ] **Step 2: Full unit suite + native build**

Run: `./gradlew testDebugUnitTest :render-crengine:externalNativeBuildDebug`
Expected: BUILD SUCCESSFUL, all green (incl. the parity test).

- [ ] **Step 3: Commit**

```bash
git add .claude/rules/architecture-seams.md
git commit -m "docs: sync hyphenation language selection (Naht B)"
```

- [ ] **Step 4: Emulator E2E (manual verification).**

Install (`./gradlew :app:installDebug`) and on the `eink_test` emulator:
1. Settings → Comic/Novel hyphenation: the control shows **Automatisch** selected by default.
2. Tap the **Sprache** chip → a modal lists many languages (Arabisch … Ukrainisch), localized + sorted.
3. Pick **Italienisch** → modal closes, the Language chip shows "Italienisch" selected.
4. Open a German EPUB with Automatic → German hyphenation applies (crengine `.so` per the project's x86_64 note — if the emulator lacks the reflow `.so`, verify hyphenation on the arm64 Boox instead; the picker/modal UI is still fully verifiable on the emulator).

Report what was observed; attach a screenshot of the open language modal.

---

## Self-Review Coverage

- Default "auto", Off kept: Task 1 + Task 4 (chips) ✓
- Bundle full pattern set: Task 3 ✓
- Domain SSOT + parity guard (no drift across 3 lists): Tasks 2, 3 ✓
- Shared picker in both call sites: Tasks 4, 5 ✓
- Language modal, localized names, sorted: Task 4 ✓
- i18n parity (de+en+MapBacked): Task 4 ✓
- Tests: default, resolver-new-langs, mode helper, parity: Tasks 1-3 ✓
- docs-match-code: Task 6 ✓
- Avoids parallel session's files: Tasks touch data/domain/render(ReflowCss,Factory)/app(UI,i18n,assets) — none of cr3_bridge.cpp/CrengineDocument.kt/CrengineNative.kt ✓
